package com.github.whyrising.kedn.core

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
  val macros = arrayOfNulls<Any?>(256)

  init {
    // TODO: 5/19/22 change any to readers
    val any = Any()
    macros['"'.code] = any
    macros[';'.code] = any
    macros['^'.code] = any
    macros['('.code] = any
    macros[')'.code] = any
    macros['['.code] = any
    macros[']'.code] = any
    macros['{'.code] = any
    macros['}'.code] = any
    macros['\\'.code] = any
//    macros['#'.code] = any
  }
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
      return EdnNode(n, NodeType.BigInt)
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
    // TODO: 5/21/22 implement Ratio numbers
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

internal fun readNumber(ch0: Char, iterator: SequenceIterator<Char>): Any {
  val s = buildString {
    append(ch0)
    while (iterator.hasNext()) {
      val ch = iterator.next()
//    todo:  if whitespace or macro
      append(ch)
    }
  }
  return matchNumber(s)
    ?: throw NumberFormatException("Invalid number: $s")
}

private fun isWhitespace(c: Char) = c.isWhitespace() || c == ','

fun isMacro(ch: Int): Boolean = ch < EdnReader.macros.size &&
  EdnReader.macros[ch] != null

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
