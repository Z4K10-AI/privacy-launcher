package com.privacylauncher.data

import java.io.EOFException
import java.io.InputStream

/**
 * Reads the names of the classes an app defines, straight out of its code.
 *
 * An Android app's code is in one or more "dex" files inside the APK. Each
 * starts with tables saying where everything is, and somewhere after them
 * the names themselves. This reads the tables, works out which names are
 * classes the app itself defines, and hands those over one at a time.
 *
 * **Defined, not mentioned.** A dex file also names every class it merely
 * uses. An app that calls into an advertising library it does not contain
 * would otherwise look as though it contained it. Only the classes in the
 * file's own list of definitions are reported, which is also what Exodus
 * Privacy's tool looks at.
 *
 * **Low memory.** The file is never held in memory. It is read once, front
 * to back, as it comes out of the APK's compression, through a small
 * buffer. What is kept is three tables of numbers: about four bytes per
 * string in the file, a few hundred kilobytes for a large one, let go as
 * soon as that file is done. A class name is read into one reused buffer
 * and passed on as bytes; no text is ever built for it here.
 *
 * **It is reading something another app wrote.** The file comes from
 * whichever app is being scanned, so nothing in it is trusted. Every count
 * and position is checked against the size of the file before it is used,
 * a table too large to be real is refused before any memory is set aside
 * for it, and a file that ends early or is laid out in an order this
 * reader does not expect ends in [Malformed], never in a crash or a hang.
 */
internal object DexClasses {

    /** The file is not a dex file this can read. The message says why. */
    class Malformed(message: String) : Exception(message)

    /** Reused between files and between apps, so a scan allocates it once. */
    class Scratch {
        val io = ByteArray(IO_BUFFER)
        val name = ByteArray(LONGEST_NAME)
    }

    /**
     * Calls [onClass] with each class this dex file defines, as the file
     * stores it (`Lcom/example/Thing;`) and the number of bytes that is.
     *
     * [size] is how large the archive says the file is once unpacked, or
     * a negative number when it does not say. Returns how many classes
     * were handed over.
     */
    fun read(
        input: InputStream,
        size: Long,
        scratch: Scratch,
        onClass: (name: ByteArray, length: Int) -> Unit,
    ): Int {
        val cursor = Cursor(input, scratch.io)
        try {
            return read(cursor, size, scratch, onClass)
        } catch (e: EOFException) {
            throw Malformed("the file ends before its own tables say it should")
        }
    }

