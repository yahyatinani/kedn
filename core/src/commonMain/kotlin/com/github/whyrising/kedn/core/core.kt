package com.github.whyrising.kedn.core

import com.github.whyrising.kedn.core.EdnReader.macroFn
import com.github.whyrising.kedn.core.EdnReader.macros

internal typealias MacroFn = (reader: CachedIterator<Char>, macro: Char) -> Any

/* Built-in
  nil       -> null
  boolean   -> Boolean
  integer   -> Long
  floating  -> Double
  character -> Char
  strings   -> String
 */

private val intRegex = Regex(
  "([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)" +
    "[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?"
)

private val floatRegex =
  Regex("([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?")

private val ratioRegex = Regex("([-+]?[0-9]+)/([0-9]+)")

internal object EdnReader {
  val macros = arrayOfNulls<MacroFn?>(256)
  private val placeholder: MacroFn = { _, _ -> }
  private val stringReaderFn: MacroFn = { reader, _ ->
    buildString {
      while (reader.hasNext()) {
        var ch = reader.next()
        if (ch != '"') {
          if (!reader.hasNext())
            throw RuntimeException("EOF while reading string")

          if (ch == '\\') {
            ch = reader.next()
            ch = when (ch) {
              'n' -> '\n'
              't' -> '\t'
              'r' -> '\r'
              'b' -> '\b'
              'f' -> '\u000c'
              '"' -> break
              '\\' -> break
              'u' -> {
                ch = reader.next()
                if (ch.digitToIntOrNull(16) == null)
                  throw RuntimeException("Invalid unicode escape: \\u$ch")
                readUnicodeChar(reader, ch, 16, 4, true)
              }
              else -> when {
                ch.isDigit() -> {
                  val ret = readUnicodeChar(reader, ch, 8, 3, false)
                  if (ret.code > 255)
                    throw RuntimeException(
                      "Octal escape sequence must be in range [0, 255]."
                    )
                  else ret
                }
                else -> throw RuntimeException(
                  "Unsupported escape character: \\$ch"
                )
              }
            }
          }
          append(ch)
        } else break
      }
    }
  }

  init {
    macros['"'.code] = stringReaderFn
    macros[';'.code] = placeholder
    macros['^'.code] = placeholder
    macros['('.code] = placeholder
    macros[')'.code] = placeholder
    macros['['.code] = placeholder
    macros[']'.code] = placeholder
    macros['{'.code] = placeholder
    macros['}'.code] = placeholder
    macros['\\'.code] = placeholder
//    macros['#'.code] = any
  }

  private fun readUnicodeChar(
    reader: CachedIterator<Char>,
    initch: Char,
    base: Int,
    length: Int,
    exact: Boolean
  ): Char {
    var uc = initch.digitToIntOrNull(base)
      ?: throw IllegalArgumentException("Invalid digit: $initch")
    var i = 1
    while (i < length) {
      val ch = if (reader.hasNext()) reader.next() else null
      if (ch == null || isWhitespace(ch) || isMacro(ch.code)) {
        reader.previous()
        break
      }
      val digit = ch.digitToIntOrNull(base)
        ?: throw IllegalArgumentException("Invalid digit: $ch")
      uc = uc * base + digit
      i++
    }
    if (i != length && exact)
      throw IllegalArgumentException(
        "Invalid character length: $i, should be: $length"
      )
    return uc.toChar()
  }

  fun macroFn(ch: Char): ((CachedIterator<Char>, Char) -> Any)? =
    macros[ch.code]
}

/**
 * String to number if possible, else return null.
 */
internal fun matchNumber(s: String): Any? {
  var matchResult = intRegex.matchEntire(s)
  if (matchResult != null) {
    val groups = matchResult.groupValues
    if (groups[2] != "") {
      if (groups[8] != "")
        return EdnNode("0", NodeType.BigInt)
      return 0L
    }

    val negate = groups[1] == "-"
    var n: String? = null
    var radix = 10
    when {
      groups[3] != "" -> {
        radix = 10
        n = groups[3]
      }
      groups[4] != "" -> {
        radix = 16
        n = groups[4]
      }
      groups[5] != "" -> {
        radix = 8
        n = groups[5]
      }
      groups[7] != "" -> {
        n = groups[7]
        radix = groups[6].toInt()
      }
    }

    if (n == null) return null

    if (negate)
      n = "-$n"

    if (groups[8] != "") // numbers that end with 'N'.
      return EdnNode(n, NodeType.BigInt)

    return try {
      n.toLong(radix)
    } catch (e: NumberFormatException) {
      EdnNode(n, NodeType.BigInt)
    }
  }

  matchResult = floatRegex.matchEntire(s)
  if (matchResult != null) {
    if (matchResult.groups[4] != null)
      return EdnNode(
        value = matchResult.groupValues[1].removePrefix("+"),
        type = NodeType.BigDecimal
      )
    return s.toDouble()
  }

  matchResult = ratioRegex.matchEntire(s)
  if (matchResult != null) {
    val s1 = matchResult.groupValues[1]
    val s2 = matchResult.groupValues[2]
    val numerator = try {
      s1.toInt()
    } catch (e: NumberFormatException) {
      EdnNode(s1, NodeType.BigInt)
    }
    val denominator = try {
      s2.toInt()
    } catch (e: NumberFormatException) {
      EdnNode(s2, NodeType.BigInt)
    }
    return Pair(numerator, denominator)
  }
  return null
}

fun isMacro(ch: Int) = ch < macros.size && macros[ch] != null

private fun isWhitespace(c: Char) = c.isWhitespace() || c == ','

internal fun readNumber(ch0: Char, iterator: SequenceIterator<Char>): Any {
  val s = buildString {
    append(ch0)
    while (iterator.hasNext()) {
      val ch = iterator.next()
      if (isWhitespace(ch) || isMacro(ch.code)) {
        iterator.previous()
        break
      }
      append(ch)
    }
  }
  return matchNumber(s)
    ?: throw NumberFormatException("Invalid number: $s")
}

// TODO: 5/19/22 test with # and ' when symbols available
private fun isTerminatingMacro(ch: Int) = isMacro(ch)

internal fun invalidTokenChar(c: Char): Boolean =
  isWhitespace(c) || isTerminatingMacro(c.code)

internal fun readToken(ch0: Char, iterator: SequenceIterator<Char>) =
  buildString {
    append(ch0)
    while (iterator.hasNext()) {
      val ch = iterator.next()
      if (invalidTokenChar(ch)) {
//        iterator.previous()
        return@buildString
      }
      append(ch)
    }
  }

internal fun interpretToken(s: String): Any? {
  when (s) {
    "nil" -> return null
    "true" -> return true
    "false" -> return false
  }

  // TODO: 5/19/22 symbols
  TODO()
}

fun read(seq: Sequence<Char>): Any? {
  val iterator = SequenceIterator(seq.iterator())
  while (true) {
    val ch = iterator.next()

    if (isWhitespace(ch))
      continue

    if (ch.isDigit())
      return readNumber(ch, iterator)

    val macroFn: MacroFn? = macroFn(ch)
    if (macroFn != null) {
      val ret = macroFn(iterator, ch)
      return ret
    }

    if (ch == '-' || ch == '+') {
      val ch2 = iterator.next()
      if (ch2.isDigit()) {
        iterator.previous()
        return readNumber(ch, iterator)
      }
      // TODO: 5/21/22 else
    }

    val t = readToken(ch, iterator)

    return interpretToken(t)
  }
}

fun readEdn(edn: String): Any? = read(edn.asSequence())
