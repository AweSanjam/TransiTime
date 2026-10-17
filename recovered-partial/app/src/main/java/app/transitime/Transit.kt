package app.transitime

import android.graphics.Color
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Departure(val epochSec: Long, val realtime: Boolean)

data class Direction(
    val headsign: String,
    val stopName: String,
    val departures: List<Departure>,
)

data class RouteCard(
    val id: String,
    val shortName: String,
    val longName: String,
    val color: Int,
    val textColor: Int,
    val directions: List<Direction>,
)

data class Station(
    val name: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Int,
    val routes: List<RouteCard>,
)

class RateLimited : IOException("Rate limited by Transit API")

/** Client for Transit's public v3 API (https://api-doc.transitapp.com). */
class TransitApi(private val apiKey: String) {

    fun nearestStation(lat: Double, lon: Double): Station? =
        pickNearestStation(lat, lon, fetchNearbyRoutes(lat, lon))

    private fun fetchNearbyRoutes(lat: Double, lon: Double): JSONObject {
        val url = URL(
            "https://external.transitapp.com/v3/public/nearby_routes" +
                "?lat=$lat&lon=$lon&max_distance=$SEARCH_RADIUS_M&should_update_realtime=true"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("apiKey", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            when (val code = conn.responseCode) {
                200 -> return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                429 -> throw RateLimited()
                401, 403 -> throw IOException("API key rejected ($code)")
                else -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private class StopRef(
        val id: String, val name: String, val parentId: String?,
        val lat: Double, val lon: Double, val distanceM: Double,
    )

    /**
     * The nearest stop and every bus route serving it. Stops sharing its name,
     * parent station, or sitting within [CLUSTER_RADIUS_M] (the stop across the
     * street) count as the same station, so both directions of a route show up.
     */
    private fun pickNearestStation(lat: Double, lon: Double, json: JSONObject): Station? {
        val all = json.optJSONArray("routes") ?: return null
        val routes = (0 until all.length()).map { all.getJSONObject(it) }
        val buses = routes.filter {
            it.optInt("route_type", -1) == 3 || it.optString("mode_name").contains("bus", true)
        }.ifEmpty { routes }

        fun stopOf(itin: JSONObject): StopRef? {
            val s = itin.optJSONObject("closest_stop") ?: return null
            val sLat = s.optDouble("stop_lat")
            val sLon = s.optDouble("stop_lon")
            val dist = if (sLat.isNaN() || sLon.isNaN()) s.optDouble("distance", Double.MAX_VALUE)
            else haversineM(lat, lon, sLat, sLon)
            return StopRef(
                id = s.optString("global_stop_id"),
                name = s.optString("stop_name"),
                parentId = s.optString("parent_station_global_stop_id").ifBlank { null },
                lat = sLat, lon = sLon, distanceM = dist,
            )
        }

        fun itinerariesOf(route: JSONObject) =
            route.optJSONArray("itineraries")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()

        val nearest = buses.flatMap { r -> itinerariesOf(r).mapNotNull(::stopOf) }
            .minByOrNull { it.distanceM } ?: return null

        fun sameStation(s: StopRef) =
            s.id == nearest.id ||
                s.name.equals(nearest.name, ignoreCase = true) ||
                (s.parentId != null && s.parentId == nearest.parentId) ||
                (!s.lat.isNaN() && haversineM(s.lat, s.lon, nearest.lat, nearest.lon) <= CLUSTER_RADIUS_M)

        val cards = buses.mapNotNull { route ->
            val itins = itinerariesOf(route)
            if (itins.none { itin -> stopOf(itin)?.let(::sameStation) == true }) return@mapNotNull null
            val directions = itins.map { itin ->
                Direction(
                    headsign = itin.optString("merged_headsign")
                        .ifBlank { itin.optString("direction_headsign") }
                        .ifBlank { itin.optString("headsign") },
                    stopName = itin.optJSONObject("closest_stop")?.optString("stop_name").orEmpty(),
                    departures = parseDepartures(itin),
                )
            }
            RouteCard(
                id = route.optString("global_route_id"),
                shortName = route.optString("route_short_name").ifBlank { route.optString("route_long_name") },
                longName = route.optString("route_long_name"),
                color = parseColor(route.optString("route_color"), 0xFF3A3A3A.toInt()),
                textColor = parseColor(route.optString("route_text_color"), Color.WHITE),
                directions = directions,
            )
        }

        return Station(nearest.name, nearest.lat, nearest.lon, nearest.distanceM.toInt(), cards)
    }

    private fun parseDepartures(itin: JSONObject): List<Departure> {
        val items = itin.optJSONArray("schedule_items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it) }
            .filterNot { it.optBoolean("is_cancelled") }
            .map { Departure(it.optLong("departure_time"), it.optBoolean("is_real_time")) }
            .filter { it.epochSec > 0 }
            .sortedBy { it.epochSec }
    }

    private fun parseColor(hex: String, fallback: Int): Int =
        runCatching { Color.parseColor("#" + hex.removePrefix("#")) }.getOrDefault(fallback)

    companion object {
        const val SEARCH_RADIUS_M = 800
        const val CLUSTER_RADIUS_M = 90.0
    }
}

fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
