package org.scalablytyped.converter.cli

import com.olvind.logging.{stdout, storing, LogLevel, Logger}
import fansi.{Attr, Color, Str}
import org.scalablytyped.converter.internal.importer._
import org.scalablytyped.converter.internal.maps._
import org.scalablytyped.converter.internal.phases.{
  PhaseListener,
  PhaseRes,
  PhaseRunner,
  RecPhase
}
import org.scalablytyped.converter.internal.scalajs._
import org.scalablytyped.converter.internal.scalajs.Minimization
import org.scalablytyped.converter.internal.IArray
import org.scalablytyped.converter.internal.ts.CalculateLibraryVersion.PackageJsonOnly
import org.scalablytyped.converter.internal.ts.{PackageJson, TsIdentLibrary}
import org.scalablytyped.converter.internal.{
  constants,
  files,
  sets,
  BuildInfo,
  InFolder,
  Json
}
import org.scalablytyped.converter.{Flavour, Selection}
import scopt.{OParser, OParserBuilder, Read}

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success, Try}

object Tracing {

  /** Generate only Scala source files without sbt project structure
    */
  def generateSourcesOnly()
      : Either[Map[LibTsSource, Either[Throwable, String]], Set[os.Path]] = {
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
