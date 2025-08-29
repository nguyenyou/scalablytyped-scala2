package org.scalablytyped.runtime

import scala.language.implicitConversions

trait Shortcut {
  type _To
  def _to: _To
}

object Shortcut {
  implicit def follow[T](s: Shortcut { type _To = T }): T = s._to
}
