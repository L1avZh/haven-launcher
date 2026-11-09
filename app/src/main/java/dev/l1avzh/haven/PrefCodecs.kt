package dev.l1avzh.haven

/**
 * How lists are stored inside SharedPreferences strings. Kept in one place, without Android types, so the formats that
 * earlier versions wrote stay readable (older bindings have 2 or 3 fields, newer ones 4) and are covered by tests.
 */
object PrefCodecs {
    fun parseBindings(raw: String): List<Binding> = raw.split('\n').mapNotNull {
        val p = it.split('|')
        if (p.size < 2 || p[0].toIntOrNull() == null) null else Binding(p[0].toInt(), p[1], p.getOrElse(2) { "" }, p.getOrElse(3) { "0" } == "1")
    }

    fun formatBindings(v: List<Binding>): String = v.joinToString("\n") { "${it.key}|${it.action}|${it.arg}|${if (it.long) 1 else 0}" }

    fun parseLaunchLog(raw: String): Map<String, Long> = raw.split('\n').mapNotNull {
        val p = it.split('|'); if (p.size == 2 && p[1].toLongOrNull() != null) p[0] to p[1].toLong() else null
    }.toMap()

    fun formatLaunchLog(entries: List<Map.Entry<String, Long>>): String = entries.joinToString("\n") { "${it.key}|${it.value}" }
}
