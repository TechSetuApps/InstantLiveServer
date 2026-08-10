package app.techsetuapps.instantweb

import java.util.concurrent.ConcurrentHashMap

// ================================================================
//  Virtual File System — RAM-based file storage
//  Files are stored as byte arrays with relative paths as keys.
//  This allows HTML files to reference assets with relative paths
//  (e.g., src="css/style.css", src="images/logo.png")
//  Nothing is written to disk — all in RAM, user-invisible.
// ================================================================

object VirtualFS {

    // relative path → (content bytes, timestamp)
    private val files = ConcurrentHashMap<String, Pair<ByteArray, Long>>()

    /** Put a file into the virtual file system. */
    fun put(path: String, bytes: ByteArray) {
        val normalized = path.trimStart('/').replace('\\', '/')
        files[normalized] = Pair(bytes, System.currentTimeMillis())
    }

    /** Get file content by relative path. Returns null if not found. */
    fun get(path: String): ByteArray? {
        val normalized = path.trimStart('/').replace('\\', '/')
        return files[normalized]?.first
    }

    /** Check if a file exists in VFS. */
    fun contains(path: String): Boolean {
        val normalized = path.trimStart('/').replace('\\', '/')
        return files.containsKey(normalized)
    }

    /** Remove a file from VFS. */
    fun remove(path: String) {
        val normalized = path.trimStart('/').replace('\\', '/')
        files.remove(normalized)
    }

    /** Clear all files — call when hosting stops. */
    fun clear() = files.clear()

    /** Get all stored paths. */
    fun paths(): Set<String> = files.keys.toSet()

    /** Combined fingerprint for change detection (polling).
     *  Sorted by path for deterministic output. */
    fun fingerprint(): String {
        return files.entries.sortedBy { it.key }.joinToString("_") {
            "${it.key}:${it.value.second}:${it.value.first.size}"
        }
    }

    /** Total bytes stored in VFS. */
    fun totalSize(): Long = files.values.sumOf { it.first.size.toLong() }

    /** Number of files stored. */
    fun size(): Int = files.size
}
