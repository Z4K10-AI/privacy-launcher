package com.privacylauncher.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.BitSet
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/** How the scan of one app ended. */
enum class ScanStatus {
    /** All of the app's code was read. */
    SCANNED,

    /** Some of its code was read and some could not be. What was found still counts. */
    PARTLY,

    /**
     * The app's file has no code in it to read.
     *
     * Normal for apps that came with the phone: the maker compiles their
     * code ahead of time and stores it elsewhere, and the file an ordinary
     * app may read is left without it.
     */
    NO_CODE,

    /** The app's file could not be opened, or none of its code could be read. */
    UNREADABLE,
}

/** What a scan found in one app, and which version of the app it looked at. */
@Immutable
data class AppScan(
    val packageName: String,
    val status: ScanStatus,
    /** Exodus Privacy's ids for the trackers found, smallest first. */
    val trackerIds: List<Int>,
    val versionCode: Long,
    /** When the app was last installed or updated, as Android reports it. */
    val updatedAt: Long,
) {
    /** Whether this is still a description of the app as it is installed now. */
    fun describes(versionCode: Long, updatedAt: Long): Boolean =
        this.versionCode == versionCode && this.updatedAt == updatedAt
}

/**
 * Looks through the code in an app's files for known trackers.
 *
 * An APK is a zip archive, and its code is in entries called `classes.dex`,
 * `classes2.dex` and so on, with no gaps in the numbering. An app delivered
 * in pieces has several APKs; each is looked through.
 *
 * Nothing here needs Android, so it is tested on the build machine against
 * archives made for the purpose.
 */
internal class ApkScanner(private val list: TrackerList) {

    private val scratch = DexClasses.Scratch()

    fun scan(apks: List<File>): Pair<ScanStatus, List<Int>> {
        val found = BitSet(list.trackers.size)
        var read = 0
        var failed = 0
        for (apk in apks) {
            try {
                ZipFile(apk).use { zip ->
                    for (n in 1..MOST_DEX_FILES) {
                        val entry = zip.getEntry(if (n == 1) "classes.dex" else "classes$n.dex")
                            ?: break
                        try {
                            zip.getInputStream(entry).use { stream ->
                                DexClasses.read(stream, entry.size, scratch) { name, length ->
                                    list.match(name, length, found)
                                }
                            }
                            read++
                        } catch (e: DexClasses.Malformed) {
                            failed++
                        } catch (e: IOException) {
                            failed++
                        }
                    }
                }
            } catch (e: IOException) {
                // Not an archive, or gone, or not ours to read.
                failed++
            } catch (e: SecurityException) {
                failed++
            }
        }
        val status = when {
            read > 0 && failed == 0 -> ScanStatus.SCANNED
            read > 0 -> ScanStatus.PARTLY
            failed > 0 -> ScanStatus.UNREADABLE
            else -> ScanStatus.NO_CODE
        }
        return status to list.trackersIn(found).map { it.id }.sorted()
    }

    private companion object {
        /** Real apps have a handful. This only stops a loop that has no other end. */
        const val MOST_DEX_FILES = 200
    }
}

/**
 * Remembers what earlier scans found, so an app is read once per version.
 *
 * A small text file in the launcher's private storage: one line an app,
 * with the version that was scanned. It never leaves the phone and is not
 * part of a backup; it can always be made again by scanning.
 *
 * The first line names the tracker list the results came from. Results from
 * an older list are thrown away whole, because "nothing found" by a list
 * that did not yet know a tracker says nothing about that tracker.
 */
internal object TrackerCache {

    fun encode(snapshot: String, scans: Collection<AppScan>): String = buildString {
        append(HEADER).append('\n')
        append("list\t").append(snapshot).append('\n')
        scans.sortedBy { it.packageName }.forEach { scan ->
            append(scan.packageName).append('\t')
            append(scan.versionCode).append('\t')
            append(scan.updatedAt).append('\t')
            append(scan.status.name).append('\t')
            append(scan.trackerIds.joinToString(","))
            append('\n')
        }
    }

