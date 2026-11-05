package com.liquid.liquidbaby

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** All app settings. Also used for Backup / Export. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    fun getString(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun putString(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun getInt(k: String, d: Int = 0): Int = sp.getInt(k, d)
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun getLong(k: String, d: Long = 0L): Long = sp.getLong(k, d)
    fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()
    fun getBool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun remove(k: String) = sp.edit().remove(k).apply()

    /** Settings that are re-created by the app itself are not exported. */
    private val transientKeys = setOf("availableJson", "lastCheckMs", "notifiedCode", "lastSeenCode")

    fun exportJson(): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (k in transientKeys || v == null) continue
            when (v) {
                is Boolean, is Int, is Long, is String -> o.put(k, v)
                is Float -> o.put(k, v.toDouble())
                is Set<*> -> o.put(k, JSONArray(v.map { it.toString() }))
            }
        }
        return o.toString(2)
    }

    /** Returns the number of restored settings, or -1 if the text is not valid. */
    fun importJson(text: String): Int {
        return try {
            val o = JSONObject(text.trim())
            val ed = sp.edit()
            var n = 0
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k in transientKeys) continue
                when (val v = o.get(k)) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is String -> ed.putString(k, v)
                    else -> continue
                }
                n++
            }
            ed.apply()
            n
        } catch (e: Exception) {
            -1
        }
    }
}
