package app.transitime

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/*
 * Radar mode: the opening radar, kept running. Every nearby route is a blip at a random
 * bearing; pinned routes sit close to the centre, the rest further out. Tap a blip for its
 * times (swipe left/right there to switch direction), hold a blip to pin it, tap the
 * centre to sync, and the gear at the bottom opens settings.
 */

private val Phosphor = Color(0xFF6BFF8E)
private val PhosphorDim = Color(0xFF3C9A55)
private val RadarGrid = Color(0xFF1E6B34)
private val RadarBg = Brush.radialGradient(listOf(Color(0xFF0B2410), Color.Black))

/** A blip's place on the scope: bearing in degrees (0 = 3 o'clock, clockwise) and distance as a fraction of the radius. */
private data class Polar(val deg: Float, val dist: Float) {
    val x get() = cos(Math.toRadians(deg.toDouble())).toFloat() * dist
    val y get() = sin(Math.toRadians(deg.toDouble())).toFloat() * dist
}

/**
 * Keeps each route's blip where it first landed (so the scope doesn't reshuffle every
 * sync) and places new ones at the free-est of a few random spots in their ring.
 */
private class BlipLayout {
    private val placed = mutableMapOf<String, Pair<Boolean, Polar>>()

    /** Spots kept clear: the centre (sync), the stop name at the top, the gear at the bottom. */
    private val keepClear = listOf(Polar(0f, 0f), Polar(-90f, 0.6f), Polar(-60f, 0.62f), Polar(-120f, 0.62f), Polar(90f, 0.8f))

    fun update(routes: List<RouteCard>, pins: List<String>): Map<String, Polar> {
        val ids = routes.map { it.id }.toSet()
        placed.keys.retainAll(ids)
        routes.forEach { r ->
            val pinned = r.shortName in pins
            if (placed[r.id]?.first == pinned) return@forEach
            placed.remove(r.id)
            val rnd = Random((r.id + pinned).hashCode())
            val others = placed.values.map { it.second } + keepClear
            val best = (0 until 24).map {
                val dist = if (pinned) 0.24f + rnd.nextFloat() * 0.2f else 0.52f + rnd.nextFloat() * 0.26f
                Polar(rnd.nextFloat() * 360f, dist)
            }.maxBy { p -> others.minOfOrNull { o -> hypot(p.x - o.x, p.y - o.y) } ?: 1f }
            placed[r.id] = pinned to best
        }
        return placed.mapValues { it.value.second }
    }
}

