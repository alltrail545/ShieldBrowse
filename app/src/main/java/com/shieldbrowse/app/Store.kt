package com.shieldbrowse.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only storage: settings, bookmarks, history. Nothing leaves the phone. */
object Store {
    private const val PREFS = "shieldbrowse"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- settings ----
    fun searchEngine(ctx: Context): String = p(ctx).getString("engine", "ddg") ?: "ddg"
    fun setSearchEngine(ctx: Context, id: String) { p(ctx).edit().putString("engine", id).apply() }

    fun searchUrl(engine: String, query: String): String {
        val q = android.net.Uri.encode(query)
        return when (engine) {
            "google" -> "https://www.google.com/search?q=$q"
            "bing" -> "https://www.bing.com/search?q=$q"
            else -> "https://duckduckgo.com/?q=$q"
        }
    }
    fun engineName(id: String): String = when (id) {
        "google" -> "Google"
        "bing" -> "Bing"
        else -> "DuckDuckGo"
    }

    fun blockingOn(ctx: Context): Boolean = p(ctx).getBoolean("blocking", true)
    fun setBlockingOn(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("blocking", on).apply() }

    fun jsOn(ctx: Context): Boolean = p(ctx).getBoolean("js", true)
    fun setJsOn(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("js", on).apply() }

    // ---- bookmarks / history (JSON arrays of {t:title, u:url, w:when}) ----
    private fun readList(ctx: Context, key: String): MutableList<JSONObject> {
        val out = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(p(ctx).getString(key, "[]"))
            for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
        } catch (_: Throwable) { }
        return out
    }
    private fun writeList(ctx: Context, key: String, list: List<JSONObject>) {
        val arr = JSONArray()
        for (o in list) arr.put(o)
        p(ctx).edit().putString(key, arr.toString()).apply()
    }

    fun bookmarks(ctx: Context): MutableList<JSONObject> = readList(ctx, "bookmarks")
    fun isBookmarked(ctx: Context, url: String): Boolean =
        bookmarks(ctx).any { it.optString("u") == url }
    fun toggleBookmark(ctx: Context, title: String, url: String): Boolean {
        val list = bookmarks(ctx)
        val idx = list.indexOfFirst { it.optString("u") == url }
        if (idx >= 0) { list.removeAt(idx); writeList(ctx, "bookmarks", list); return false }
        list.add(0, JSONObject().put("t", title).put("u", url).put("w", System.currentTimeMillis()))
        writeList(ctx, "bookmarks", list)
        return true
    }
    fun removeBookmark(ctx: Context, url: String) {
        val list = bookmarks(ctx)
        list.removeAll { it.optString("u") == url }
        writeList(ctx, "bookmarks", list)
    }

    fun history(ctx: Context): MutableList<JSONObject> = readList(ctx, "history")
    fun addHistory(ctx: Context, title: String, url: String) {
        if (!url.startsWith("http")) return
        val list = history(ctx)
        list.removeAll { it.optString("u") == url }
        list.add(0, JSONObject().put("t", title).put("u", url).put("w", System.currentTimeMillis()))
        while (list.size > 500) list.removeAt(list.size - 1)
        writeList(ctx, "history", list)
    }
    fun removeHistory(ctx: Context, url: String) {
        val list = history(ctx)
        list.removeAll { it.optString("u") == url }
        writeList(ctx, "history", list)
    }
    fun clearHistory(ctx: Context) { p(ctx).edit().remove("history").apply() }
}
