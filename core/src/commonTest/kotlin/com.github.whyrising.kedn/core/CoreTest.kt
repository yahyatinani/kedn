package com.github.whyrising.kedn.core

import com.github.whyrising.kedn.core.NodeType.BigDecimal
import com.github.whyrising.kedn.core.NodeType.BigInt
import com.github.whyrising.kedn.core.NodeType.Keyword
import com.github.whyrising.kedn.core.NodeType.Symbol
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CoreTest : FreeSpec({
  "readEdn(edn)" - {
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

    "strings" - {
      "basic" {
        readEdn("\"test\"") shouldBe "test"
        readEdn("\"abcdefZZ\"") shouldBe "abcdefZZ"
        readEdn("\"tests are good\"") shouldBe "tests are good"
        readEdn("\"[]{}\"") shouldBe "[]{}"
        shouldThrowExactly<RuntimeException> {
          readEdn("\"sdfhsd")
        }.message shouldBe "EOF while reading string"
      }

      "escape" {
        readEdn("\"test\\ntest\"") shouldBe "test\ntest"
        readEdn("\"test\\ttest\"") shouldBe "test\ttest"
        readEdn("\"test\\rtest\"") shouldBe "test\rtest"
        readEdn("\"test\\btest\"") shouldBe "test\btest"
        readEdn("\"test\\ftest\"") shouldBe "test\u000ctest"
        readEdn("\"sj\"ewr\"") shouldBe "sj"
      }

      "unicodes with exact length" {
        readEdn("\"saef\\u000c\"") shouldBe "saef\u000c"
        readEdn("\"saef\\u000A\"") shouldBe "saef\n"
        readEdn("\"saef\\u0009\"") shouldBe "saef\t"

        shouldThrowExactly<RuntimeException> {
          readEdn("\"saef\\uh")
        }.message shouldBe "Invalid unicode escape: \\uh"

        shouldThrowExactly<IllegalArgumentException> {
          readEdn("\"saef\\u0zzzz\"")
        }.message shouldBe "Invalid digit: z"

        shouldThrowExactly<IllegalArgumentException> {
          readEdn("\"saef\\u0zzz\"")
        }.message shouldBe "Invalid digit: z"

        shouldThrowExactly<IllegalArgumentException> {
          readEdn("\"saef\\u00 0A\"")
        }.message shouldBe "Invalid character length: 2, should be: 4"

        shouldThrowExactly<IllegalArgumentException> {
          readEdn("\"saef\\u00]0A\"")
        }.message shouldBe "Invalid character length: 2, should be: 4"

        shouldThrowExactly<IllegalArgumentException> {
          readEdn("\"saef\\u00")
        }.message shouldBe "Invalid character length: 2, should be: 4"
      }

      "escape chars" {
        readEdn("\"saef\\1000A\"") shouldBe "saef@0A"
        readEdn("\"saef\\100\"") shouldBe "saef@"
        readEdn("\"saef\\10 0\"") shouldBe "saef 0"

        shouldThrowExactly<RuntimeException> {
          readEdn("\"saef\\z000A\"")
        }.message shouldBe "Unsupported escape character: \\z"

        shouldThrowExactly<RuntimeException> {
          readEdn("\"saef\\66666\"")
        }.message shouldBe "Octal escape sequence must be in range [0, 255]."

        shouldThrowExactly<IllegalArgumentException> {
          readEdn("\"saef\\92z\"")
        }.message shouldBe "Invalid digit: 9"
      }
    }

    "symbols" {
      readEdn("-edn") shouldBe EdnNode("-edn", Symbol)
      readEdn("ed#n") shouldBe EdnNode("ed#n", Symbol)
      shouldThrowExactly<RuntimeException> {
        readEdn("//abc")
      }.message shouldBe "Invalid token: //abc"

      readEdn("edn.test.bar.baz/abc") shouldBe EdnNode(
        value = "edn.test.bar.baz/abc",
        type = Symbol
      )

      shouldThrowExactly<RuntimeException> {
        readEdn("::edn.test.bar.baz/abc")
      }.message shouldBe "Invalid token: ::edn.test.bar.baz/abc"

      shouldThrowExactly<RuntimeException> {
        readEdn("edn.test.bar.baz:/abc")
      }.message shouldBe "Invalid token: edn.test.bar.baz:/abc"

      shouldThrowExactly<RuntimeException> {
        readEdn("edn.test.bar.baz/abc:")
      }.message shouldBe "Invalid token: edn.test.bar.baz/abc:"

      shouldThrowExactly<RuntimeException> {
        readEdn("edn.test.b::ar.baz/abc")
      }.message shouldBe "Invalid token: edn.test.b::ar.baz/abc"
    }

    "keywords" {
      readEdn(":edn") shouldBe EdnNode("edn", Keyword)
      readEdn(":edn.test.bar.baz/abc") shouldBe EdnNode(
        value = "edn.test.bar.baz/abc",
        type = Keyword
      )
    }

    "characters" - {
      shouldThrowExactly<RuntimeException> { readEdn("\\") }
        .message shouldBe "EOF while reading character"
      shouldThrowExactly<RuntimeException> { readEdn("\\dsfs") }
        .message shouldBe "Unsupported character: \\dsfs"

      readEdn("\\n") shouldBe 'n'
      readEdn("\\z") shouldBe 'z'
      readEdn("\\\\") shouldBe '\\'
      readEdn("\\newline") shouldBe '\n'
      readEdn("\\space") shouldBe ' '
      readEdn("\\tab") shouldBe '\t'
      readEdn("\\backspace") shouldBe '\b'
      readEdn("\\formfeed") shouldBe '\u000c'
      readEdn("\\return") shouldBe '\r'
    }
  }
})