@Composable
internal fun RadarApp(controller: Controller, onExit: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()
    val backStack by nav.currentBackStack.collectAsStateWithLifecycle()
    val atRoot = backStack.count { it.destination.route != null } <= 1

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Same arrangement as the classic layout: only the radar's own swipe closes the app.
    val rootSwipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(atRoot) { if (atRoot) rootSwipe.snapTo(SwipeToDismissValue.Default) }

    AppScaffold(timeText = { TimeText() }) {
        BasicSwipeToDismissBox(onDismissed = onExit, state = rootSwipe, userSwipeEnabled = atRoot) { isBackground ->
            if (!isBackground) Box(Modifier.fillMaxSize()) {
                SwipeDismissableNavHost(navController = nav, startDestination = "radar") {
                    composable("radar") {
                        RadarScreen(
                            controller, state, now,
                            onOpen = { route -> nav.navigate("route/${Uri.encode(route.id)}/0") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("route/{route}/{dir}") { entry ->
                        val routeId = Uri.decode(entry.arguments?.getString("route").orEmpty())
                        val dir = entry.arguments?.getString("dir")?.toIntOrNull() ?: 0
                        RadarRouteScreen(controller, state, routeId, dir, now)
                    }
                    composable("settings") { SettingsScreen(controller) }
                }
                SyncBezelText(state, now)
            }
        }
    }
}

@Composable
private fun RadarScreen(
    controller: Controller,
    state: UiState,
    now: Long,
    onOpen: (RouteCard) -> Unit,
    onSettings: () -> Unit,
) {
    val pins by controller.prefs.pins.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scope.launch { controller.sync() } }

    val station = state.station
    val routes = station?.let { sortedRoutes(it, pins) }.orEmpty()
    val layout = remember { BlipLayout() }
    val spots = remember(routes, pins) { layout.update(routes, pins) }

    // Beam angle, read only while drawing so the sweep never recomposes the screen.
    val sweep = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) withFrameNanos {
            sweep.floatValue = (sweep.floatValue + (it - last) / 1_000_000f * 0.12f) % 360f
            last = it
        }
    }
    // Ping wave from the centre while a sync is running.
    val ping = remember { Animatable(0f) }
    LaunchedEffect(state.syncing) {
        if (state.syncing) while (true) {
            ping.snapTo(0f)
            ping.animateTo(1f, tween(900))
        } else ping.snapTo(0f)
    }

    val routesNow by rememberUpdatedState(routes)
    val spotsNow by rememberUpdatedState(spots)
    val needsPermission by rememberUpdatedState(state.needsPermission)

    BoxWithConstraints(Modifier.fillMaxSize().background(RadarBg)) {
        val r = with(density) { minOf(maxWidth, maxHeight).toPx() } / 2f
        val c = Offset(r, r)
        val hitR = with(density) { 30.dp.toPx() }
        fun spotPx(p: Polar) = Offset(c.x + p.x * r, c.y + p.y * r)
        fun blipAt(pos: Offset): RouteCard? = routesNow
            .mapNotNull { rt -> spotsNow[rt.id]?.let { rt to (spotPx(it) - pos).getDistance() } }
            .filter { it.second < hitR }
            .minByOrNull { it.second }?.first

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        when {
                            needsPermission -> permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                            (pos - spotPx(Polar(90f, 0.8f))).getDistance() < hitR -> onSettings()
                            else -> {
                                val hit = blipAt(pos)
                                if (hit != null) {
                                    if (hit.directions.isNotEmpty()) onOpen(hit)
                                } else if ((pos - c).getDistance() < hitR * 1.2f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    scope.launch { controller.sync() }
                                }
                            }
                        }
                    },
                    onLongPress = { pos ->
                        blipAt(pos)?.let { hit ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val pinned = hit.shortName !in controller.prefs.pins.value
                            controller.prefs.togglePin(hit.shortName)
                            Toast.makeText(context, "${if (pinned) "Pinned" else "Unpinned"} ${hit.shortName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val grid = RadarGrid.copy(alpha = 0.55f)
                for (i in 1..4) drawCircle(grid, radius = r * i / 4.2f, center = c, style = Stroke(1.dp.toPx()))
                // Dashed ring between the pinned (inner) and the other (outer) blips.
                drawCircle(
                    PhosphorDim.copy(alpha = 0.35f), radius = r * 0.48f, center = c,
                    style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f))),
                )
                drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
                drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
                if (ping.value > 0f) {
                    drawCircle(
                        Phosphor.copy(alpha = (1f - ping.value) * 0.5f), radius = r * ping.value, center = c,
                        style = Stroke((2f + 4f * (1f - ping.value)).dp.toPx()),
                    )
                }
                rotate(sweep.floatValue, c) {
                    drawCircle(
                        Brush.sweepGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to Phosphor.copy(alpha = 0.4f), center = c),
                        radius = r, center = c,
                    )
                    drawLine(Phosphor.copy(alpha = 0.8f), c, Offset(c.x + r, c.y), 2.dp.toPx(), cap = StrokeCap.Round)
                }
                drawCircle(Phosphor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = c)
                drawCircle(Phosphor, radius = 4.dp.toPx(), center = c)
            }

            routes.forEach { route ->
                val spot = spots[route.id] ?: return@forEach
                key(route.id) {
                    Blip(route, route.shortName in pins, spot, now, r, sweep)
                }
            }

            // Settings "contact" at 6 o'clock.
            Icon(
                Icons.Rounded.Settings, "Settings",
                Modifier.centerAt(spotPx(Polar(90f, 0.8f))).size(20.dp),
                tint = PhosphorDim,
            )

            // Stop name under the clock.
            Column(
                Modifier.centerAt(spotPx(Polar(-90f, 0.6f))).width(with(density) { (r * 1.1f).toDp() }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    station?.name ?: if (state.error == null) "Scanning…" else "—",
                    style = MaterialTheme.typography.labelMedium, color = Phosphor, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    state.needsPermission -> "Tap to allow location"
                    state.error != null -> state.error
                    station != null -> "${station.distanceM} m · " + if (station.source == "Transit") "Powered by Transit" else station.source
                    else -> null
                }
                if (sub != null) {
                    Text(
                        sub, fontSize = 10.sp, color = if (state.error != null && !state.needsPermission) Warn else PhosphorDim,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One route on the scope: a dot ringed in the route's colour, its number above and the
 * next departure below. It glows as the beam passes, and springs out from the centre
 * the first time it shows up (or moves ring when pinned/unpinned).
 */
@Composable
private fun Blip(route: RouteCard, pinned: Boolean, spot: Polar, now: Long, r: Float, sweep: State<Float>) {
    val pos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(spot) {
        pos.animateTo(Offset(spot.x, spot.y), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    val train = route.mode == Mode.TRAIN
    val tint = if (train) TrainAccent else Phosphor
    val next = route.directions.flatMap { it.upcoming(now) }.minByOrNull { it.epochSec }
    val ring = Color(route.color)
    Column(
        Modifier
            .centerAt(Offset(r + pos.value.x * r, r + pos.value.y * r))
            .graphicsLayer { alpha = (hypot(pos.value.x, pos.value.y) / spot.dist.coerceAtLeast(0.01f)).coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            route.shortName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1,
            modifier = Modifier.offset(y = 2.dp),
        )
        Box(
            Modifier.size(22.dp).drawBehind {
                // Brightest just after the beam passes, never fully dark.
                val since = ((sweep.value - spot.deg) % 360f + 360f) % 360f
                val glow = 0.35f + 0.65f * (1f - since / 360f)
                val cc = center
                drawCircle(tint.copy(alpha = glow * 0.3f), radius = size.minDimension / 2, center = cc)
                if (pinned) drawCircle(tint.copy(alpha = glow), radius = 8.dp.toPx(), center = cc, style = Stroke(1.5.dp.toPx()))
                drawCircle(ring, radius = 5.5.dp.toPx(), center = cc)
                drawCircle(tint.copy(alpha = 0.6f + 0.4f * glow), radius = 3.5.dp.toPx(), center = cc)
            }
        )
        Text(
            next?.let { minutesLabel(it.epochSec, now) } ?: "—",
            fontSize = 10.sp, color = if (next?.realtime == true) tint else PhosphorDim, maxLines = 1,
            modifier = Modifier.offset(y = (-2).dp),
        )
    }
}

/** Places the element so its centre sits at [p] (px, in the parent's coordinates). */
private fun Modifier.centerAt(p: Offset): Modifier = wrapContentSize(unbounded = true, align = Alignment.TopStart).layout { m, cs ->
    val placeable = m.measure(cs.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        placeable.place((p.x - placeable.width / 2f).toInt(), (p.y - placeable.height / 2f).toInt())
    }
}

/**
 * A route's page in radar colours. Swipe left/right to switch direction; swiping right on
 * the first direction goes back to the radar. Hold anywhere to pin.
 */
@Composable
private fun RadarRouteScreen(controller: Controller, state: UiState, routeId: String, dirIndex: Int, now: Long) {
    val haptics = LocalHapticFeedback.current
    val pins by controller.prefs.pins.collectAsStateWithLifecycle()
    val route = state.station?.routes?.firstOrNull { it.id == routeId }
    val directions = route?.directions.orEmpty()
    val pager = rememberPagerState(initialPage = dirIndex.coerceAtMost((directions.size - 1).coerceAtLeast(0))) {
        directions.size
    }
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.currentPage }.collect {
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            first = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(RadarBg).pointerInput(route?.shortName) {
            detectTapGestures(onLongPress = {
                if (route != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.prefs.togglePin(route.shortName)
                }
            })
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            for (i in 1..4) drawCircle(RadarGrid.copy(alpha = 0.3f), radius = r * i / 4.2f, style = Stroke(1.dp.toPx()))
        }
        if (route == null || directions.isEmpty()) {
            Text(
                "Route no longer nearby", color = PhosphorDim, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            HorizontalPager(state = pager, rotaryScrollableBehavior = null) { page ->
                RadarDirectionPage(route, directions[page], route.shortName in pins, now)
            }
            if (directions.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(directions.size) { i ->
                        val on = i == pager.currentPage
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (on) 7.dp else 5.dp).clip(CircleShape)
                                .background(if (on) Phosphor else PhosphorDim.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarDirectionPage(route: RouteCard, dir: Direction, pinned: Boolean, now: Long) {
    val context = LocalContext.current
    val clock = clockFormat(context)
    val train = route.mode == Mode.TRAIN
    val tint = if (train) TrainAccent else Phosphor
    val upcoming = dir.upcoming(now)
    val listState = rememberTransformingLazyColumnState()
    ResetOnSplash { listState.scrollToItem(0) }

    ScreenScaffold(
        scrollState = listState,
        contentPadding = PaddingValues(top = 40.dp, start = 22.dp, end = 22.dp, bottom = 30.dp),
    ) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RouteTitle(route, pinned)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "→ ${dir.headsign}", style = MaterialTheme.typography.titleMedium, color = tint,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (dir.stopName.isNotBlank()) {
                        Text(
                            dir.stopName, fontSize = 11.sp, color = PhosphorDim, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val first = upcoming.firstOrNull()
                        if (first?.realtime == true) {
                            LiveDot(tint)
                            Spacer(Modifier.width(6.dp))
                        }
                        RollingText(first?.let { minutesLabel(it.epochSec, now) } ?: "—", tint)
                    }
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(RadarGrid))
                }
            }
            if (upcoming.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No upcoming departures", fontSize = 12.sp, color = PhosphorDim,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(upcoming.size, key = { upcoming[it].epochSec }) { i ->
                val dep = upcoming[i]
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (dep.realtime) "● " else "") + minutesOnly(dep.epochSec, now),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (dep.realtime) tint else Phosphor.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    Text(clock.format(Date(dep.epochSec * 1000)), fontSize = 14.sp, color = PhosphorDim, maxLines = 1)
                }
            }
        }
    }
}
