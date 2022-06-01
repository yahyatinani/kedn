package com.github.whyrising.kedn.core

import io.kotest.matchers.shouldBe
import kotlinx.datetime.toInstant
import kotlin.test.Test

class BuiltInTaggedTest {
  @Test
  fun `readEdn should return an instance of Instant`() {
    val instStr = "1985-04-12T23:20:50.52Z"
    readEdn("#inst \"$instStr\"") shouldBe instStr.toInstant()
  }
}