    /** Anything unreadable is dropped. A damaged cache costs a rescan, nothing more. */
    fun decode(raw: String, snapshot: String): Map<String, AppScan> {
        val lines = raw.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != HEADER) return emptyMap()
        if (!lines.hasNext() || lines.next() != "list\t$snapshot") return emptyMap()
        val out = HashMap<String, AppScan>()
        for (line in lines) {
            val fields = line.split('\t')
            if (fields.size != 5 || fields[0].isBlank()) continue
            val version = fields[1].toLongOrNull() ?: continue
            val updated = fields[2].toLongOrNull() ?: continue
            val status = ScanStatus.entries.firstOrNull { it.name == fields[3] } ?: continue
            val ids = ArrayList<Int>()
            var readable = true
            if (fields[4].isNotEmpty()) {
                for (part in fields[4].split(',')) {
                    val id = part.toIntOrNull()
                    if (id == null) {
                        readable = false
                        break
                    }
                    ids += id
                }
            }
            if (!readable) continue
            out[fields[0]] = AppScan(fields[0], status, ids, version, updated)
        }
        return out
    }

    private const val HEADER = "# privacy-launcher tracker scan, format 1"
}

/** One app's line in the dashboard's tracker card. */
@Immutable
data class TrackerFinding(
    val packageName: String,
    val label: String,
    val status: ScanStatus,
    val trackers: List<Tracker>,
)

/** Everything the dashboard's tracker card shows. */
@Immutable
data class TrackerReport(
    val findings: List<TrackerFinding> = emptyList(),
    val running: Boolean = false,
    /** While running: how many apps are finished, out of how many, and the one being read. */
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    /** Apps on the phone that no scan has looked at in the version installed now. */
    val waiting: Int = 0,
    /** The date of the tracker list, and how many trackers are on it. */
    val snapshot: String = "",
    val listSize: Int = 0,
) {
    /** Apps something was found in, the most trackers first. */
    val withTrackers: List<TrackerFinding> =
        findings.filter { it.trackers.isNotEmpty() }
            .sortedWith(
                compareByDescending<TrackerFinding> { it.trackers.size }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )

    /** Apps whose code was read, in full or in part. Only these can be called clean or not. */
    val read: Int =
        findings.count { it.status == ScanStatus.SCANNED || it.status == ScanStatus.PARTLY }

    /** Apps there was nothing to read in, or that could not be opened. */
    val unread: Int = findings.size - read

    val hasResults: Boolean get() = findings.isNotEmpty()
}

/**
 * Scans the phone's apps for known trackers, one app at a time.
 *
 * **Which apps.** The ones given to it, which are the ones with a launcher
 * icon: this app does not hold QUERY_ALL_PACKAGES and will not ask for it,
 * so those are the apps it can see. See [PrivacyScanner] for the same limit
 * on the permissions list.
 *
 * **What it reads.** Each app's own APK files, which Android lets any app
 * read. It reads the names of the classes inside and nothing else: no data
 * an app has stored, nothing about how it is used.
 *
 * **What a result means.** "Found" means the tracker's code is inside the
 * app. It does not show the code runs, or what it sends. "Not found" means
 * nothing on the list was recognised; code can be renamed to hide it, and
 * the list is a snapshot. The screen says both.
 *
 * **One at a time, and cheap to repeat.** Apps are read in turn on a
 * background thread, each through one small buffer, and every result is
 * remembered with the version it describes. A second scan only reads apps
 * that are new or have been updated.
 */
class TrackerScanner(private val context: Context) {

    @Volatile
    private var loaded: TrackerList? = null

    /** The bundled list, read on first use. */
    fun list(): TrackerList =
        loaded ?: synchronized(this) {
            loaded ?: context.assets.open(ASSET).use(TrackerList::parse).also { loaded = it }
        }

