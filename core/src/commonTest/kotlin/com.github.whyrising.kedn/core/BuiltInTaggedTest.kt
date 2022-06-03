package com.github.whyrising.kedn.core

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.toInstant
import kotlin.test.Test

class BuiltInTaggedTest {
  @Test
  fun `readEdn should return an instance of Instant`() {
    val instStr = "1985-04-12T23:20:50.52Z"
    readEdn("#inst \"$instStr\"") shouldBe instStr.toInstant()
  }

  @Test
  fun `readEdn should return Uuid`() {
    val uuid = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    readEdn("#uuid \"$uuid\"") shouldBe EdnNode(uuid, NodeType.UUID)
  }

  @Test
  fun exceptions() {
    shouldThrowExactly<RuntimeException> {
      readEdn("#abc 1234234543")
    }.message shouldBe "No reader function for tag: abc"

    shouldThrowExactly<RuntimeException> {
      readEdn("#nil \"f81d4fae-7dec-11d0-a765-00a0c91e6bf6\"")
    }.message shouldBe "Reader tag must be a symbol"
  }
}
