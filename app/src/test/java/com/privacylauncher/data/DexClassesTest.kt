package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

/**
 * Reading class names out of an app's code.
 *
 * Half of this is "does it read a good file correctly". The other half is
 * the half that matters on a phone: the file comes from whichever app is
 * being scanned, and that app is not on the launcher's side. A file that
 * lies about its own size, stops half way, or points at things that are
 * not there must end in "could not be read". It must never crash the home
 * screen, and never talk it into setting aside a gigabyte.
 */
class DexClassesTest {

    private val classes = listOf("com.example.app.Main", "com.flurry.sdk.Agent", "org.sample.Thing")

    private fun mustBeRefused(bytes: ByteArray, why: String, size: Long = bytes.size.toLong()) {
        try {
            val read = DexFixture.classesIn(bytes, size)
            fail("$why: was read as $read")
        } catch (expected: DexClasses.Malformed) {
            // the only acceptable way to say no
        }
    }

    // --- a good file ---------------------------------------------------------

    @Test
    fun itReportsTheClassesTheFileDefines() {
        assertEquals(classes.sorted(), DexFixture.classesIn(DexFixture.dex(classes)).sorted())
    }

    @Test
    fun aClassThatIsOnlyMentionedIsNotReported() {
        // The app calls into an advertising library that is not inside it.
        // Its code names the library's classes without containing them.
        val dex = DexFixture.dex(
            classes = listOf("com.example.app.Main"),
            mentioned = listOf("com.google.android.gms.ads.AdView", "java.lang.Object"),
        )
        assertEquals(listOf("com.example.app.Main"), DexFixture.classesIn(dex))
    }

    @Test
    fun aFileWithNoClassesIsReadAsEmpty() {
        assertEquals(emptyList<String>(), DexFixture.classesIn(DexFixture.dex(emptyList())))
    }

    @Test
    fun aVeryLongNameIsCutShortWithoutLosingTheNextOne() {
        val long = "a." + "x".repeat(2000) + ".Long"
        val read = DexFixture.classesIn(DexFixture.dex(listOf(long, "z.after.It")))
        assertEquals(2, read.size)
        assertTrue("the class after the long one was lost: $read", "z.after.It" in read)
        assertTrue(read.any { it.startsWith("a.xxxx") && it.length < 600 })
    }

    @Test
    fun itWorksWhenTheArchiveDoesNotSayHowBigTheFileIs() {
        val dex = DexFixture.dex(classes)
        assertEquals(classes.sorted(), DexFixture.classesIn(dex, size = -1).sorted())
    }

    @Test
    fun aThousandClassesAreAllRead() {
        val many = (0 until 1000).map { "com.example.pkg${it % 37}.Class$it" }
        assertEquals(1000, DexFixture.classesIn(DexFixture.dex(many)).toSet().size)
    }

    // --- a file that is not on our side ---------------------------------------

    @Test
    fun somethingThatIsNotADexFileIsRefused() {
        mustBeRefused(ByteArray(400) { 'A'.code.toByte() }, "400 letters")
        mustBeRefused(ByteArray(0), "an empty file")
        mustBeRefused("dex\n035".toByteArray(), "seven bytes")
    }

    @Test
    fun aFileCutOffAnywhereIsRefusedNotCrashedOn() {
        val whole = DexFixture.dex(classes)
        for (length in 0 until whole.size) {
            val cut = whole.copyOf(length)
            try {
                // The archive reports the true, shorter length...
                DexFixture.classesIn(cut)
                // ...and reading may succeed only if nothing it needed was lost.
            } catch (expected: DexClasses.Malformed) {
                // fine
            }
            // ...or still claims the original one.
            try {
                DexFixture.classesIn(cut, size = whole.size.toLong())
                fail("cut to $length of ${whole.size} bytes and still read as whole")
            } catch (expected: DexClasses.Malformed) {
                // fine
            }
        }
    }

    @Test
    fun aFileThatClaimsABillionStringsIsRefusedBeforeAnyMemoryIsSetAside() {
        val dex = DexFixture.dex(classes)
        DexFixture.put(dex, 0x38, 0x7FFFFFFF)
        val before = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        mustBeRefused(dex, "two billion strings")
        val after = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        assertTrue("memory grew by ${(after - before) / 1_000_000} MB", after - before < 64_000_000)
    }

    @Test
    fun tablesThatPointOutsideTheFileAreRefused() {
        for (field in listOf(0x3C, 0x44, 0x64)) {
            val dex = DexFixture.dex(classes)
            DexFixture.put(dex, field, dex.size + 1000)
            mustBeRefused(dex, "the table at header offset $field moved past the end")
        }
        for (field in listOf(0x38, 0x40, 0x60)) {
            val dex = DexFixture.dex(classes)
            DexFixture.put(dex, field, 60_000)
            mustBeRefused(dex, "the count at header offset $field raised to 60,000")
        }
    }

    @Test
    fun aClassThatRefersToATypeThatIsNotThereIsRefused() {
        val dex = DexFixture.dex(classes)
        val classesAt = DexFixture.HEADER + classes.size * 4 + classes.size * 4
        DexFixture.put(dex, classesAt, 9999)
        mustBeRefused(dex, "a class pointing at type 9999")
    }

    @Test
    fun aNameSaidToBeBeforeTheTablesIsRefused() {
        val dex = DexFixture.dex(classes)
        // The first string is said to live inside the header.
        DexFixture.put(dex, DexFixture.HEADER, 8)
        mustBeRefused(dex, "a name inside the header")
    }

    @Test
    fun aFileWithItsNumbersTheOtherWayRoundIsRefused() {
        val dex = DexFixture.dex(classes)
        DexFixture.put(dex, 0x28, 0x78563412)
        mustBeRefused(dex, "the other byte order")
    }

    @Test
    fun randomDamageNeverDoesAnythingWorseThanARefusal() {
        // Five thousand files, each a good one with a few bytes changed at
        // random somewhere in the part that says where everything is. The
        // seed is fixed so a failure can be run again.
        val whole = DexFixture.dex(classes + (0 until 40).map { "com.example.more.C$it" })
        val tables = DexFixture.HEADER + (classes.size + 40) * (4 + 4 + DexFixture.CLASS_DEF)
        val random = Random(20261112)
        repeat(5000) { round ->
            val damaged = whole.copyOf()
            repeat(1 + random.nextInt(4)) {
                damaged[random.nextInt(tables)] = random.nextInt(256).toByte()
            }
            try {
                DexFixture.classesIn(damaged)
            } catch (expected: DexClasses.Malformed) {
                // fine
            } catch (other: Throwable) {
                fail("round $round: ${other.javaClass.simpleName}: ${other.message}")
            }
        }
    }
}
