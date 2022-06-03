package com.github.whyrising.kedn.core

import com.github.whyrising.kedn.core.NodeType.BigDecimal
import com.github.whyrising.kedn.core.NodeType.BigInt
import com.github.whyrising.kedn.core.NodeType.Keyword
import com.github.whyrising.y.core.Symbol
import com.github.whyrising.y.core.collections.PersistentHashSet
import com.github.whyrising.y.core.collections.PersistentList
import com.github.whyrising.y.core.collections.PersistentVector
import com.github.whyrising.y.core.hashSet
import com.github.whyrising.y.core.l
import com.github.whyrising.y.core.m
import com.github.whyrising.y.core.v
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
      readEdn("-edn") shouldBe Symbol("-edn")
      readEdn("ed#n") shouldBe Symbol("ed#n")
      shouldThrowExactly<RuntimeException> {
        readEdn("//abc")
      }.message shouldBe "Invalid token: //abc"

      readEdn("edn.test.bar.baz/abc") shouldBe Symbol(
        "edn.test.bar.baz/abc"
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

      shouldThrowExactly<RuntimeException> {
        readEdn("\\o344324")
      }.message shouldBe "Invalid octal escape sequence length: 6"
      shouldThrowExactly<RuntimeException> {
        readEdn("\\o666")
      }.message shouldBe "Octal escape sequence must be in range [0, 255]."
      readEdn("\\o344") shouldBe 'ä'
      shouldThrowExactly<IllegalArgumentException> {
        readEdn("\\o999")
      }.message shouldBe "Invalid digit: 9"

      shouldThrowExactly<IllegalArgumentException> {
        readEdn("\\u999")
      }.message shouldBe "Invalid unicode character: \\u999"

      shouldThrowExactly<RuntimeException> {
        readEdn("\\uD800")
      }.message shouldBe "Invalid character constant: \\ud800"

      readEdn("\\uEFFF") shouldBe ''

      shouldThrowExactly<RuntimeException> {
        readEdn("\\uDFFF")
      }.message shouldBe "Invalid character constant: \\udfff"
    }

    "comments" {
      shouldThrowExactly<RuntimeException> {
        readEdn(";this is a comment.")
      }.message shouldBe "EOF while reading"

      readEdn(";this is a comment.\n23") shouldBe 23L
      readEdn(";this is a comment.\r23") shouldBe 23L
    }

    "lists (a b c d)" - {
      "should return a PersistentList in the same order as in EDN" {
        val list = readEdn("(2  343 nil 432)") as PersistentList<Any?>

        list[0] shouldBe 2L
        list[1] shouldBe 343L
        list[2] shouldBe null
        list[3] shouldBe 432L
      }

      "when there is no delim ')' throw an exception" {
        shouldThrowExactly<RuntimeException> {
          readEdn("(2 343 023 nil 432")
        }.message shouldBe "EOF while reading"
      }

      "multiple lines" {
        readEdn("(2  343\n nil \n432\n)") shouldBe l(2L, 343L, null, 432L)
        readEdn(
          """
          (2
          343
          nil
          432)
        """
        ) shouldBe l(2L, 343L, null, 432L)
      }

      "nested lists" {
        readEdn("(2  343 (2 3 nil 9) 432)") shouldBe l(
          2L,
          343L,
          l(2L, 3L, null, 9L),
          432L
        )

        readEdn("(2 (1 2) (3 (1 1)) 4)") shouldBe l(
          2L,
          l(1L, 2L),
          l(3L, l(1L, 1L)),
          4L
        )
      }

      "list with comments" {
        readEdn(
          """
                    ; list
          (2
          ; comment
          343
                    ; comment
          nil
                    ; comment
          432)
                    ; comment
        """
        ) shouldBe l(2L, 343L, null, 432L)
      }

      "Unmatched delimiter exception" {
        shouldThrowExactly<RuntimeException> {
          readEdn("(2 3 43 3]")
        }.message shouldBe "Unmatched delimiter: ]"
        shouldThrowExactly<RuntimeException> {
          readEdn("(2 3 43 3}")
        }.message shouldBe "Unmatched delimiter: }"
      }
    }

    "vector [a b c]" - {
      "should return a persistent vector in the same given order" {
        val v = readEdn("[1 2 3]") as PersistentVector<*>

        v[0] shouldBe 1L
        v[1] shouldBe 2L
        v[2] shouldBe 3L
      }

      "when there is no delim ')' throw an exception" {
        shouldThrowExactly<RuntimeException> {
          readEdn("[1 2 3")
        }.message shouldBe "EOF while reading"
      }

      "multiple lines" {
        readEdn("[2  343\n nil \n432\n]") as PersistentVector<*> shouldBe
          v(2L, 343L, null, 432L)
        readEdn(
          """
          [2
          343
          nil
          432]
        """
        ) as PersistentVector<*> shouldBe v(2L, 343L, null, 432L)
      }

      "nested vectors" {
        readEdn("[1 [2 3] 4 [5 8]]") as PersistentVector<*> shouldBe
          v(1L, v(2L, 3L), 4L, v(5L, 8L))
      }

      "list with comments" {
        readEdn(
          """
                    ; vector
          [2
          ; comment
          343
                    ; comment
          nil
                    ; comment
          432]
                    ; comment
        """
        ) as PersistentVector<*> shouldBe v(2L, 343L, null, 432L)
      }

      "Unmatched delimiter exception" {
        shouldThrowExactly<RuntimeException> {
          readEdn("[2 3 43 3)")
        }.message shouldBe "Unmatched delimiter: )"
      }
    }

    "maps {a 1, 2 b}" {
      readEdn("{a 1, b 2}") shouldBe m(
        Symbol("a") to 1,
        Symbol("b") to 2,
      )

      shouldThrowExactly<RuntimeException> {
        readEdn("{a 1, b}")
      }.message shouldBe "Map literal must contain an even number of forms"
    }

    "hashsets" {
      readEdn("#{1 2 3 4}") as PersistentHashSet<*> shouldBe
        hashSet(1L, 2L, 3L, 4L)

      shouldThrowExactly<RuntimeException> {
        readEdn("#")
      }.message shouldBe "EOF while reading character"

      shouldThrowExactly<RuntimeException> {
        readEdn("#4")
      }.message shouldBe "No dispatch macro for: 4"
    }

    "discard sequence" - {
      "discard element after discard sequence" {
        readEdn("[1 2 #_ 3 4]") shouldBe v(1L, 2L, 4L)
        readEdn("[1 2 #_     3 4]") shouldBe v(1L, 2L, 4L)
        readEdn("[1 2 #_3 4]") shouldBe v(1L, 2L, 4L)
        readEdn("[1 2 #_foo 4]") shouldBe v(1L, 2L, 4L)
      }
    }

    "Unreadable forms" {
      shouldThrowExactly<RuntimeException> {
        readEdn("#<")
      }.message shouldBe "Unreadable form"
    }

    "symbolic values" {
      readEdn("##Inf") shouldBe Double.POSITIVE_INFINITY
      readEdn("##-Inf") shouldBe Double.NEGATIVE_INFINITY
    }
  }
})
