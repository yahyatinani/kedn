package com.github.whyrising.kedn.core

import com.github.whyrising.kedn.core.EdnReader.macroFn
import com.github.whyrising.kedn.core.EdnReader.macros
import com.github.whyrising.y.core.Symbol
import com.github.whyrising.y.core.get
import com.github.whyrising.y.core.hashSet
import com.github.whyrising.y.core.toPlist
import com.github.whyrising.y.core.util.m
import com.github.whyrising.y.core.vec
import kotlinx.datetime.toInstant

internal typealias MacroFn = (reader: SequenceIterator<Char>, Char) -> Any

private val intRegex = Regex(
  "([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)" +
    "[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?"
)

private val floatRegex =
  Regex("([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?")

private val ratioRegex = Regex("([-+]?[0-9]+)/([0-9]+)")

private val symbolRegex = Regex("[:]?([\\D&&[^/]].*/)?(/|[\\D&&[^/]][^/]*)")

private val DEFAULT_DATA_READER = m<Any, Any>(
  Symbol("inst"), { instant: Any -> (instant as String).toInstant() },
  Symbol("uuid"), { instant: Any -> EdnNode(instant as String, NodeType.UUID) }
)

internal object EdnReader {
  private val specials = m<Symbol, Double>(
    Symbol("Inf"), Double.POSITIVE_INFINITY
  )
  val macros = arrayOfNulls<MacroFn?>(256)
  private val dispatchMacros = arrayOfNulls<MacroFn?>(256)
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
  private val characterReaderFn: MacroFn = { reader, _ ->
    if (!reader.hasNext())
      throw RuntimeException("EOF while reading character")

    val ch = reader.next()
    val token = readToken(ch, reader)
    when {
      token.length == 1 -> token[0]
      token == "newline" -> '\n'
      token == "space" -> ' '
      token == "tab" -> '\t'
      token == "backspace" -> '\b'
      token == "formfeed" -> '\u000c'
      token == "return" -> '\r'
      token.startsWith("u") -> {
        val uc = readUnicodeChar(token, 1, 4, 16)
        val c = uc.toChar()
        if (c in '\ud800'..'\uDFFF')
          throw RuntimeException(
            "Invalid character constant: \\u${uc.toString(16)}"
          )
        c
      }
      token.startsWith("o") -> {
        val len = token.length - 1
        if (len > 3)
          throw RuntimeException("Invalid octal escape sequence length: $len")

        val uc = readUnicodeChar(token, offset = 1, length = len, base = 8)
        if (uc > 255)
          throw RuntimeException(
            "Octal escape sequence must be in range [0, 255]."
          )
        uc.toChar()
      }
      else -> throw RuntimeException("Unsupported character: \\$token")
    }
  }
  private val commentReaderFn: MacroFn = { reader, _ ->
    while (reader.hasNext()) {
      val ch = reader.next()
      when {
        ch != '\n' && ch != '\r' -> continue
        else -> break
      }
    }
    reader
  }
  private val listReaderFn: MacroFn = { reader, _ ->
    readDelimitedList(')', reader).toPlist()
  }
  private val vectorReaderFn: MacroFn = { reader, _ ->
    vec(readDelimitedList(']', reader))
  }
  private val unmatchedDelimiterReaderFn: MacroFn = { _, closingDelim ->
    throw RuntimeException("Unmatched delimiter: $closingDelim")
  }
  private val mapReader: MacroFn = { reader, _ ->
    val a = readDelimitedList('}', reader).toTypedArray()
    if ((a.size and 1) == 1)
      throw RuntimeException("Map literal must contain an even number of forms")
    m<Any?, Any?>(*a)
  }
  private val taggedReader: MacroFn = { reader, _ ->
    val tag = read(reader)

    if (tag !is Symbol)
      throw RuntimeException("Reader tag must be a symbol")

    val dataReader = DEFAULT_DATA_READER[tag] as ((Any) -> Any)?
      ?: throw RuntimeException("No reader function for tag: $tag")

    val o = read(reader)

    dataReader(o!!)
  }
  private val dispatchReader: MacroFn = { reader, c ->
    if (!reader.hasNext())
      throw RuntimeException("EOF while reading character")

    val ch = reader.next()

    when (val fn = dispatchMacros[ch.code]) {
      null -> {
        if (!ch.isLetter())
          throw RuntimeException("No dispatch macro for: $ch")
        reader.previous()
        taggedReader(reader, ch)
      }
      else -> fn(reader, ch)
    }
  }

  private val setReader: MacroFn = { reader, _ ->
    hashSet(*readDelimitedList('}', reader).toTypedArray())
  }

  private val discardReader: MacroFn = { reader, _ ->
    read(reader)
    reader
  }

