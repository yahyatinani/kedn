package com.github.whyrising.kedn.core

import com.github.whyrising.kedn.core.NodeType.BigDecimal
import com.github.whyrising.kedn.core.NodeType.BigInt
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CoreTest : FreeSpec({
  "readEdn()" - {
    "nil and boolean" {
      readEdn("nil") shouldBe null
      readEdn("true") shouldBe true
      readEdn("false") shouldBe false
    }

    "terminating macros" {
      readEdn("false\"") shouldBe false
      readEdn("false;") shouldBe false
      readEdn("false^") shouldBe false
      readEdn("nil(") shouldBe null
      readEdn("nil)") shouldBe null
      readEdn("nil[") shouldBe null
      readEdn("nil]") shouldBe null
      readEdn("nil{") shouldBe null
      readEdn("nil}") shouldBe null
      readEdn("nil\\") shouldBe null
    }

    "whitespaces" {
      readEdn("       nil ") shouldBe null
      readEdn("\nnil") shouldBe null
      readEdn("\tnil") shouldBe null
      readEdn("\rnil") shouldBe null
      readEdn("\r\t\n  nil ") shouldBe null
      readEdn(",,,nil,,") shouldBe null
    }

    "integers" {
      readEdn("0") shouldBe 0
      readEdn("0000000000000000") shouldBe 0
      readEdn("222") shouldBe 222L
      readEdn("9223372036854775807") shouldBe 9223372036854775807L
      readEdn("0x2342") shouldBe 9026L
      readEdn("0X2342") shouldBe 9026L
      readEdn("02342") shouldBe 1250L
      readEdn("11r49A") shouldBe 593L
      readEdn("11R49A") shouldBe 593L
      readEdn("11R49A") shouldBe 593L
      readEdn("14N") shouldBe EdnNode("14", BigInt)
      readEdn("0N") shouldBe EdnNode("0", BigInt)
      readEdn("922337203685477580834") shouldBe EdnNode(
        value = "922337203685477580834",
        type = BigInt
      )
      readEdn("+2423") shouldBe 2423
      readEdn("+2423N") shouldBe EdnNode("2423", BigInt)
      shouldThrowExactly<NumberFormatException> {
        readEdn("1234j")
      }.message shouldBe "Invalid number: 1234j"
    }

    "negative numbers" {
      readEdn("-123") shouldBe -123L
      readEdn("-0x234") shouldBe -564L
      readEdn("-0") shouldBe 0L
    }

    "floating-point numbers" {
      readEdn("0.0") shouldBe 0.0
      readEdn("0.4") shouldBe 0.4
      readEdn("+0.4") shouldBe 0.4
      readEdn("2.3e-5") shouldBe 2.3e-5
      readEdn("2.3e+5") shouldBe 2.3e+5
      readEdn("2.3e5") shouldBe 2.3e5
      readEdn("2.3E-5") shouldBe 2.3e-5
      readEdn("2.3E+5") shouldBe 2.3e+5
      readEdn("2.3E5") shouldBe 2.3e5
      readEdn("1.4M") shouldBe EdnNode(value = "1.4", BigDecimal)
      readEdn("+1.4M") shouldBe EdnNode(value = "1.4", BigDecimal)
      readEdn("23.23423M") shouldBe EdnNode(value = "23.23423", BigDecimal)
      readEdn("0.0M") shouldBe EdnNode(value = "0.0", BigDecimal)
    }

    "ratio numbers" {
      readEdn("1/2") shouldBe Pair(1, 2)
      readEdn("3/5") shouldBe Pair(3, 5)
      readEdn("42342349275834759874234598435/5") shouldBe Pair(
        EdnNode(
          value = "42342349275834759874234598435",
          type = BigInt
        ),
        5
      )
      readEdn("3/42342349275834759874234598435") shouldBe Pair(
        3,
        EdnNode(
          value = "42342349275834759874234598435",
          type = BigInt
        )
      )
    }

    "when multiple number in edn, read first number only " {
      readEdn("0 2 234") shouldBe 0
      readEdn("0.4 2 234") shouldBe 0.4
      readEdn("-3 2 234") shouldBe -3
    }

    "when a macro appears after a number, return that number" {
      readEdn("-3]4 74 0") shouldBe -3
      readEdn("6{4 74 0") shouldBe 6
      readEdn("6(4 74 0") shouldBe 6
    }
  }
})
