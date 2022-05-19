package com.github.whyrising.kedn.core

/* Built-in
  nil       -> null
  boolean   -> Boolean
  integer   -> Long
  floating  -> Double
  character -> Char
  strings   -> String
 */

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

internal fun readNumber(): Any? {
  TODO("Not yet implemented")
}

private fun isWhitespace(c: Char) = c.isWhitespace() || c == ','

fun isMacro(ch: Int): Boolean = ch < EdnReader.macros.size &&
  EdnReader.macros[ch] != null

internal fun invalidTokenChar(c: Char): Boolean {
  // TODO: 5/19/22 test with # and ' when symbols available
  return isWhitespace(c) || isMacro(c.code)
}

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
    val c = iterator.next()

    if (isWhitespace(c))
      continue
//    if (c.isDigit()) {
//      return readNumber()
//    }
    val t = readToken(c, iterator)

    return interpretToken(t)
  }
}

fun readString(edn: String): Any? = read(edn.asSequence())