  private val unreadableReader: MacroFn = { _, _ ->
    throw RuntimeException("Unreadable form")
  }

  private val symbolicValueReader: MacroFn = { reader, _ ->
    val symbol = read(reader)

    if (symbol == null)
      TODO()

    specials[symbol]!!
  }

  init {
    macros['"'.code] = stringReaderFn
    macros[';'.code] = commentReaderFn
    macros['\\'.code] = characterReaderFn
    macros['('.code] = listReaderFn
    macros[')'.code] = unmatchedDelimiterReaderFn
    macros['['.code] = vectorReaderFn
    macros[']'.code] = unmatchedDelimiterReaderFn
    macros['{'.code] = mapReader
    macros['}'.code] = unmatchedDelimiterReaderFn
    macros['#'.code] = dispatchReader
    macros['^'.code] = placeholder

    dispatchMacros['{'.code] = setReader
    dispatchMacros['_'.code] = discardReader
    dispatchMacros['<'.code] = unreadableReader
    dispatchMacros['#'.code] = symbolicValueReader
  }

  private fun readDelimitedList(
    delim: Char,
    reader: SequenceIterator<Char>
  ): List<Any?> {
    val a = arrayListOf<Any?>()
    while (true) {
      if (!reader.hasNext())
        throw RuntimeException("EOF while reading")

      var ch = reader.next()
      while (isWhitespace(ch))
        ch = reader.next()

      if (ch == delim)
        break

      // TODO: 5/28/22 review
//      val macroFn: MacroFn? = macroFn(ch)
//      if (macroFn != null) {
//        val ret = macroFn(reader, ch)
//        if (ret != reader)
//          a.add(ret)
//      } else {
//        reader.previous()
//        val o = read(reader)
//        a.add(o)
//      }
      reader.previous()
      a.add(read(reader))
    }
    return a
  }

  private fun readUnicodeChar(
    token: String,
    offset: Int,
    length: Int,
    base: Int
  ): Int {
    if (token.length != offset + length)
      throw IllegalArgumentException("Invalid unicode character: \\$token")
    var uc = 0
    var i = offset
    while (i < offset + length) {
      val d = token[i].digitToIntOrNull(base)
        ?: throw IllegalArgumentException("Invalid digit: ${token[i]}")
      uc = uc * base + d
      i++
    }
    return uc
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

  fun macroFn(ch: Char): ((SequenceIterator<Char>, Char) -> Any)? =
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

private fun isTerminatingMacro(ch: Char) = ch != '#' && isMacro(ch.code)

internal fun readToken(ch0: Char, iterator: SequenceIterator<Char>) =
  buildString {
    append(ch0)
    while (iterator.hasNext()) {
      val ch = iterator.next()
      if (isWhitespace(ch) || isTerminatingMacro(ch)) {
        iterator.previous()
        return@buildString
      }
      append(ch)
    }
  }

fun matchSymbol(s: String): Any? {
  val matchResult = symbolRegex.matchEntire(s) ?: return null

  val ns = matchResult.groups[1]?.value
  val name = matchResult.groups[2]?.value

  if ((ns != null && ns.endsWith(":/")) ||
    (name != null && name.endsWith(":")) ||
    s.indexOf("::") != -1
  ) return null

  val isKeyword = s.startsWith(':')
  val nsname = s.substring(if (isKeyword) 1 else 0)

  return when {
    isKeyword -> EdnNode(nsname, NodeType.Keyword)
    else -> Symbol(nsname)
  }
}

internal fun interpretToken(s: String): Any? {
  when (s) {
    "nil" -> return null
    "true" -> return true
    "false" -> return false
  }

  return matchSymbol(s) ?: throw RuntimeException("Invalid token: $s")
}

internal fun read(iterator: SequenceIterator<Char>): Any? {
  while (true) {
    if (!iterator.hasNext())
      throw RuntimeException("EOF while reading")

    val ch = iterator.next()
    if (isWhitespace(ch))
      continue

    if (ch.isDigit())
      return readNumber(ch, iterator)

    val macroFn: MacroFn? = macroFn(ch)
    if (macroFn != null) {
      val ret = macroFn(iterator, ch)
      if (ret === iterator)
        continue
      return ret
    }

    if (ch == '-' || ch == '+') {
      val ch2 = iterator.next()
      if (ch2.isDigit()) {
        iterator.previous()
        return readNumber(ch, iterator)
      }
      iterator.previous()
    }

    return interpretToken(readToken(ch, iterator))
  }
}

fun read(seq: Sequence<Char>) = read(SequenceIterator(seq.iterator()))

fun readEdn(edn: String): Any? = read(edn.asSequence())
