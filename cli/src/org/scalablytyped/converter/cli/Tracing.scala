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

object Tracing {

  val logger: Logger[(Array[Logger.Stored], Unit)] =
    storing().zipWith(stdout.filter(LogLevel.warn))

  val DefaultOptions = ConversionOptions(
    useScalaJsDomTypes = true,
    outputPackage = Name.typings,
    enableScalaJsDefined = Selection.All,
    flavour = Flavour.Normal,
    ignored = SortedSet("typescript"),
    stdLibs = SortedSet("es6"),
    expandTypeMappings = EnabledTypeMappingExpansion.DefaultSelection,
    versions = Versions(Versions.Scala3, Versions.ScalaJs1),
    organization = "org.scalablytyped",
    enableReactTreeShaking = Selection.None,
    enableLongApplyMethod = false,
    privateWithin = None,
    useDeprecatedModuleNames = false
  )

  /** Generate only Scala source files without sbt project structure
    */
  def generateSourcesOnly(): Either[Map[LibTsSource, Either[Throwable, String]], Set[os.Path]] = {
    val inDirectory = os.pwd
    val sourceOutputDir = os.pwd / "generated-sources"
    lazy val paths = new Main.Paths(inDirectory)
    val packageJsonPath = paths.packageJson.getOrElse(sys.error(s"${inDirectory} does not contain package.json"))
    val nodeModulesPath = paths.node_modules.getOrElse(sys.error(s"${inDirectory} does not contain node_modules"))

    require(files.exists(nodeModulesPath / "typescript" / "lib"), "must install typescript")

    println(s"Package JSON path: $packageJsonPath")
    println(s"Node modules path: $nodeModulesPath")

    val packageJson = Json.force[PackageJson](packageJsonPath)

    val wantedLibs: SortedSet[TsIdentLibrary] = {
      val fromPackageJson = packageJson.allLibs(false, peer = true).keySet
      require(fromPackageJson.nonEmpty, "No libraries found in package.json")
      val ret = fromPackageJson -- DefaultOptions.ignoredLibs
      require(ret.nonEmpty, s"All libraries in package.json ignored")
      ret
    }
    println(s"Wanted libs: $wantedLibs")

    val bootstrapped = Bootstrap.fromNodeModules(InFolder(nodeModulesPath), DefaultOptions, wantedLibs)

    val sources: Vector[LibTsSource] = {
      bootstrapped.initialLibs match {
        case Left(unresolved) => sys.error(unresolved.msg)
        case Right(initial)   => initial
      }
    }

    println(s"Converting ${sources.map(_.libName.value).mkString(", ")} to sources only...")

    val cachedParser = PersistingParser(Main.parseCachePath, bootstrapped.inputFolders, logger.void)

    // Step 1: Parse TypeScript files
    println("Step 1: Parsing TypeScript files...")
    val phase1 = new Phase1ReadTypescript(
      resolve = bootstrapped.libraryResolver,
      calculateLibraryVersion = PackageJsonOnly,
      ignored = DefaultOptions.ignoredLibs,
      ignoredModulePrefixes = DefaultOptions.ignoredModulePrefixes,
      pedantic = false,
      parser = cachedParser,
      expandTypeMappings = DefaultOptions.expandTypeMappings
    )

    // Step 2: Convert to Scala.js
    println("Step 2: Converting to Scala.js...")
    val phase2 = new Phase2ToScalaJs(
      pedantic = false,
      scalaVersion = DefaultOptions.versions.scala,
      enableScalaJsDefined = DefaultOptions.enableScalaJsDefined,
      outputPkg = DefaultOptions.outputPackage,
      flavour = DefaultOptions.flavourImpl,
      useDeprecatedModuleNames = DefaultOptions.useDeprecatedModuleNames
    )

    // Step 3: Apply flavour transformations
    println("Step 3: Applying flavour transformations...")
    val phase3 = new PhaseFlavour(DefaultOptions.flavourImpl, maybePrivateWithin = DefaultOptions.privateWithin)

    // Step 4: Create a simple pipeline and run it using PhaseRunner (like SourceOnlyMain)
    println("Step 4: Creating conversion pipeline...")
    val pipeline: RecPhase[LibTsSource, LibScalaJs] = RecPhase[LibTsSource]
      .next(phase1, "typescript")
      .next(phase2, "scala.js")
      .next(phase3, DefaultOptions.flavour.toString)

    val NoListener: PhaseListener[LibTsSource] = (_, _, _) => ()

    println("Step 5: Processing libraries through pipeline...")
    val importedLibs: Map[LibTsSource, PhaseRes[LibTsSource, LibScalaJs]] =
      sources
        .map(s => (s: LibTsSource) -> PhaseRunner(pipeline, (_: LibTsSource) => logger.void, NoListener)(s))
        .toMap

    // Step 6: Process results and generate files
    println("Step 6: Processing results and generating Scala files...")
    PhaseRes.sequenceMap(importedLibs.toSorted) match {
      case PhaseRes.Ok(libs) =>
        println(s"✓ Successfully processed ${libs.size} libraries")

        // Step 7: Create global scope for all libraries
        println("Step 7: Creating global scope...")
        val globalScope = new TreeScope.Root(
          DefaultOptions.outputPackage,
          Name.dummy,
          libs.map { case (_, l) => (l.scalaName, l.packageTree) },
          logger.void,
          false
        )

        // Step 8: Set up minimization (simplified - no minimization for now)
        println("Step 8: Generating source files...")
        val allGeneratedFiles: Iterator[(os.Path, String)] = libs.iterator.flatMap { case (source, lib) =>
          val scalaFiles = Printer(
            globalScope,
            new ParentsResolver,
            lib.packageTree,
            DefaultOptions.outputPackage,
            DefaultOptions.versions.scala
          )

          val targetFolder = sourceOutputDir / source.libName.value
          println(s"  Writing ${source.libName.value} (${scalaFiles.length} files) to $targetFolder...")

          scalaFiles.map { case (relPath, content) =>
            (targetFolder / relPath, content)
          }.iterator
        }

        // Step 9: Write all files to disk
        println("Step 9: Writing files to disk...")
        val writtenFiles = allGeneratedFiles.map { case (path, content) =>
          files.softWrite(path) { writer =>
            writer.write(content)
          }
          path
        }.toSet

        println(s"✓ Successfully generated ${writtenFiles.size} Scala source files to $sourceOutputDir")
        Right(writtenFiles)

      case PhaseRes.Failure(errors) =>
        println("✗ Pipeline failed with errors:")
        errors.foreach {
          case (source, Left(throwable)) =>
            println(s"  ${source.libName.value}: ${throwable.getMessage}")
          case (source, Right(message)) =>
            println(s"  ${source.libName.value}: $message")
        }
        Left(errors)

      case PhaseRes.Ignore() =>
        println("- Pipeline ignored all sources")
        Right(Set.empty)
    }
  }

  def main(args: Array[String]): Unit = System.exit(mainNoExit(args))

  def mainNoExit(args: Array[String]): Int = {
    generateSourcesOnly() match {
      case Right(files) =>
        println(Color.Green(s"✓ Generated source files"))
        0
      case Left(failures) =>
        println(Color.Red("✗ Generation failed:"))
        1
    }
  }
}
