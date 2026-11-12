package com.privacylauncher.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Makes small dex files and APKs for tests, by hand.
 *
 * A real dex file is written by Android's build tools, which the unit tests
 * do not have. The reader under test uses four parts of the format: the
 * header, the table of strings, the table of types, and the table of class
 * definitions. So that is what this writes, laid out the way the build
 * tools lay them out, and nothing else. The on-device test reads real files
 * made by the real tools, so a mistake shared by this fixture and the
 * reader would show there.
 */
internal object DexFixture {

    /**
     * A dex file that defines [classes] and merely mentions [mentioned].
     * Names are written the ordinary way, with dots.
     */
    fun dex(classes: List<String>, mentioned: List<String> = emptyList()): ByteArray {
        fun descriptor(name: String) = "L" + name.replace('.', '/') + ";"
        val strings = (classes + mentioned).map(::descriptor).distinct().sorted()
        val types = strings // every string here is a type
        val defined = classes.map { strings.indexOf(descriptor(it)) }

        val stringsAt = HEADER
        val typesAt = stringsAt + strings.size * 4
        val classesAt = typesAt + types.size * 4
        val dataAt = classesAt + defined.size * CLASS_DEF

        val data = ByteArrayOutputStream()
        val stringOffsets = strings.map { text ->
            val at = dataAt + data.size()
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            uleb(data, bytes.size)
            data.write(bytes)
            data.write(0)
            at
        }
        val size = dataAt + data.size()

        val out = ByteArray(size)
        "dex\n035\u0000".toByteArray(Charsets.ISO_8859_1).copyInto(out, 0)
        put(out, 0x20, size)
        put(out, 0x24, HEADER)
        put(out, 0x28, 0x12345678)
        put(out, 0x38, strings.size)
        put(out, 0x3C, stringsAt)
        put(out, 0x40, types.size)
        put(out, 0x44, typesAt)
        put(out, 0x60, defined.size)
        put(out, 0x64, classesAt)
        stringOffsets.forEachIndexed { i, at -> put(out, stringsAt + i * 4, at) }
        types.indices.forEach { i -> put(out, typesAt + i * 4, i) }
        defined.forEachIndexed { i, type -> put(out, classesAt + i * CLASS_DEF, type) }
        data.toByteArray().copyInto(out, dataAt)
        return out
    }

    fun put(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value shr 8).toByte()
        bytes[at + 2] = (value shr 16).toByte()
        bytes[at + 3] = (value shr 24).toByte()
    }

    private fun uleb(out: ByteArrayOutputStream, value: Int) {
        var left = value
        while (true) {
            val low = left and 0x7F
            left = left ushr 7
            if (left == 0) {
                out.write(low)
                return
            }
            out.write(low or 0x80)
        }
    }

    /** An APK, which is a zip, holding [entries] by name. */
    fun apk(file: File, entries: Map<String, ByteArray>): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    /** The classes [bytes] defines, as the reader reports them, with dots. */
    fun classesIn(bytes: ByteArray, size: Long = bytes.size.toLong()): List<String> {
        val names = ArrayList<String>()
        DexClasses.read(bytes.inputStream(), size, DexClasses.Scratch()) { name, length ->
            names += String(name, 0, length, Charsets.ISO_8859_1)
                .removePrefix("L").removeSuffix(";").replace('/', '.')
        }
        return names
    }

    const val HEADER = 0x70
    const val CLASS_DEF = 32
}
