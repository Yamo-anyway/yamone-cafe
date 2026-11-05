package com.yamone.cafevisit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    fun prefs(c: Context) = c.getSharedPreferences("yamone_cafe_v4", Context.MODE_PRIVATE)
    fun config(c: Context): VisitConfig? = runCatching {
        val p = prefs(c)
        VisitConfig(p.getString("cafe", "")!!, p.getInt("min", 1860), p.getInt("max", 2100))
    }.getOrNull()
    fun save(c: Context, v: VisitConfig) { prefs(c).edit().putString("cafe", v.cafeUrl)
        .putInt("min", v.minSeconds).putInt("max", v.maxSeconds).apply() }
    fun log(c: Context, message: String, kind: String = "info") {
        val p = prefs(c)
        val old = runCatching { JSONArray(p.getString("logs", "[]")) }.getOrDefault(JSONArray())
        val all = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("text", message).put("kind", kind))
        for (i in 0 until minOf(old.length(), 99)) all.put(old.getJSONObject(i))
        p.edit().putString("logs", all.toString()).apply()
    }
    fun logs(c: Context): List<JSONObject> {
        val arr = runCatching { JSONArray(prefs(c).getString("logs", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
    fun clock(ms: Long) = if (ms <= 0) "—" else SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(ms))
    fun stamp(ms: Long) = SimpleDateFormat("MM.dd  HH:mm:ss", Locale.KOREA).format(Date(ms))
    fun duration(seconds: Long): String {
        val n = maxOf(0, seconds)
        return if (n >= 3600) String.format(Locale.US, "%02d:%02d:%02d", n / 3600, (n / 60) % 60, n % 60)
        else String.format(Locale.US, "%02d:%02d", n / 60, n % 60)
    }
}