    /** True once the list has been read, so memory pressure can ask without reading it. */
    val isLoaded: Boolean get() = loaded != null

    /** Lets the list go. It is read again from the app's own files when next needed. */
    fun trim() {
        loaded = null
    }

    private val cacheFile: File get() = File(context.noBackupFilesDir, CACHE_FILE)

    private fun readCache(snapshot: String): Map<String, AppScan> =
        try {
            if (cacheFile.isFile) TrackerCache.decode(cacheFile.readText(), snapshot) else emptyMap()
        } catch (e: IOException) {
            emptyMap()
        }

    private fun writeCache(snapshot: String, scans: Collection<AppScan>) {
        try {
            val temp = File(cacheFile.parentFile, "$CACHE_FILE.tmp")
            temp.writeText(TrackerCache.encode(snapshot, scans))
            if (!temp.renameTo(cacheFile)) temp.delete()
        } catch (e: IOException) {
            // The results are still on screen; they are only not remembered.
        }
    }

    private class Installed(val versionCode: Long, val updatedAt: Long, val files: List<File>)

    private fun installed(packageName: String): Installed? {
        val info = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        } catch (e: RuntimeException) {
            // The package manager can fail in transit; treat it as not there.
            return null
        }
        val app = info.applicationInfo ?: return null
        val files = buildList {
            app.sourceDir?.let { add(File(it)) }
            app.splitSourceDirs?.forEach { add(File(it)) }
        }
        return Installed(PackageInfoCompat.getLongVersionCode(info), info.lastUpdateTime, files)
    }

    /**
     * What earlier scans found for [packages], leaving out any app that has
     * been updated since it was scanned.
     */
    suspend fun remembered(packages: List<String>): Map<String, AppScan> =
        withContext(Dispatchers.IO) {
            val cache = readCache(list().snapshot)
            if (cache.isEmpty()) return@withContext emptyMap()
            buildMap {
                for (name in packages) {
                    val scan = cache[name] ?: continue
                    val now = installed(name) ?: continue
                    if (scan.describes(now.versionCode, now.updatedAt)) put(name, scan)
                }
            }
        }

    /**
     * Scans [packages] in order.
     *
     * [onApp] is told which app is about to be looked at, and [onResult]
     * what was found, including for apps answered from memory. With
     * [fresh], memory is ignored and every app is read again.
     *
     * Stopping the coroutine stops the scan between two apps, and whatever
     * was finished by then is remembered.
     */
    suspend fun scan(
        packages: List<String>,
        fresh: Boolean,
        onApp: suspend (index: Int, packageName: String) -> Unit,
        onResult: suspend (AppScan) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val list = list()
        val scanner = ApkScanner(list)
        val results = HashMap<String, AppScan>()
        if (!fresh) results += readCache(list.snapshot)
        var unsaved = 0
        try {
            packages.forEachIndexed { index, name ->
                coroutineContext.ensureActive()
                onApp(index, name)
                val now = installed(name)
                if (now == null) {
                    results.remove(name)
                    return@forEachIndexed
                }
                val before = results[name]
                val scan = if (before != null && before.describes(now.versionCode, now.updatedAt)) {
                    before
                } else {
                    val (status, ids) = scanner.scan(now.files)
                    unsaved++
                    AppScan(name, status, ids, now.versionCode, now.updatedAt)
                }
                results[name] = scan
                onResult(scan)
                if (unsaved >= SAVE_EVERY) {
                    writeCache(list.snapshot, results.values)
                    unsaved = 0
                }
            }
        } finally {
            // Also on the way out of a scan that was stopped: an app that
            // was read should not have to be read again.
            val wanted = packages.toHashSet()
            writeCache(list.snapshot, results.values.filter { it.packageName in wanted })
        }
    }

    private companion object {
        const val ASSET = "trackers.tsv"
        const val CACHE_FILE = "tracker-scan.tsv"
        const val SAVE_EVERY = 8
    }
}
