package com.example.data.api

import android.util.Log
import com.example.data.model.RoadObstacleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OverpassService {
    private const val TAG = "OverpassService"
    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Requirement: Map data take only relevant API data.
     * Restrict query strictly to relevant traffic calming and traffic signals nodes.
     */
    suspend fun fetchNearbyObstacles(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 2000
    ): List<RoadObstacleEntity> = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:10];
            (
              node["traffic_calming"](around:$radiusMeters,$latitude,$longitude);
              node["highway"="traffic_signals"](around:$radiusMeters,$latitude,$longitude);
              node["highway"="speed_display"](around:$radiusMeters,$latitude,$longitude);
            );
            out body;
        """.trimIndent()

        val results = mutableListOf<RoadObstacleEntity>()

        try {
            val body = query.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url(OVERPASS_URL)
                .post(body)
                .addHeader("User-Agent", "AutoKiosk-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonString = response.body?.string() ?: ""
                val root = JSONObject(jsonString)
                val elements = root.optJSONArray("elements")

                if (elements != null) {
                    for (i in 0 until elements.length()) {
                        val element = elements.getJSONObject(i)
                        val id = "osm_" + element.optLong("id")
                        val lat = element.optDouble("lat")
                        val lon = element.optDouble("lon")
                        val tags = element.optJSONObject("tags")

                        val trafficCalming = tags?.optString("traffic_calming", "") ?: ""
                        val highway = tags?.optString("highway", "") ?: ""
                        val name = tags?.optString("name", "") ?: ""

                        val isBump = trafficCalming.isNotEmpty() || highway == "speed_display" || trafficCalming in listOf("bump", "hump", "table", "cushion")
                        val isSignal = highway == "traffic_signals"

                        if (isBump) {
                            val bumpTitle = if (name.isNotEmpty()) name else "Speed Bump (${trafficCalming.ifEmpty { "hump" }})"
                            results.add(
                                RoadObstacleEntity(
                                    id = id,
                                    type = "SPEED_BUMP",
                                    latitude = lat,
                                    longitude = lon,
                                    title = bumpTitle,
                                    isAutoDetected = false
                                )
                            )
                        } else if (isSignal) {
                            val signalTitle = if (name.isNotEmpty()) name else "Traffic Light"
                            results.add(
                                RoadObstacleEntity(
                                    id = id,
                                    type = "TRAFFIC_SIGNAL",
                                    latitude = lat,
                                    longitude = lon,
                                    title = signalTitle,
                                    isAutoDetected = false
                                )
                            )
                        }
                    }
                }
                Log.d(TAG, "Fetched ${results.size} strictly relevant road items from Overpass API")
            } else {
                Log.w(TAG, "Overpass API returned response code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch from Overpass API: ${e.message}")
        }

        // Deduplicate: merge obstacles of the same type within 40m
        val deduplicated = mutableListOf<RoadObstacleEntity>()
        for (item in results) {
            val hasNearby = deduplicated.any { existing ->
                existing.type == item.type &&
                        calculateDistanceMeters(item.latitude, item.longitude, existing.latitude, existing.longitude) < 40.0
            }
            if (!hasNearby) {
                deduplicated.add(item)
            }
        }

        deduplicated
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
