package com.github.whyrising.kedn.core

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CoreTest : FreeSpec({
  "readString()" - {
    "nil and boolean" {
      readString("nil") shouldBe null
      readString("true") shouldBe true
      readString("false") shouldBe false
    }

    "terminating macros" {
      readString("false\"") shouldBe false
      readString("false;") shouldBe false
      readString("false^") shouldBe false
      readString("nil(") shouldBe null
      readString("nil)") shouldBe null
      readString("nil[") shouldBe null
      readString("nil]") shouldBe null
      readString("nil{") shouldBe null
      readString("nil}") shouldBe null
      readString("nil\\") shouldBe null
    }

    "whitespaces" {
      readString("       nil ") shouldBe null
      readString("\nnil") shouldBe null
      readString("\tnil") shouldBe null
      readString("\rnil") shouldBe null
      readString("\r\t\n  nil ") shouldBe null
      readString(",,,nil,,") shouldBe null
    }
  }
})
