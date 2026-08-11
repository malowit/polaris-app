package com.malowit.polaris

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.core.content.edit

private const val PREFS_NAME = "polaris_rates_cache"
private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 часа

private val client = OkHttpClient()

suspend fun getRate(context: Context, from: String, to: String, forceRefresh: Boolean = false): Double? {
    if (from == to) return 1.0

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val cacheKey = "rate_${from}_$to"

    if (!forceRefresh) {
        val cachedValue = prefs.getString(cacheKey, null)
        if (cachedValue != null) {
            val parts = cachedValue.split("|")
            val value = parts.getOrNull(0)?.toDoubleOrNull()
            val timestamp = parts.getOrNull(1)?.toLongOrNull()
            if (value != null && timestamp != null &&
                System.currentTimeMillis() - timestamp < CACHE_DURATION_MS
            ) {
                return value
            }
        }
    }

    return withContext(Dispatchers.IO) {
        val rateFromApi1 = tryFetchFromApi1(from, to)
        if (rateFromApi1 != null) {
            saveToCache(prefs, cacheKey, rateFromApi1)
            return@withContext rateFromApi1
        }

        val rateFromApi2 = tryFetchFromApi2(from, to)
        if (rateFromApi2 != null) {
            saveToCache(prefs, cacheKey, rateFromApi2)
            return@withContext rateFromApi2
        }

        null
    }
}

private fun tryFetchFromApi1(from: String, to: String): Double? {
    return try {
        val url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/${from.lowercase()}.json"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val fromObj = json.optJSONObject(from.lowercase()) ?: return null
            if (fromObj.has(to.lowercase())) fromObj.getDouble(to.lowercase()) else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun tryFetchFromApi2(from: String, to: String): Double? {
    return try {
        val url = "https://api.frankfurter.app/latest?from=$from&to=$to"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val rates = json.optJSONObject("rates") ?: return null
            if (rates.has(to)) rates.getDouble(to) else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun saveToCache(prefs: android.content.SharedPreferences, key: String, value: Double) {
    prefs.edit {
        putString(key, "$value|${System.currentTimeMillis()}")
    }
}

data class DisplayRate(val fromCode: String, val toCode: String, val rate: Double)

fun formatRateForDisplay(from: String, to: String, rate: Double): DisplayRate {
    return if (rate < 1) {
        DisplayRate(fromCode = to, toCode = from, rate = 1 / rate)
    } else {
        DisplayRate(fromCode = from, toCode = to, rate = rate)
    }

}

fun getRateTimestamp(context: Context, from: String, to: String): Long? {
    if (from == to) return null

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val cacheKey = "rate_${from}_$to"

    val cachedValue = prefs.getString(cacheKey, null) ?: return null
    val parts = cachedValue.split("|")
    return parts.getOrNull(1)?.toLongOrNull()
}