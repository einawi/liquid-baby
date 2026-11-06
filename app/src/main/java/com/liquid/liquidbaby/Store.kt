package com.liquid.liquidbaby

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Pairing keys. Kept in their own file so they are never part of the settings export. */
object PairStore {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("pairing", Context.MODE_PRIVATE)
    }

    private fun idFor(key: String): String {
        var id = sp.getString(key, null)
        if (id == null) {
            id = Hex.encode(Crypto.random(16))
            sp.edit().putString(key, id).apply()
        }
        return id
    }

    // ---- child side ----

    class Peer(val id: String, val name: String, val key: String)

    fun unitId(): String = idFor("unitId")

    @Synchronized
    fun parents(): List<Peer> {
        val out = ArrayList<Peer>()
        try {
            val a = JSONArray(sp.getString("parents", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Peer(o.getString("id"), o.optString("name"), o.getString("key")))
            }
        } catch (e: Exception) {
            Diag.e("Could not read paired parents", e)
        }
        return out
    }

    @Synchronized
    private fun saveParents(list: List<Peer>) {
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("id", p.id).put("name", p.name).put("key", p.key))
        sp.edit().putString("parents", a.toString()).commit()
    }

    fun addParent(p: Peer) {
        val list = parents().filter { it.id != p.id }.toMutableList()
        list.add(p)
        saveParents(list)
    }

    fun removeParent(id: String) = saveParents(parents().filter { it.id != id })

    fun keyFor(id: String): ByteArray? {
        val p = parents().firstOrNull { it.id == id } ?: return null
        return try {
            Hex.decode(p.key)
        } catch (e: Exception) {
            null
        }
    }

    // ---- parent side ----

    class Paired(val childId: String, val childName: String, val parentId: String, val key: String, val lastIp: String)

    fun parentId(): String = idFor("parentId")

    fun paired(): Paired? {
        val childId = sp.getString("childId", null) ?: return null
        val key = sp.getString("childKey", null) ?: return null
        return Paired(
            childId, sp.getString("childName", "") ?: "", parentId(), key,
            sp.getString("childIp", "") ?: ""
        )
    }

    fun savePaired(childId: String, childName: String, key: String, ip: String) {
        sp.edit().putString("childId", childId).putString("childName", childName)
            .putString("childKey", key).putString("childIp", ip).remove("lastSyncTs").commit()
    }

    /** Time of the newest clip this parent phone has already received (kept even if the clip is deleted). */
    fun lastSyncTs(): Long = sp.getLong("lastSyncTs", 0L)

    @Synchronized
    fun raiseLastSyncTs(v: Long) {
        if (v > sp.getLong("lastSyncTs", 0L)) sp.edit().putLong("lastSyncTs", v).commit()
    }

    fun setLastIp(ip: String) {
        sp.edit().putString("childIp", ip).apply()
    }

    fun clearPaired() {
        sp.edit().remove("childId").remove("childName").remove("childKey").remove("childIp").remove("lastSyncTs").commit()
    }
}