    private fun read(
        cursor: Cursor,
        size: Long,
        scratch: Scratch,
        onClass: (ByteArray, Int) -> Unit,
    ): Int {
        val header = ByteArray(HEADER_SIZE)
        cursor.readFully(header, HEADER_SIZE)
        if (header[0] != 'd'.code.toByte() || header[1] != 'e'.code.toByte() ||
            header[2] != 'x'.code.toByte() || header[3] != '\n'.code.toByte()
        ) {
            throw Malformed("it does not start the way a dex file does")
        }
        if (u4(header, 0x28) != ENDIAN_TAG) {
            throw Malformed("its numbers are stored in an order this cannot read")
        }

        val declared = u4(header, 0x20)
        // Believe the smaller of what the file says about itself and what
        // the archive says about it. Nothing may point past that.
        val limit = when {
            size >= 0 -> minOf(size, declared)
            else -> declared
        }.coerceAtMost(LARGEST_FILE)
        if (limit < HEADER_SIZE) throw Malformed("it says it is smaller than its own header")

        val stringCount = u4(header, 0x38)
        val stringsAt = u4(header, 0x3C)
        val typeCount = u4(header, 0x40)
        val typesAt = u4(header, 0x44)
        val classCount = u4(header, 0x60)
        val classesAt = u4(header, 0x64)

        if (classCount == 0L || typeCount == 0L || stringCount == 0L) return 0

        if (stringCount > MOST_STRINGS) throw Malformed("it claims $stringCount strings")
        if (typeCount > MOST_TYPES) throw Malformed("it claims $typeCount types")
        if (classCount > typeCount) throw Malformed("it defines more classes than it has types")
        if (stringsAt < HEADER_SIZE || stringsAt + stringCount * 4 > limit ||
            typesAt + typeCount * 4 > limit ||
            classesAt + classCount * CLASS_DEF_SIZE > limit
        ) {
            throw Malformed("a table points outside the file")
        }
        // Read front to back, so the tables have to come in the order
        // every tool that writes dex files puts them in.
        if (typesAt < stringsAt + stringCount * 4 || classesAt < typesAt + typeCount * 4) {
            throw Malformed("its tables are in an order this does not expect")
        }

        cursor.skipTo(stringsAt)
        val stringAt = IntArray(stringCount.toInt())
        cursor.readInts(stringAt)

        cursor.skipTo(typesAt)
        val typeString = IntArray(typeCount.toInt())
        cursor.readInts(typeString)

        // Where in the file the name of each defined class is.
        cursor.skipTo(classesAt)
        val nameAt = LongArray(classCount.toInt())
        val def = ByteArray(CLASS_DEF_SIZE)
        for (i in nameAt.indices) {
            cursor.readFully(def, CLASS_DEF_SIZE)
            val type = u4(def, 0)
            if (type >= typeCount) throw Malformed("a class refers to a type that is not there")
            val string = typeString[type.toInt()].toLong() and 0xFFFFFFFFL
            if (string >= stringCount) throw Malformed("a type refers to a string that is not there")
            val at = stringAt[string.toInt()].toLong() and 0xFFFFFFFFL
            if (at >= limit) throw Malformed("a name is said to be outside the file")
            nameAt[i] = at
        }

        // In the order they sit in the file, so one pass reaches them all.
        nameAt.sort()
        var count = 0
        var previous = -1L
        for (at in nameAt) {
            if (at == previous) continue
            previous = at
            if (at < cursor.position) throw Malformed("the names come before the tables")
            cursor.skipTo(at)
            // The length in characters comes first, in a form that takes
            // one to five bytes. The name ends at a zero byte whatever it
            // says, so it is stepped over rather than believed.
            var more = 5
            while (more-- > 0 && cursor.u1() and 0x80 != 0) { /* keep going */ }
            var length = 0
            while (true) {
                val b = cursor.u1()
                if (b == 0) break
                if (length < scratch.name.size) scratch.name[length++] = b.toByte()
                if (cursor.position > limit) throw Malformed("a name runs off the end of the file")
            }
            onClass(scratch.name, length)
            count++
        }
        return count
    }

    private fun u4(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)

    /** A stream with a position, that can only go forwards. */
    private class Cursor(private val input: InputStream, private val buffer: ByteArray) {
        var position = 0L
            private set
        private var filled = 0
        private var next = 0

        private fun fill() {
            filled = input.read(buffer, 0, buffer.size)
            next = 0
            if (filled <= 0) throw EOFException()
        }

        fun u1(): Int {
            if (next >= filled) fill()
            position++
            return buffer[next++].toInt() and 0xFF
        }

        fun readFully(target: ByteArray, length: Int) {
            var done = 0
            while (done < length) {
                if (next >= filled) fill()
                val take = minOf(length - done, filled - next)
                System.arraycopy(buffer, next, target, done, take)
                next += take
                done += take
            }
            position += length
        }

        fun readInts(target: IntArray) {
            for (i in target.indices) {
                target[i] = u1() or (u1() shl 8) or (u1() shl 16) or (u1() shl 24)
            }
        }

        fun skipTo(offset: Long) {
            if (offset < position) throw Malformed("its tables are in an order this does not expect")
            var left = offset - position
            while (left > 0) {
                if (next >= filled) fill()
                val take = minOf(left, (filled - next).toLong()).toInt()
                next += take
                left -= take
            }
            position = offset
        }
    }

    private const val HEADER_SIZE = 0x70
    private const val CLASS_DEF_SIZE = 32
    private const val ENDIAN_TAG = 0x12345678L
    private const val IO_BUFFER = 64 * 1024

    /** Longer than any class name worth matching. The rest of a longer one is skipped. */
    private const val LONGEST_NAME = 512

    /**
     * Ceilings, each far above anything a real app has, so that a file
     * lying about its size cannot make the scanner set aside memory for it.
     * A dex file can hold at most 65,536 types. The number of strings has
     * no such limit, but the largest real files have a few hundred
     * thousand; a million costs four megabytes for a moment, and more than
     * that is refused.
     */
    private const val MOST_TYPES = 65_536L
    private const val MOST_STRINGS = 1_000_000L
    private const val LARGEST_FILE = 256L * 1024 * 1024
}
