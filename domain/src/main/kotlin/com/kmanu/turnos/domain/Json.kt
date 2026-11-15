package com.kmanu.turnos.domain

/**
 * JSON mínimo para la copia de seguridad y los borradores, sin dependencias externas.
 * Los objetos se leen como [Map], los arreglos como [List] y los números como [Long] o [Double].
 */
object Json {
    fun write(value: Any?): String = buildString { appendValue(value) }

    // Nombre propio: un `append(Any?)` de extensión perdería contra el de StringBuilder.
    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean, is Int, is Long -> append(value.toString())
            is Number -> append(value.toString())
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) append(',')
                    appendString(key.toString())
                    append(':')
                    appendValue(item)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }
            else -> appendString(value.toString())
        }
    }

    private fun StringBuilder.appendString(text: String) {
        append('"')
        text.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    /** Lanza [IllegalArgumentException] si el texto no es JSON válido. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipSpace()
        require(parser.done) { "Sobra texto después del JSON." }
        return value
    }

    private class Parser(private val text: String) {
        private var index = 0
        val done: Boolean get() = index >= text.length

        fun skipSpace() {
            while (!done && text[index].isWhitespace()) index++
        }

        private fun peek(): Char {
            skipSpace()
            require(!done) { "El JSON terminó antes de tiempo." }
            return text[index]
        }

        private fun expect(char: Char) {
            require(peek() == char) { "Se esperaba '$char' en la posición $index." }
            index++
        }

        private fun literal(word: String, result: Any?): Any? {
            require(text.startsWith(word, index)) { "Valor inválido en la posición $index." }
            index += word.length
            return result
        }

        fun value(): Any? = when (peek()) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> number()
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            if (peek() == '}') {
                index++
                return result
            }
            while (true) {
                skipSpace()
                val key = string()
                expect(':')
                result[key] = value()
                if (peek() == ',') index++ else break
            }
            expect('}')
            return result
        }

        private fun array(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            if (peek() == ']') {
                index++
                return result
            }
            while (true) {
                result += value()
                if (peek() == ',') index++ else break
            }
            expect(']')
            return result
        }

        private fun string(): String {
            expect('"')
            val result = StringBuilder()
            while (true) {
                require(!done) { "Texto sin cerrar." }
                val char = text[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(!done) { "Escape incompleto." }
                        when (val escaped = text[index++]) {
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'u' -> {
                                require(index + 4 <= text.length) { "Escape \\u incompleto." }
                                result.append(text.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> result.append(escaped)
                        }
                    }
                    else -> result.append(char)
                }
            }
        }

        private fun number(): Number {
            val start = index
            while (!done && (text[index].isDigit() || text[index] in "-+.eE")) index++
            val raw = text.substring(start, index)
            require(raw.isNotEmpty()) { "Valor inválido en la posición $start." }
            return raw.toLongOrNull() ?: requireNotNull(raw.toDoubleOrNull()) { "Número inválido: $raw" }
        }
    }
}
