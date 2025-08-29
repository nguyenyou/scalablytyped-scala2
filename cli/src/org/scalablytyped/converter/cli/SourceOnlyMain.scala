package org.scalablytyped.converter.cli

import com.olvind.logging.{stdout, storing, LogLevel, Logger}
import fansi.{Attr, Color, Str}
import org.scalablytyped.converter.internal.importer._
import org.scalablytyped.converter.internal.maps._
import org.scalablytyped.converter.internal.phases.{PhaseListener, PhaseRes, PhaseRunner, RecPhase}
import org.scalablytyped.converter.internal.scalajs._
import org.scalablytyped.converter.internal.scalajs.Minimization
import org.scalablytyped.converter.internal.IArray
import org.scalablytyped.converter.internal.ts.CalculateLibraryVersion.PackageJsonOnly
import org.scalablytyped.converter.internal.ts.{PackageJson, TsIdentLibrary}
import org.scalablytyped.converter.internal.{constants, files, sets, BuildInfo, InFolder, Json}
import org.scalablytyped.converter.{Flavour, Selection}
import scopt.{OParser, OParserBuilder, Read}

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success, Try}

object SourceOnlyMain {
  
  // Reuse the same Config from Main but add sourceOnly flag
  case class SourceOnlyConfig(
      conversion:          ConversionOptions,
      wantedLibs:          SortedSet[TsIdentLibrary],
      inDirectory:         os.Path,
      includeDev:          Boolean,
      includeProject:      Boolean,
      sourceOutputDir:     os.Path,
  ) {
    lazy val paths = new Main.Paths(inDirectory)
    def mapConversion(f: ConversionOptions => ConversionOptions) = copy(conversion = f(conversion))
  }

  val DefaultConfig = SourceOnlyConfig(
    Main.DefaultOptions,
    wantedLibs       = SortedSet(),
    inDirectory      = os.pwd,
    includeDev       = false,
    includeProject   = false,
    sourceOutputDir  = os.pwd / "generated-sources",
  )

  val logger: Logger[(Array[Logger.Stored], Unit)] =
    storing().zipWith(stdout.filter(LogLevel.warn))

  // Reuse the same parser setup from Main but update for source-only config
  val ParseSourceOnlyOptions: OParser[Unit, SourceOnlyConfig] = {
    val builder: OParserBuilder[SourceOnlyConfig] = OParser.builder[SourceOnlyConfig]
    import builder._
    import Main._ // Import implicit readers
    
    OParser.sequence(
      programName("stc-sources"),
      head(s"ScalablyTyped Converter - Source Only Mode (version ${BuildInfo.version})"),
      help('h', "help"),
      version('v', "version"),
      opt[os.Path]('d', "directory")
        .action((x, c) => c.copy(inDirectory = x))
        .text("Specify directory where your package.json and node_modules is"),
      opt[os.Path]('o', "output")
        .action((x, c) => c.copy(sourceOutputDir = x))
        .text("Directory to output generated Scala source files"),
      opt[Boolean]("includeDev")
        .action((x, c) => c.copy(includeDev = x))
        .text("Include dev dependencies"),
      opt[Boolean]("includeProject")
        .action((x, c) => c.copy(includeProject = x))
        .text("Include project in current directory"),
      // Minimization is now configured statically below; no CLI flags needed
      opt[Boolean]("useScalaJsDomTypes")
        .action((x, c) => c.mapConversion(_.copy(useScalaJsDomTypes = x)))
        .text("Use scala-js-dom types when possible"),
      opt[Flavour]('f', "flavour")
        .action((x, c) => c.mapConversion(_.copy(flavour = x)))
        .text(s"One of ${Flavour.byName.keys.mkString(", ")}"),
      opt[Versions.ScalaJs]("scalajs")
        .action((x, c) => c.mapConversion(cc => cc.copy(versions = cc.versions.copy(scalaJs = x))))
        .text("Scala.js version"),
      opt[Versions.Scala]("scala")
        .action((x, c) => c.mapConversion(cc => cc.copy(versions = cc.versions.copy(scala = x))))
        .text("Scala version"),
      opt[String]("outputPackage")
        .action((x, c) => c.mapConversion(_.copy(outputPackage = Name(x))))
        .text("Output package"),
      opt[Selection[TsIdentLibrary]]("enableScalaJSDefined")
        .action((x, c) => c.mapConversion(_.copy(enableScalaJsDefined = x)))
        .text("Libraries to enable @ScalaJSDefined traits for"),
      opt[Seq[String]]('s', "stdlib")
        .action((x, c) => c.mapConversion(_.copy(stdLibs = SortedSet(x: _*))))
        .text("Which parts of typescript stdlib to enable"),
      opt[String]("organization")
        .action((x, c) => c.mapConversion(_.copy(organization = x)))
        .text("Organization for generated packages"),
      opt[Seq[String]]("ignoredLibs")
        .action((x, c) => c.mapConversion(_.copy(ignored = SortedSet(x: _*))))
        .text("Libraries to ignore"),
      opt[String]("privateWithin")
        .action((x, c) => c.mapConversion(_.copy(privateWithin = Some(Name(x)))))
        .text("Package for private[package] visibility"),
      opt[Boolean]("enableLongApplyMethod")
        .action((x, c) => c.mapConversion(_.copy(enableLongApplyMethod = x)))
        .text("Enable long apply methods instead of builder pattern"),
      opt[Boolean]("shortModuleNames")
        .action((x, c) => c.mapConversion(_.copy(useDeprecatedModuleNames = x)))
        .text("Enable short module names (deprecated)"),
      arg[Seq[TsIdentLibrary]]("libs")
        .text("Libraries to convert from node_modules")
        .unbounded()
        .optional()
        .action((x, c) => c.copy(wantedLibs = c.wantedLibs ++ x)),
    )
  }

