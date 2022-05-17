package com.github.whyrising.kedn.core

/**
 * Warning: This iterator is not threadsafe.
 */
internal data class SequenceIterator<T>(
  val iterator: Iterator<T>
) : CachedIterator<T> {
  private var skip: Boolean = false
  internal var previous: T? = null

  override fun hasNext(): Boolean = iterator.hasNext()

  override fun next(): T {
    if (skip) {
      skip = false
      return previous!!
    }
    val ret = iterator.next()
    previous = ret
    return ret
  }

  override fun hasPrevious(): Boolean = previous != null

  override fun previous(): T {
    if (!hasPrevious())
      throw NoSuchElementException()

    skip = true
    return previous!!
  }
}
