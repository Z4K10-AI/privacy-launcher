package com.flurry.android

/**
 * Not Flurry. A class with nothing in it, in a package named like Flurry's.
 *
 * It exists only inside the test app, so that the tracker scanner has
 * something real to find: this file is compiled by the real Android build
 * into a real code file, which `TrackerScanDeviceTest` then scans. The
 * launcher itself contains no such class, and the same test checks that.
 */
class TrackerScanFixture
