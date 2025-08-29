package org.scalablytyped.runtime

import scala.scalajs.js

import scala.language.implicitConversions

@js.native
trait TopLevel[T] extends js.Any

object TopLevel {
  implicit class TopLevelOps[T](private val t: TopLevel[T]) {
    @inline def get: T = t.asInstanceOf[T]
  }

  @inline implicit def asT[T](t: TopLevel[T]): T = t.asInstanceOf[T]
}
