package com.github.whyrising.kedn.core

import com.github.whyrising.kedn.core.NodeType.BigDecimal
import com.github.whyrising.kedn.core.NodeType.BigInt
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
      readString("14N") shouldBe EdnNode("14", BigInt)
      readString("0N") shouldBe EdnNode("0", BigInt)
      readString("922337203685477580834") shouldBe EdnNode(
        value = "922337203685477580834",
        type = BigInt
      )
      readString("+2423") shouldBe 2423
      readString("+2423N") shouldBe EdnNode("2423", BigInt)
      shouldThrowExactly<NumberFormatException> {
        readString("1234j")
      }.message shouldBe "Invalid number: 1234j"
    }

    "negative numbers" {
      readString("-123") shouldBe -123L
      readString("-0x234") shouldBe -564L
      readString("-0") shouldBe 0L
    }

    "floating-point numbers" {
      readString("0.0") shouldBe 0.0
      readString("0.4") shouldBe 0.4
      readString("+0.4") shouldBe 0.4
      readString("2.3e-5") shouldBe 2.3e-5
      readString("2.3e+5") shouldBe 2.3e+5
      readString("2.3e5") shouldBe 2.3e5
      readString("2.3E-5") shouldBe 2.3e-5
      readString("2.3E+5") shouldBe 2.3e+5
      readString("2.3E5") shouldBe 2.3e5
      readString("1.4M") shouldBe EdnNode(value = "1.4", BigDecimal)
      readString("+1.4M") shouldBe EdnNode(value = "1.4", BigDecimal)
      readString("23.23423M") shouldBe EdnNode(value = "23.23423", BigDecimal)
      readString("0.0M") shouldBe EdnNode(value = "0.0", BigDecimal)
    }
  }
})
