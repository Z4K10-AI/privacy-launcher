package com.privacylauncher.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** A tile's state, where "unknown" means Android will not tell an ordinary app. */
enum class Toggle { ON, OFF, UNKNOWN }

enum class PrivateDnsMode { OFF, AUTOMATIC, HOSTNAME, UNKNOWN }

/**
 * Everything the quick panel shows, read in one pass.
 *
 * All of it comes from places an app may read without holding a permission:
 * the Settings provider, LocationManager's enabled flag, and the camera
 * manager's torch callback.
 */
data class QuickControlsState(
    val wifi: Toggle = Toggle.UNKNOWN,
    val bluetooth: Toggle = Toggle.UNKNOWN,
    val location: Toggle = Toggle.UNKNOWN,
    val torch: Toggle = Toggle.OFF,
    val torchAvailable: Boolean = false,
    val dnd: Toggle = Toggle.UNKNOWN,
    val autoRotate: Toggle = Toggle.UNKNOWN,
    val privateDns: PrivateDnsMode = PrivateDnsMode.UNKNOWN,
    val privateDnsHost: String? = null,
    /** 0..1 of [QuickControlsRepository.BRIGHTNESS_MAX]. */
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    /** Whether the user has granted Modify system settings. */
    val canWriteSettings: Boolean = false,
)

/**
 * The quick controls panel's view of the phone, and the few things it can
 * actually change.
 *
 * **What an ordinary app may do here, and what it may not.** This is the
 * whole design constraint of the panel, so it is worth setting out:
 *
 *  - **Reading** state is mostly fine. Wi-Fi, Bluetooth, Do Not Disturb,
 *    auto-rotate and Private DNS all have entries in the Settings provider
 *    that any app may read. Location has a proper API,
 *    `LocationManager.isLocationEnabled`, which also needs no permission.
 *    Reading Bluetooth through `BluetoothAdapter` would need
 *    BLUETOOTH_CONNECT on Android 12 and later, so it is read from Settings
 *    instead and the manifest stays as it is.
 *  - **Changing** state is almost entirely closed. `setWifiEnabled` has been
 *    a no-op for non-system apps since Android 10, `BluetoothAdapter.enable`
 *    since Android 13, location was never togglable, and Do Not Disturb,
 *    auto-rotate and Private DNS each need a permission or special access.
 *    Those tiles open Android's own screen, and the tile says so on its face
 *    rather than looking like a switch that does not work.
 *  - **The torch is the exception.** `CameraManager.setTorchMode` needs no
 *    permission at all, so the flashlight tile is a real toggle.
 */
class QuickControlsRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver
    private val cameraManager: CameraManager? =
        context.getSystemService(CameraManager::class.java)

    /** The back camera with a flash, if there is one. */
    private val torchCameraId: String? by lazy {
        runCatching {
            val manager = cameraManager ?: return@runCatching null
            manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    private var torchOn = false

    // --- reading ----------------------------------------------------------

    fun read(): QuickControlsState = QuickControlsState(
        wifi = globalToggle(WIFI_ON),
        bluetooth = globalToggle(BLUETOOTH_ON),
        location = locationToggle(),
        torch = if (torchOn) Toggle.ON else Toggle.OFF,
        torchAvailable = torchCameraId != null,
        // zen_mode is 0 for off and 1, 2 or 3 for the various flavours of on.
        dnd = globalInt(ZEN_MODE)?.let { if (it != 0) Toggle.ON else Toggle.OFF }
            ?: Toggle.UNKNOWN,
        autoRotate = systemInt(Settings.System.ACCELEROMETER_ROTATION)
            ?.let { if (it == 1) Toggle.ON else Toggle.OFF }
            ?: Toggle.UNKNOWN,
        privateDns = when (globalString(PRIVATE_DNS_MODE)) {
            "off" -> PrivateDnsMode.OFF
            "opportunistic" -> PrivateDnsMode.AUTOMATIC
            "hostname" -> PrivateDnsMode.HOSTNAME
            // Absent means the user has never touched it, and the platform
            // default since Android 9 is opportunistic. Reporting "unknown"
            // there would be accurate about the key and wrong about the
            // phone. Reading the real state instead would mean
            // ConnectivityManager, which needs ACCESS_NETWORK_STATE -- a
            // permission this app removes from the merged manifest on
            // purpose.
            null -> PrivateDnsMode.AUTOMATIC
            else -> PrivateDnsMode.UNKNOWN
        },
        privateDnsHost = globalString(PRIVATE_DNS_SPECIFIER)?.takeIf { it.isNotBlank() },
        brightness = (systemInt(Settings.System.SCREEN_BRIGHTNESS) ?: 0)
            .toFloat()
            .div(BRIGHTNESS_MAX)
            .coerceIn(0f, 1f),
        autoBrightness = systemInt(Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        canWriteSettings = Settings.System.canWrite(context),
    )

    /**
     * Calls [onChange] whenever one of the values above moves.
     *
     * Registered while the panel is on screen and unregistered when it
     * leaves, so a panel nobody is looking at costs nothing. Returns a
     * function that undoes the registration.
     */
    fun observe(onChange: () -> Unit): () -> Unit {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
        }
        val uris = listOf(
            Settings.Global.getUriFor(WIFI_ON),
            Settings.Global.getUriFor(BLUETOOTH_ON),
            Settings.Global.getUriFor(ZEN_MODE),
            Settings.Global.getUriFor(PRIVATE_DNS_MODE),
            Settings.Global.getUriFor(PRIVATE_DNS_SPECIFIER),
            Settings.Secure.getUriFor(Settings.Secure.LOCATION_MODE),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
        )
        uris.forEach { runCatching { resolver.registerContentObserver(it, false, observer) } }

        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchCameraId) {
                    torchOn = enabled
                    onChange()
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == torchCameraId) {
                    // The camera has been claimed by another app, so the
                    // torch is not ours to control until it comes back.
                    torchOn = false
                    onChange()
                }
            }
        }
        // The Handler overload rather than the Executor one: the latter
        // arrived in API 28 and this app supports 26.
        runCatching { cameraManager?.registerTorchCallback(torchCallback, handler) }

        return {
            runCatching { resolver.unregisterContentObserver(observer) }
            runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
        }
    }

    // --- the one thing we can actually change -----------------------------

    /** Returns false if the camera is busy or has no flash. */
    fun setTorch(on: Boolean): Boolean {
        val id = torchCameraId ?: return false
        return runCatching {
            cameraManager?.setTorchMode(id, on)
            true
        }.getOrDefault(false)
    }

    // --- brightness -------------------------------------------------------

    /**
     * Writes system brightness, if the user has granted Modify system
     * settings. Returns false when they have not.
     *
     * [BRIGHTNESS_MAX] is assumed to be 255, which is the range Android has
     * used for `SCREEN_BRIGHTNESS` since the beginning and what Samsung
     * devices use. A handful of manufacturers widen it; there is no public
     * API that reports the real maximum, so the slider is clamped and the
     * worst case on such a device is that it only reaches part way up.
     */
    fun setBrightness(fraction: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val value = (fraction.coerceIn(0f, 1f) * BRIGHTNESS_MAX).toInt().coerceIn(1, BRIGHTNESS_MAX)
        return runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            true
        }.getOrDefault(false)
    }

    /** Opens Android's own "Modify system settings" screen for this app. */
    fun requestWriteSettings(): Boolean = start(
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )

    // --- everything else opens Android's own screen -----------------------

    fun openWifi(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // A Settings *panel*: a slice that slides up over this app with
            // a working Wi-Fi switch on it, rather than a whole screen.
            start(Intent(Settings.Panel.ACTION_WIFI)) ||
                start(Intent(Settings.ACTION_WIFI_SETTINGS))
        } else {
            start(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

    fun openBluetooth(): Boolean = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openLocation(): Boolean = start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun openDnd(): Boolean =
        start(Intent(ACTION_ZEN_MODE_SETTINGS)) || start(Intent(Settings.ACTION_SOUND_SETTINGS))

    fun openRotation(): Boolean = start(Intent(Settings.ACTION_DISPLAY_SETTINGS))

    fun openPrivateDns(): Boolean =
        start(Intent(ACTION_PRIVATE_DNS_SETTINGS)) ||
            start(Intent(Settings.ACTION_WIRELESS_SETTINGS))

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    // --- Settings provider helpers ---------------------------------------

    private fun globalToggle(key: String): Toggle = when (globalInt(key)) {
        null -> Toggle.UNKNOWN
        0 -> Toggle.OFF
        else -> Toggle.ON
    }

    private fun globalInt(key: String): Int? =
        runCatching { Settings.Global.getInt(resolver, key) }.getOrNull()

    private fun globalString(key: String): String? =
        runCatching { Settings.Global.getString(resolver, key) }.getOrNull()

    private fun systemInt(key: String): Int? =
        runCatching { Settings.System.getInt(resolver, key) }.getOrNull()

    private fun locationToggle(): Toggle = runCatching {
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@runCatching Toggle.UNKNOWN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (manager.isLocationEnabled) Toggle.ON else Toggle.OFF
        } else {
            @Suppress("DEPRECATION")
            val mode = Settings.Secure.getInt(resolver, Settings.Secure.LOCATION_MODE)
            if (mode != Settings.Secure.LOCATION_MODE_OFF) Toggle.ON else Toggle.OFF
        }
    }.getOrDefault(Toggle.UNKNOWN)

    companion object {
        const val BRIGHTNESS_MAX = 255

        /**
         * Settings keys written as literals.
         *
         * Each of these exists as a constant on Settings.Global, but several
         * are marked deprecated or hidden depending on the API level, and
         * what matters here is the string in the provider, which has not
         * changed. Every read is wrapped so a missing key gives "unknown"
         * rather than an exception.
         */
        private const val WIFI_ON = "wifi_on"
        private const val BLUETOOTH_ON = "bluetooth_on"
        private const val ZEN_MODE = "zen_mode"
        private const val PRIVATE_DNS_MODE = "private_dns_mode"
        private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

        /**
         * Not public Settings constants, so they are tried first and a public
         * action is the fallback. Unlike the permission controller these are
         * ordinary exported Settings activities, so a start either works or
         * throws ActivityNotFoundException, which `start` catches.
         */
        private const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
        private const val ACTION_PRIVATE_DNS_SETTINGS = "android.settings.PRIVATE_DNS_SETTINGS"
    }
}
