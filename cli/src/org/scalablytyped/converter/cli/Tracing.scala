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
    lazy val paths = new Main.Paths(inDirectory)
    val packageJsonPath = paths.packageJson.getOrElse(sys.error(s"${inDirectory} does not contain package.json"))
    val nodeModulesPath = paths.node_modules.getOrElse(sys.error(s"${inDirectory} does not contain node_modules"))
    println(s"Package JSON path: $packageJsonPath")
    println(s"Node modules path: $nodeModulesPath")

    val packageJson = Json.force[PackageJson](packageJsonPath)

    val wantedLibs: SortedSet[TsIdentLibrary] = {
      val fromPackageJson = packageJson.allLibs(false, peer = true).keySet
      require(fromPackageJson.nonEmpty, "No libraries found in package.json")
      val ret = fromPackageJson
      require(ret.nonEmpty, s"All libraries in package.json ignored")
      ret
    }
    println(s"Wanted libs: $wantedLibs")
    val bootstrapped = Bootstrap.fromNodeModules(InFolder(nodeModulesPath), DefaultOptions, wantedLibs)

    val sources: Vector[LibTsSource] = {
      bootstrapped.initialLibs match {
        case Left(unresolved) => sys.error(unresolved.msg)
        case Right(initial)   => None.foldLeft(initial)(_ :+ _)
      }
    }

    println(s"Sources: ${sources.map(_.libName.value).mkString(", ")}")

    return Right(Set.empty)
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
