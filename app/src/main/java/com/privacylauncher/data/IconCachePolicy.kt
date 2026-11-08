package com.privacylauncher.data

import android.content.ComponentCallbacks2

/**
 * How much of the icon cache to keep when Android says memory is short.
 *
 * Android asks every app to give memory back before it starts killing them,
 * through `onTrimMemory`. An app that ignores the request is simply a bigger
 * target, and for a launcher that is a particular kind of bad: being killed
 * costs the user a cold start the next time they press the home button, on
 * the one app that is supposed to appear instantly. On a 4 GB phone with a
 * browser and a chat app open, this is not a rare event.
 *
 * The icons are the only memory worth talking about -- up to 160 bitmaps at
 * 128x128, around 10 MB -- and they are also the cheapest thing in the app
 * to rebuild, since re-reading one takes a few milliseconds on a background
 * thread and the drawer only ever shows a screenful at a time.
 *
 * Kept separate from [IconLoader] so the policy can be read and tested
 * without a device: the decision is a number, and a number is checkable.
 */
internal object IconCachePolicy {

    /**
     * The number of icons to keep at trim [level], out of [maxSize].
     *
     * The levels split in two. [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN]
     * and above mean we are off screen: nobody is looking at these icons, so
     * all of them go. Below that we are the foreground app and the system is
     * merely tight, so keep enough for the screenful in front of the user
     * and drop the scrollback.
     *
     * An unknown level is treated as the mildest real one rather than
     * ignored, because future Android versions add levels and a launcher
     * that silently does nothing with them is back to being a target.
     */
    fun targetSize(level: Int, maxSize: Int): Int = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> 0
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> maxSize / 8
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> maxSize / 4
        else -> maxSize / 2
    }
}
