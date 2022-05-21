package com.github.whyrising.kedn.core

import io.kotest.assertions.throwables.shouldThrowExactly
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

    "integers" {
      readString("0") shouldBe 0
      readString("0000000000000000") shouldBe 0
      readString("222") shouldBe 222L
      readString("9223372036854775807") shouldBe 9223372036854775807L
      readString("0x2342") shouldBe 9026L
      readString("0X2342") shouldBe 9026L
      readString("02342") shouldBe 1250L
      readString("11r49A") shouldBe 593L
      readString("11R49A") shouldBe 593L
      readString("11R49A") shouldBe 593L
      shouldThrowExactly<NotImplementedError> {
        readString("14N")
      }.message shouldBe "An operation is not implemented: Big integers are " +
        "not supported yet."
      shouldThrowExactly<NotImplementedError> {
        readString("0N")
      }.message shouldBe "An operation is not implemented: Big integers are " +
        "not supported yet."

      shouldThrowExactly<NumberFormatException> {
        readString("1234j")
      }.message shouldBe "Invalid number: 1234j"
    }

    "negative numbers" {
      readString("-123") shouldBe -123L
      readString("-0x234") shouldBe -564L
    }

    "floating-point numbers" {
      readString("1.4") shouldBe 1.4
      shouldThrowExactly<NotImplementedError> {
        readString("1.4M")
      }.message shouldBe "An operation is not implemented: big decimals are" +
        " not supported yet."
    }
  }
})
