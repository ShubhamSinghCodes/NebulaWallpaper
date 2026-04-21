package com.example.nebulawallpaper

object RleHelper {

    fun decodeRle(rleString: String): Array<BooleanArray>? {
        try {
            val lines = rleString.split("\n", "\r").map { it.trim() }
            var width = 0
            var height = 0
            val dataBuilder = StringBuilder()

            for (line in lines) {
                if (line.startsWith("#")) continue
                if (line.startsWith("x")) {
                    val parts = line.split(",")
                    for (part in parts) {
                        val kv = part.split("=")
                        if (kv.size == 2) {
                            val key = kv[0].trim().lowercase()
                            val value = kv[1].trim().toIntOrNull() ?: 0
                            if (key == "x") width = value
                            if (key == "y") height = value
                        }
                    }
                    continue
                }
                dataBuilder.append(line)
            }

            if (width == 0 || height == 0) return null

            val grid = Array(height) { BooleanArray(width) }
            val data = dataBuilder.toString()

            var x = 0; var y = 0; var count = 0

            for (char in data) {
                if (char.isDigit()) {
                    count = count * 10 + (char - '0')
                } else if (char == 'b' || char == 'o') {
                    val runs = if (count == 0) 1 else count
                    val alive = char == 'o'
                    for (i in 0 until runs) {
                        if (x < width && y < height) grid[y][x] = alive
                        x++
                    }
                    count = 0
                } else if (char == '$') {
                    val runs = if (count == 0) 1 else count
                    y += runs
                    x = 0; count = 0
                } else if (char == '!') {
                    break
                }
            }
            return grid
        } catch (e: Exception) { return null }
    }

    fun encodeRle(grid: Array<BooleanArray>): String {
        if (grid.isEmpty() || grid[0].isEmpty()) return ""
        val h = grid.size; val w = grid[0].size
        val sb = StringBuilder().append("x = $w, y = $h, rule = B3/S23\n")
        var currentLineLength = 0

        fun appendStr(str: String) {
            if (currentLineLength + str.length > 70) {
                sb.append("\n"); currentLineLength = 0
            }
            sb.append(str); currentLineLength += str.length
        }

        for (y in 0 until h) {
            var count = 0; var lastState: Boolean? = null
            for (x in 0 until w) {
                val state = grid[y][x]
                if (state == lastState) { count++ }
                else {
                    if (lastState != null) {
                        val tag = if (lastState) "o" else "b"
                        val prefix = if (count > 1) count.toString() else ""
                        appendStr("$prefix$tag")
                    }
                    lastState = state; count = 1
                }
            }
            if (lastState == true && count > 0) {
                val prefix = if (count > 1) count.toString() else ""
                appendStr("${prefix}o")
            }
            if (y < h - 1) {
                var emptyRows = 1
                while (y + emptyRows < h && grid[y + emptyRows].all { !it }) emptyRows++
                val prefix = if (emptyRows > 1) emptyRows.toString() else ""
                appendStr("$prefix$")
            }
        }
        appendStr("!")
        return sb.toString()
    }
}