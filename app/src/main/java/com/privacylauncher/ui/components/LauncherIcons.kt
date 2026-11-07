package com.privacylauncher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The handful of icons this launcher needs, defined in-repo.
 *
 * Why not a library: `androidx.compose.material:material-icons-core` stopped
 * at 1.7.8 and is no longer a dependency of material3 from 1.4.0 onwards. It
 * still resolves, but pinning a frozen artifact for three glyphs is a poor
 * trade in a project meant to be maintained inside a ROM. Icon path data does
 * not rot, so keeping it here costs nothing and removes a dependency.
 *
 * The path data is from the Material Design Icons set, licensed Apache 2.0
 * (see README). Each was rendered and visually checked before being added.
 */
object LauncherIcons {

    val Search: ImageVector by lazy {
        vector(
            "Search",
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 " +
                "9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 " +
                "4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 " +
                "9.5 11.99 14 9.5 14z",
        )
    }

    val Close: ImageVector by lazy {
        vector(
            "Close",
            "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 " +
                "13.41 17.59 19 19 17.59 13.41 12z",
        )
    }

    val Settings: ImageVector by lazy {
        vector(
            "Settings",
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14." +
                "23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-" +
                "1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 " +
                "2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12." +
                "21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18." +
                "14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62." +
                "94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 " +
                "1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-." +
                "61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 " +
                "3.6 3.6-1.62 3.6-3.6 3.6z",
        )
    }

    val Person: ImageVector by lazy {
        vector(
            "Person",
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 " +
                "0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )
    }

    val ChevronDown: ImageVector by lazy {
        vector(
            "ChevronDown",
            "M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z",
        )
    }

    // --- quick controls tiles -------------------------------------------

    val Wifi: ImageVector by lazy {
        vector(
            "Wifi",
            "M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-" +
                "4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z",
        )
    }

    val Bluetooth: ImageVector by lazy {
        vector(
            "Bluetooth",
            "M17.71 7.71 12 2h-1v7.59L6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 11 14.41V22h1l5.71-" +
                "5.71-4.3-4.29 4.3-4.29zM13 5.83l1.88 1.88L13 9.59V5.83zm1.88 10.46L13 18.17v-" +
                "3.76l1.88 1.88z",
        )
    }

    val Location: ImageVector by lazy {
        vector(
            "Location",
            "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 " +
                "0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
        )
    }

    val Flashlight: ImageVector by lazy {
        vector("Flashlight", "M7 2v11h3v9l7-12h-4l4-8z")
    }

    val DoNotDisturb: ImageVector by lazy {
        vector(
            "DoNotDisturb",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z",
        )
    }

    val Rotation: ImageVector by lazy {
        vector(
            "Rotation",
            "M16.48 2.52c3.27 1.55 5.61 4.72 5.97 8.48h1.5C23.44 4.84 18.29 0 12 0l-.66.03 3.81 " +
                "3.81 1.33-1.32zm-6.25-.77c-.59-.59-1.54-.59-2.12 0L1.75 8.11c-.59.59-.59 1.54 0 " +
                "2.12l12.02 12.02c.59.59 1.54.59 2.12 0l6.36-6.36c.59-.59.59-1.54 0-2.12L10.23 " +
                "1.75zm4.6 19.44L2.81 9.17l6.36-6.36 12.02 12.02-6.36 6.36zm-7.31.29C4.25 19.94 " +
                "1.91 16.76 1.55 13H.05C.56 19.16 5.71 24 12 24l.66-.03-3.81-3.81-1.33 1.32z",
        )
    }

    val Dns: ImageVector by lazy {
        vector(
            "Dns",
            "M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zM7 " +
                "19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM20 3H4c-.55 0-1 .45-1 1v6c0 .55.45 " +
                "1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 " +
                "2-.9 2-2 2z",
        )
    }

    val Lock: ImageVector by lazy {
        vector(
            "Lock",
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 " +
                "0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 " +
                "2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
        )
    }

    val Brightness: ImageVector by lazy {
        vector(
            "Brightness",
            "M20 15.31 23.31 12 20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 " +
                "23.31 15.31 20H20v-4.69zM12 18V6c3.31 0 6 2.69 6 6s-2.69 6-6 6z",
        )
    }

    /** Marks a control that hands off to another app. */
    val ArrowOut: ImageVector by lazy {
        vector("ArrowOut", "M9 5v2h6.59L4 18.59 5.41 20 17 8.41V15h2V5z")
    }

    val ArrowBack: ImageVector by lazy {
        vector(
            "ArrowBack",
            "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z",
        )
    }

    /**
     * Builds a 24dp ImageVector from SVG path data.
     *
     * The fill is solid black only as a placeholder: Compose's `Icon` applies
     * its own tint on top, so the colour here is never what you see.
     */
    private fun vector(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
}