  /**
   * Generate only Scala source files without sbt project structure
   */
  def generateSourcesOnly(config: SourceOnlyConfig): Either[Map[LibTsSource, Either[Throwable, String]], Set[os.Path]] = {
    val packageJsonPath = config.paths.packageJson.getOrElse(sys.error(s"${config.inDirectory} does not contain package.json"))
    val nodeModulesPath = config.paths.node_modules.getOrElse(sys.error(s"${config.inDirectory} does not contain node_modules"))
    
    require(files.exists(nodeModulesPath / "typescript" / "lib"), "must install typescript")

    val packageJson = Json.force[PackageJson](packageJsonPath)

    val projectSource: Option[LibTsSource.FromFolder] =
      if (config.includeProject) Some(LibTsSource.FromFolder(InFolder(config.inDirectory), TsIdentLibrary(config.inDirectory.last))) else None

    val wantedLibs: SortedSet[TsIdentLibrary] =
      config.wantedLibs match {
        case sets.EmptySet() =>
          val fromPackageJson = packageJson.allLibs(config.includeDev, peer = true).keySet
          require(fromPackageJson.nonEmpty, "No libraries found in package.json")
          val ret = fromPackageJson -- config.conversion.ignoredLibs
          require(ret.nonEmpty, s"All libraries in package.json ignored")
          ret
        case otherwise => otherwise
      }

    val bootstrapped = Bootstrap.fromNodeModules(InFolder(nodeModulesPath), config.conversion, wantedLibs)

    val sources: Vector[LibTsSource] = {
      bootstrapped.initialLibs match {
        case Left(unresolved) => sys.error(unresolved.msg)
        case Right(initial)   => projectSource.foldLeft(initial)(_ :+ _)
      }
    }

    println(s"Converting ${sources.map(_.libName.value).mkString(", ")} to sources only...")

    val cachedParser = PersistingParser(Main.parseCachePath, bootstrapped.inputFolders, logger.void)

    // Create a pipeline that stops at LibScalaJs (before compilation)
    val SourceOnlyPipeline: RecPhase[LibTsSource, LibScalaJs] = RecPhase[LibTsSource]
      .next(
        new Phase1ReadTypescript(
          resolve                 = bootstrapped.libraryResolver,
          calculateLibraryVersion = PackageJsonOnly,
          ignored                 = config.conversion.ignoredLibs,
          ignoredModulePrefixes   = config.conversion.ignoredModulePrefixes,
          pedantic                = false,
          parser                  = cachedParser,
          expandTypeMappings      = config.conversion.expandTypeMappings,
        ),
        "typescript",
      )
      .next(
        new Phase2ToScalaJs(
          pedantic                 = false,
          scalaVersion             = config.conversion.versions.scala,
          enableScalaJsDefined     = config.conversion.enableScalaJsDefined,
          outputPkg                = config.conversion.outputPackage,
          flavour                  = config.conversion.flavourImpl,
          useDeprecatedModuleNames = config.conversion.useDeprecatedModuleNames,
        ),
        "scala.js",
      )
      .next(
        new PhaseFlavour(config.conversion.flavourImpl, maybePrivateWithin = config.conversion.privateWithin),
        config.conversion.flavour.toString,
      )

    val NoListener: PhaseListener[LibTsSource] = (_, _, _) => ()

    val importedLibs: Map[LibTsSource, PhaseRes[LibTsSource, LibScalaJs]] =
      sources
        .map(s => (s: LibTsSource) -> PhaseRunner(SourceOnlyPipeline, (_: LibTsSource) => logger.void, NoListener)(s))
        .toMap

    PhaseRes.sequenceMap(importedLibs.toSorted) match {
      case PhaseRes.Ok(LibScalaJs.Unpack(libs)) =>
        // Global scope for all translated libraries
        val globalScope = new TreeScope.Root(
          config.conversion.outputPackage,
          Name.dummy,
          libs.map { case (_, l) => (l.scalaName, l.packageTree) },
          logger.void,
          false,
        )

        // Static minimization policy applied to every run
        val minimizes = List("quill", "clsx", "scroll-into-view-if-needed", "compute-scroll-into-view").map(TsIdentLibrary.apply)
        val staticMinimize: Selection[TsIdentLibrary] = Selection.AllExcept(minimizes: _*)
        val staticMinimizeKeep: IArray[QualifiedName] = IArray.Empty

        // Compute shared keep index for minimization (only if needed)
        lazy val referencesToKeep: Minimization.KeepIndex = {
          val packagesWithShouldMinimize: IArray[(PackageTree, Boolean)] =
            IArray.fromTraversable(libs).map { case (s, l) => (l.packageTree, staticMinimize(s.libName)) }

          Minimization.findReferences(globalScope, staticMinimizeKeep, packagesWithShouldMinimize)
        }

        val allGeneratedFiles: Iterator[(os.Path, String)] = libs.iterator.flatMap {
          case (source, lib) =>
            val willMinimize = staticMinimize(source.libName)
            val treeToPrint =
              if (willMinimize) Minimization(globalScope, referencesToKeep, logger.void, lib.packageTree)
              else lib.packageTree

            val scalaFiles = Printer(
              globalScope,
              new ParentsResolver,
              treeToPrint,
              config.conversion.outputPackage,
              config.conversion.versions.scala,
            )

            val targetFolder = config.sourceOutputDir / source.libName.value
            val minimizedMsg = if (willMinimize) "minimized " else ""
            logger.warn(s"Writing ${minimizedMsg}${source.libName.value} (${scalaFiles.length} files) to $targetFolder...")

            scalaFiles.map { case (relPath, content) =>
              (targetFolder / relPath, content)
            }.iterator
        }

        // Write all files
        val writtenFiles = allGeneratedFiles.map { case (path, content) =>
          files.softWrite(path) { writer =>
            writer.write(content)
          }
          path
        }.toSet

        println(s"Successfully generated ${writtenFiles.size} Scala source files to ${config.sourceOutputDir}")
        Right(writtenFiles)

      case PhaseRes.Failure(errors) => Left(errors)
      case PhaseRes.Ignore()        => Right(Set.empty)
    }
  }

  def main(args: Array[String]): Unit = System.exit(mainNoExit(args))

  def mainNoExit(args: Array[String]): Int = {
    OParser.parse(ParseSourceOnlyOptions, args, DefaultConfig) match {
      case Some(config) =>
        try {
          generateSourcesOnly(config) match {
            case Right(files) =>
              println(Color.Green(s"✓ Generated ${files.size} source files"))
              0
            case Left(failures) =>
              println(Color.Red("✗ Generation failed:"))
              failures.foreach {
                case (source, Left(throwable)) =>
                  println(s"${source.libName.value}: ${throwable.getMessage}")
                case (source, Right(message)) =>
                  println(s"${source.libName.value}: $message")
              }
              1
          }
        } catch {
          case ex: Exception =>
            println(Color.Red(s"✗ Error: ${ex.getMessage}"))
            ex.printStackTrace()
            1
        }

      case None =>
        1
    }
  }
}

