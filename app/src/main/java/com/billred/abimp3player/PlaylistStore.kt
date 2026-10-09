package com.billred.abimp3player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Episode(val uri: String, val title: String)

/** URI grants and preferences survive process death and device restart. */
class PlaylistStore(context: Context, preferencesName: String = "podcast_library") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    fun episodes(): MutableList<Episode> = runCatching {
        val array = JSONArray(prefs.getString("playlist", "[]"))
        MutableList(array.length()) { i -> array.getJSONObject(i).let { Episode(it.getString("uri"), it.getString("title")) } }
    }.getOrDefault(mutableListOf())
    fun save(episodes: List<Episode>, current: String?) {
        val array = JSONArray()
        episodes.forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        prefs.edit().putString("playlist", array.toString()).putString("current", current).commit()
    }
    fun current(): String? = prefs.getString("current", null)
    fun position(uri: String): Int = prefs.getInt("position:$uri", 0)
    fun savePosition(uri: String, position: Int) { prefs.edit().putInt("position:$uri", position.coerceAtLeast(0)).commit() }
}
