package com.privacylauncher.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * One thing on the home screen: either an app, or a folder of apps.
 */
@Immutable
sealed interface HomeItem {

    /** Stable identity, used as the Compose list key and for reordering. */
    val id: String

    data class App(val key: String) : HomeItem {
        override val id: String get() = "a:$key"
    }

    data class Folder(
        override val id: String,
        val name: String,
        val appKeys: List<String>,
    ) : HomeItem
}

/**
 * Reads and writes the home layout as JSON.
 *
 * Uses `org.json`, which is part of Android itself. A real serialization
 * library would be tidier, but this is one array of two shapes, and the
 * project's rule is to add a dependency only when hand-writing it would be
 * worse — which it is not here.
 *
 * Unknown or malformed entries are skipped rather than throwing. A corrupt
 * preferences file should cost you your layout, not stop the launcher from
 * starting — and the launcher is the one app that cannot fail to start.
 */
internal object HomeLayoutCodec {

    fun encode(items: List<HomeItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            when (item) {
                is HomeItem.App -> {
                    obj.put(KEY_TYPE, TYPE_APP)
                    obj.put(KEY_APP, item.key)
                }
                is HomeItem.Folder -> {
                    obj.put(KEY_TYPE, TYPE_FOLDER)
                    obj.put(KEY_ID, item.id)
                    obj.put(KEY_NAME, item.name)
                    obj.put(KEY_APPS, JSONArray(item.appKeys))
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decode(raw: String?): List<HomeItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                when (obj.optString(KEY_TYPE)) {
                    TYPE_APP -> {
                        val key = obj.optString(KEY_APP)
                        if (key.isNotBlank()) add(HomeItem.App(key))
                    }
                    TYPE_FOLDER -> {
                        val id = obj.optString(KEY_ID)
                        if (id.isBlank()) continue
                        val keys = obj.optJSONArray(KEY_APPS) ?: JSONArray()
                        val appKeys = buildList {
                            for (j in 0 until keys.length()) {
                                keys.optString(j)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { add(it) }
                            }
                        }
                        add(
                            HomeItem.Folder(
                                id = id,
                                name = obj.optString(KEY_NAME).ifBlank { DEFAULT_FOLDER_NAME },
                                appKeys = appKeys,
                            )
                        )
                    }
                }
            }
        }
    }

    const val DEFAULT_FOLDER_NAME = "Folder"

    private const val KEY_TYPE = "t"
    private const val KEY_APP = "k"
    private const val KEY_ID = "i"
    private const val KEY_NAME = "n"
    private const val KEY_APPS = "a"
    private const val TYPE_APP = "app"
    private const val TYPE_FOLDER = "folder"
}
