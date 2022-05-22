package com.github.whyrising.kedn.core

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe

class CachedIteratorTest : FreeSpec({
  "iterating" {
    val l = listOf(1, 2, 3, 4, 5)

    val iterator = SequenceIterator(l.iterator())

    l.forEach { n ->
      iterator.hasNext() shouldBe true
      iterator.next() shouldBeExactly n
    }
  }

  "hasNext()" - {
    "when hasNext() called after previous, it should return true" {
      val l = listOf('-', '0')
      val iterator = SequenceIterator(l.iterator())
      iterator.next()
      iterator.next()
      iterator.previous()

      iterator.hasNext() shouldBe true
    }
  }

  "hasPrevious()" {
    val l = listOf(1, 2, 3, 4, 5)

    val iterator = SequenceIterator(l.iterator())

    iterator.hasPrevious() shouldBe false
  }

  "next()" - {
    "should set previous value to current value of next()" {
      val l = listOf(1, 2, 3, 4, 5)
      val iterator = SequenceIterator(l.listIterator())

      val n1 = iterator.next()

      iterator.previous!! shouldBeExactly n1
    }
  }

  "previous()" - {
    "when calling previous before any forward iteration, it should throw" {
      val l = listOf(1, 2, 3, 4, 5)
      val iterator = SequenceIterator(l.listIterator())

      shouldThrowExactly<NoSuchElementException> {
        iterator.previous()
      }
    }

    "should return the cache value of current next() iteration" {
      val l = listOf(1, 2, 3, 4, 5)
      val iterator = SequenceIterator(l.listIterator())
      val n1 = iterator.next()

      val n2 = iterator.previous()

      n2 shouldBeExactly n1
    }

    """
        when calling previous(), the next next() call should skip one iteration 
        and return the same last value.
      """ {
      val l = listOf(1, 2, 3, 4, 5)
      val iterator = SequenceIterator(l.listIterator())
      val n1 = iterator.next()

      iterator.previous()
      val n = iterator.next()

      n shouldBeExactly n1
    }

    """
        when calling previous() and then next(), any next next() call should 
        continue iterating as expected.
      """ {
      val l = listOf(1, 2, 3, 4, 5)
      val iterator = SequenceIterator(l.listIterator())
      iterator.next()
      iterator.previous()
      iterator.next()

      val n = iterator.next()

      n shouldBeExactly 2
    }
  }
})
