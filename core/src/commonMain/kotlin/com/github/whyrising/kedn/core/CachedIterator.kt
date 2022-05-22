package com.github.whyrising.kedn.core

internal interface CachedIterator<T> : Iterator<T> {
  /**
   * Returns `true` if the iteration done backwards.
   */
  fun hasPrevious(): Boolean

  /**
   * Returns the previous element in the iteration and moves the cursor to the
   * last position only, it doesn't traverse. In other words, calling [previous]
   * multiple times will not result in going to previous values, only the last
   * cached one.
   */
  fun previous(): T
}
