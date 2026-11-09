package dev.l1avzh.haven

/**
 * Small size-bounded LRU keyed by strings, with removal by package. Pure Kotlin so it can be unit tested; the artwork
 * loader keeps bitmaps here. Keys are "<package>/<class>@<w>x<h>", so one package's entries share the "<package>/" prefix.
 */
class ArtCache<V>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<String, V>(16, 0.75f, true)
    private var bytes = 0L

    @Synchronized fun get(k: String): V? = map[k]

    @Synchronized fun put(k: String, v: V) {
        map.remove(k)?.let { bytes -= sizeOf(it) }
        map[k] = v; bytes += sizeOf(v)
        val it = map.entries.iterator()
        while (bytes > maxBytes && it.hasNext()) { val e = it.next(); if (e.key == k) continue; bytes -= sizeOf(e.value); it.remove() }
    }

    /** Drops only the entries that belong to [pkg]; returns how many went. */
    @Synchronized fun removePackage(pkg: String): Int {
        var n = 0
        val it = map.entries.iterator()
        while (it.hasNext()) { val e = it.next(); if (e.key.startsWith("$pkg/")) { bytes -= sizeOf(e.value); it.remove(); n++ } }
        return n
    }

    @Synchronized fun clear() { map.clear(); bytes = 0 }
    @Synchronized fun size() = map.size
    @Synchronized fun totalBytes() = bytes
}
