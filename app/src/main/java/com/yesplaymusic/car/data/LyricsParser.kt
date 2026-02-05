package com.yesplaymusic.car.data

object LyricsParser {
  private val timeRegex = Regex("\\[(\\d+):(\\d+)(?:\\.(\\d{1,3}))?]")

  fun parse(lrc: String?): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    val lines = mutableListOf<LyricLine>()
    lrc.lines().forEach lineLoop@{ rawLine ->
      val times = timeRegex.findAll(rawLine).toList()
      if (times.isEmpty()) return@lineLoop
      val text = rawLine.replace(timeRegex, "").trim()
      times.forEach timeLoop@{ match ->
        val min = match.groupValues[1].toLongOrNull() ?: return@timeLoop
        val sec = match.groupValues[2].toLongOrNull() ?: return@timeLoop
        val fraction = match.groupValues[3]
        val ms = when (fraction.length) {
          1 -> fraction.toLongOrNull()?.times(100) ?: 0L
          2 -> fraction.toLongOrNull()?.times(10) ?: 0L
          3 -> fraction.toLongOrNull() ?: 0L
          else -> 0L
        }
        val timeMs = (min * 60 + sec) * 1000 + ms
        lines.add(LyricLine(timeMs, if (text.isBlank()) "..." else text))
      }
    }
    return lines.sortedBy { it.timeMs }
  }
}
