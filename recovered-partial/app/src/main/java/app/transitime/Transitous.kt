package app.transitime

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * Keyless fallback: Transitous (https://transitous.org), a community-run MOTIS
 * instance over open GTFS / GTFS-RT feeds worldwide.
 */
class TransitousApi : DepartureSource {
    override val label = "Transitous"

    override fun nearestStation(lat: Double, lon: Double): Station? {
        // ~450 m box around the watch; pick the closest stop that serves buses.
        val dLat = 0.004
        val dLon = 0.004 / kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val stops = getArray(
            "api/v1/map/stops?min=${lat - dLat},${lon - dLon}&max=${lat + dLat},${lon + dLon}"
        )
        val nearest = (0 until stops.length()).map { stops.getJSONObject(it) }
            .filter { s -> s.optJSONArray("modes")?.let { m -> (0 until m.length()).any { m.optString(it) == "BUS" } } == true }
            .minByOrNull { haversineM(lat, lon, it.optDouble("lat"), it.optDouble("lon")) }
            ?: return null
        val sLat = nearest.optDouble("lat")
        val sLon = nearest.optDouble("lon")

        // radius pulls in the stop across the street, so both directions come back.
        val stopId = URLEncoder.encode(nearest.optString("stopId"), "UTF-8")
        val times = getObject(
            "api/v5/stoptimes?stopId=$stopId&n=80&mode=BUS&radius=${TransitApi.CLUSTER_RADIUS_M.toInt()}"
        ).optJSONArray("stopTimes") ?: JSONArray()

        val entries = (0 until times.length()).map { times.getJSONObject(it) }
            .filterNot { it.optBoolean("cancelled") || it.optBoolean("tripCancelled") }

        val routes = entries.groupBy { it.optString("routeId") }.map { (routeId, byRoute) ->
            val first = byRoute.first()
            val directions = byRoute.groupBy { it.optString("directionId").ifBlank { it.optString("headsign") } }
                .toSortedMap()
                .map { (_, byDir) ->
                    Direction(
                        headsign = byDir.groupingBy { it.optString("headsign") }.eachCount()
                            .maxByOrNull { it.value }?.key.orEmpty(),
                        stopName = byDir.first().optJSONObject("place")?.optString("name").orEmpty(),
                        departures = byDir.mapNotNull { e ->
                            val place = e.optJSONObject("place") ?: return@mapNotNull null
                            val iso = place.optString("departure").ifBlank { place.optString("arrival") }
                            runCatching { Instant.parse(iso).epochSecond }.getOrNull()
                                ?.let { Departure(it, e.optBoolean("realTime")) }
                        }.sortedBy { it.epochSec },
                    )
                }
            RouteCard(
                id = routeId,
                shortName = first.optString("routeShortName").ifBlank { first.optString("displayName") },
                longName = first.optString("routeLongName"),
                color = parseHex(first.optString("routeColor"), 0xFF3A3A3A.toInt()),
                textColor = parseHex(first.optString("routeTextColor"), Color.WHITE),
                directions = directions,
            )
        }

        return Station(
            name = nearest.optString("name"),
            lat = sLat, lon = sLon,
            distanceM = haversineM(lat, lon, sLat, sLon).toInt(),
            routes = routes,
            source = label,
        )
    }

    private fun get(path: String): String {
        val conn = URL("https://api.transitous.org/$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            // Transitous asks clients to identify themselves.
            conn.setRequestProperty("User-Agent", "TransiTime-WearOS/${BuildConfig.VERSION_NAME}")
            conn.setRequestProperty("Accept", "application/json")
            when (val code = conn.responseCode) {
                200 -> return conn.inputStream.bufferedReader().use { it.readText() }
                429 -> throw RateLimited()
                else -> throw IOException("Transitous HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun getArray(path: String) = JSONArray(get(path))
    private fun getObject(path: String) = JSONObject(get(path))

    private fun parseHex(hex: String, fallback: Int): Int =
        runCatching { Color.parseColor("#" + hex.removePrefix("#")) }.getOrDefault(fallback)
}
