package app.transitime

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Pod mode: an iPod-style menu in radar colours (phosphor green on black), after TeenyTune.
 * Nothing on screen is a hit target. The touchscreen is the click wheel: drag up/down
 * (or turn the crown) moves the highlight, a tap anywhere opens the highlighted row,
 * holding anywhere pins/unpins the highlighted route, and a one-finger swipe right goes
 * back (and closes the app from the top menu).
 */

private object Pod {
    val screen = Color(0xFF06170B)
    val screenLo = Color.Black
    val text = Color(0xFF9DF5B1)
    val subText = Color(0xFF4F9C64)
    val barHi = Color(0xFF123B1E)
    val barLo = Color(0xFF051208)
    val barText = Color(0xFFC2FFD0)
    val selHi = Color(0xFF45E585)
    val selLo = Color(0xFF12803C)
    val selText = Color.Black
    val grid = Color(0xFF1E6B34)
    val train = TrainAccent
}

private object PodMotion {
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 1800f)
    fun <T> scroll(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 1500f)
    val slide: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 0.78f, stiffness = 1700f, visibilityThreshold = IntOffset.VisibilityThreshold)
}

private sealed interface PodScreen {
    data object Home : PodScreen
    data object Stop : PodScreen
    data class Route(val id: String) : PodScreen
    data class Times(val id: String, val dir: Int) : PodScreen
    data object Settings : PodScreen
}

private var nextEntryId = 0

/** One level of the menu stack; remembers its highlighted row like the iPod does. */
private class PodEntry(val screen: PodScreen) {
    val id = nextEntryId++
    var sel by mutableIntStateOf(0)
    var scroll = 0f
}

private class PodItem(
    val label: String,
    val detail: String? = null,
    val chevron: Boolean = true,
    /** Little route-colour dot before the label. */
    val dot: Color? = null,
    val color: Color? = null,
    val onLongPress: (() -> Unit)? = null,
    val onClick: () -> Unit = {},
)

/** Screen geometry in px: [d] is the round screen's diameter, [barH] the title bar height. */
private class PodGeom(val d: Float, val barH: Float)

private val LocalPodGeom = staticCompositionLocalOf { PodGeom(300f, 60f) }

/** Crown ticks go to whichever list is on top. */
private class PodWheel {
    var tick: ((Int) -> Boolean)? = null
}

private val LocalPodWheel = staticCompositionLocalOf { PodWheel() }

@Composable
internal fun PodApp(controller: Controller, onExit: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val pins by controller.prefs.pins.collectAsStateWithLifecycle()
    val source by controller.prefs.source.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stack = remember { mutableStateListOf(PodEntry(PodScreen.Home)) }
    var forward by remember { mutableStateOf(true) }
    val wheel = remember { PodWheel() }
    val focus = remember { FocusRequester() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scope.launch { controller.sync() } }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun push(s: PodScreen) { forward = true; stack.add(PodEntry(s)) }
    fun back() {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (stack.size <= 1) onExit() else { forward = false; stack.removeAt(stack.lastIndex) }
    }
    fun togglePin(route: RouteCard) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        controller.prefs.togglePin(route.shortName)
    }
    fun tick(dir: Int) {
        if (wheel.tick?.invoke(dir) == true) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val station = state.station
    fun route(id: String) = station?.routes?.firstOrNull { it.id == id }

    @Composable
    fun itemsFor(s: PodScreen): List<PodItem> = when (s) {
        PodScreen.Home -> buildList {
            if (state.needsPermission) add(PodItem("Allow location", chevron = false) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            })
            if (state.error != null && !state.syncing) {
                add(PodItem(state.error.orEmpty(), chevron = false, color = Warn) { scope.launch { controller.sync() } })
            }
            add(
                PodItem(
                    station?.name ?: "Finding stop…",
                    detail = station?.let { "${it.distanceM} m" },
                    chevron = station != null,
                ) { if (station != null) push(PodScreen.Stop) }
            )
            if (station != null) sortedRoutes(station, pins).forEach { r ->
                val pinned = r.shortName in pins
                val next = r.directions.flatMap { it.upcoming(now) }.minByOrNull { it.epochSec }
                add(
                    PodItem(
                        (if (pinned) "★ " else "") + r.shortName + " " +
                            (r.directions.firstOrNull()?.headsign ?: r.longName),
                        detail = next?.let { minutesLabel(it.epochSec, now) } ?: "—",
                        dot = Color(r.color),
                        color = if (r.mode == Mode.TRAIN) Pod.train else null,
                        onLongPress = { togglePin(r) },
                    ) { push(PodScreen.Route(r.id)) }
                )
            }
            add(
                PodItem(
                    if (state.syncing) "Syncing…" else "Sync now",
                    detail = if (state.lastSyncMs == 0L) null else ago(now - state.lastSyncMs),
                    chevron = false,
                ) { scope.launch { controller.sync() } }
            )
            add(PodItem("Settings", detail = source.short) { push(PodScreen.Settings) })
        }

        PodScreen.Stop -> buildList {
            if (station != null) {
                add(PodItem(station.name, chevron = false))
                add(PodItem("Distance", detail = "${station.distanceM} m", chevron = false))
                add(PodItem("%.4f, %.4f".format(station.lat, station.lon), chevron = false))
                if (state.lastSyncMs > 0) {
                    add(
                        PodItem(
                            "Synced", detail = clockFormat(context, seconds = true).format(Date(state.lastSyncMs)),
                            chevron = false,
                        )
                    )
                }
                add(PodItem(if (station.source == "Transit") "Powered by Transit" else station.source, chevron = false))
            }
        }

        is PodScreen.Route -> buildList {
            val r = route(s.id)
            if (r != null) {
                r.directions.forEachIndexed { i, dir ->
                    val next = dir.upcoming(now).firstOrNull()
                    add(
                        PodItem(
                            "→ ${dir.headsign}",
                            detail = next?.let { minutesLabel(it.epochSec, now) } ?: "—",
                            onLongPress = { togglePin(r) },
                        ) { push(PodScreen.Times(r.id, i)) }
                    )
                }
                add(PodItem(if (r.shortName in pins) "Unpin route" else "Pin route", chevron = false) { togglePin(r) })
            }
        }

        is PodScreen.Times -> buildList {
            val r = route(s.id)
            val dir = r?.directions?.getOrNull(s.dir)
            if (r != null && dir != null) {
                val clock = clockFormat(context)
                if (dir.stopName.isNotBlank()) add(PodItem(dir.stopName, chevron = false, color = Pod.subText))
                dir.upcoming(now).forEach { dep ->
                    add(
                        PodItem(
                            (if (dep.realtime) "● " else "") + minutesOnly(dep.epochSec, now),
                            detail = clock.format(Date(dep.epochSec * 1000)),
                            chevron = false,
                            color = if (dep.realtime && r.mode == Mode.TRAIN) Pod.train else null,
                            onLongPress = { togglePin(r) },
                        )
                    )
                }
            }
        }

        PodScreen.Settings -> SourceMode.entries.map { mode ->
            PodItem(mode.short, detail = if (mode == source) "✓" else mode.detail, chevron = false) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                controller.prefs.setSource(mode)
            }
        }
    }

    fun titleOf(s: PodScreen): String = when (s) {
        PodScreen.Home -> "Transi-Time"
        PodScreen.Stop -> "Nearest stop"
        is PodScreen.Route -> route(s.id)?.let { "Route ${it.shortName}" } ?: "Route"
        is PodScreen.Times -> route(s.id)?.let { r ->
            r.directions.getOrNull(s.dir)?.let { "${r.shortName} → ${it.headsign}" } ?: r.shortName
        } ?: "Times"
        PodScreen.Settings -> "Data source"
    }

    fun emptyOf(s: PodScreen) = when (s) {
        is PodScreen.Route, is PodScreen.Stop -> "No longer nearby"
        is PodScreen.Times -> "No upcoming departures"
        else -> "Nothing here yet"
    }

    CompositionLocalProvider(LocalPodWheel provides wheel) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val d = with(density) { minOf(maxWidth, maxHeight).toPx() }
            val geom = remember(d) { PodGeom(d, d * 0.19f) }
            val rotaryStep = with(density) { 22f * this.density }
            val acc = remember { floatArrayOf(0f) }
            Box(
                Modifier
                    .size(podPx(d))
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Pod.screen, Pod.screenLo)))
                    .onRotaryScrollEvent { e ->
                        val px = e.verticalScrollPixels
                        if (abs(px) >= rotaryStep) {
                            // A detented bezel sends one big event per click: one row.
                            acc[0] = 0f
                            tick(if (px > 0) 1 else -1)
                            return@onRotaryScrollEvent true
                        }
                        if (acc[0] != 0f && (acc[0] > 0) != (px > 0)) acc[0] = 0f
                        acc[0] += px
                        while (abs(acc[0]) >= rotaryStep) {
                            val dir = if (acc[0] > 0) 1 else -1
                            acc[0] -= dir * rotaryStep
                            tick(dir)
                        }
                        true
                    }
                    .focusRequester(focus)
                    .focusable()
                    .backSwipe(::back),
            ) {
                CompositionLocalProvider(LocalPodGeom provides geom) {
                    RadarBackdrop()
                    AnimatedContent(
                        targetState = stack.last(),
                        contentKey = { it.id },
                        transitionSpec = {
                            if (forward) {
                                slideInHorizontally(PodMotion.slide) { it } togetherWith slideOutHorizontally(PodMotion.slide) { -it }
                            } else {
                                slideInHorizontally(PodMotion.slide) { -it } togetherWith slideOutHorizontally(PodMotion.slide) { it }
                            }
                        },
                        label = "pod",
                    ) { entry -> PodMenu(entry, itemsFor(entry.screen), emptyOf(entry.screen)) }
                    PodTitleBar(titleOf(stack.last().screen), state.syncing)
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

/**
 * One-finger swipe right = back. Watched before the lists see it; taps and vertical
 * drags pass through. Two-finger gestures are left alone (the mode switch owns them).
 */
private fun Modifier.backSwipe(onBack: () -> Unit) = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val w = size.width.toFloat()
        var claimed = false
        var done = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.count { it.pressed } >= 2) break
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            val delta = ch.position - down.position
            if (!claimed && delta.x > slop && delta.x > abs(delta.y) * 1.6f) claimed = true
            if (claimed) {
                ch.consume()
                if (!done && delta.x > w * 0.22f) { done = true; onBack() }
            }
            if (!ch.pressed) break
        }
    }
}

/** Faint radar rings and a slow sweep behind the menu. */
@Composable
private fun RadarBackdrop() {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) withFrameNanos {
            angle = (angle + (it - last) / 1_000_000f * 0.06f) % 360f
            last = it
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        val grid = Pod.grid.copy(alpha = 0.35f)
        for (i in 1..4) drawCircle(grid, radius = r * i / 4.2f, center = c, style = Stroke(1f))
        drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        rotate(angle, c) {
            drawCircle(
                Brush.sweepGradient(0f to Color.Transparent, 0.8f to Color.Transparent, 1f to Pod.grid.copy(alpha = 0.45f), center = c),
                radius = r, center = c,
            )
        }
    }
}

/**
 * The iPod list on a round screen: each row is inset to the circle's chord at its
 * height, the green highlight slides between rows, and a curved scrollbar hugs the rim.
 */
@Composable
private fun PodMenu(entry: PodEntry, items: List<PodItem>, empty: String) {
    val g = LocalPodGeom.current
    val wheel = LocalPodWheel.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val d = g.d
    val top = g.barH
    val rowH = d * 0.115f
    val listH = d - top
    val n = items.size
    val maxScroll = max(0f, n * rowH - listH + rowH * 1.1f)
    val sel = entry.sel.coerceIn(0, max(0, n - 1))

    fun scrollFor(i: Int, cur: Float): Float {
        val y = i * rowH
        var s = cur
        val upper = rowH * 0.9f
        val lower = listH - rowH * 2.0f
        if (y - s < upper) s = y - upper
        if (y - s > lower) s = y - lower
        return s.coerceIn(0f, maxScroll)
    }

    val scroll = remember(entry) { Animatable(scrollFor(sel, entry.scroll)) }
    val hi = remember(entry) { Animatable(sel * rowH) }
    LaunchedEffect(sel, n) {
        val t = scrollFor(sel, scroll.targetValue)
        entry.scroll = t
        launch { hi.animateTo(sel * rowH, PodMotion.pop()) }
        scroll.animateTo(t, PodMotion.scroll())
    }
    // Back at the top of the list whenever the opening radar finishes.
    ResetOnSplash { entry.sel = 0 }

    val itemsNow by rememberUpdatedState(items)
    androidx.compose.runtime.DisposableEffect(wheel, entry) {
        val f: (Int) -> Boolean = { delta ->
            val count = itemsNow.size
            if (count == 0) false else {
                val next = (entry.sel + delta).coerceIn(0, count - 1)
                val moved = next != entry.sel
                entry.sel = next
                moved
            }
        }
        wheel.tick = f
        onDispose { if (wheel.tick === f) wheel.tick = null }
    }

    if (n == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                empty,
                Modifier.width(podPx(d * 0.66f)).offset(y = podPx(top * 0.4f)),
                style = TextStyle(color = Pod.subText, fontSize = with(density) { (d * 0.056f).toSp() }, textAlign = TextAlign.Center),
            )
        }
        return
    }

    // The touchscreen is the click wheel: drag moves the highlight, tap opens it, hold pins.
    val touch = Modifier
        .pointerInput(entry) {
            detectTapGestures(
                onTap = {
                    itemsNow.getOrNull(entry.sel)?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch { delay(60); it.onClick() }
                    }
                },
                onLongPress = { itemsNow.getOrNull(entry.sel)?.onLongPress?.invoke() },
            )
        }
        .pointerInput(entry) {
            var acc = 0f
            detectVerticalDragGestures(onDragStart = { acc = 0f }) { ch, dy ->
                ch.consume()
                acc -= dy
                val step = rowH * 0.55f
                while (abs(acc) >= step) {
                    val dir = if (acc > 0) 1 else -1
                    acc -= dir * step
                    val next = (entry.sel + dir).coerceIn(0, itemsNow.size - 1)
                    if (next != entry.sel) {
                        entry.sel = next
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            }
        }

    Box(Modifier.fillMaxSize().then(touch)) {
        val s = scroll.value
        Canvas(Modifier.fillMaxSize()) {
            val y = top + hi.value - s
            drawRect(Brush.verticalGradient(listOf(Pod.selHi, Pod.selLo), y, y + rowH), Offset(0f, y), Size(size.width, rowH))
            drawRect(Color.White.copy(0.3f), Offset(0f, y), Size(size.width, rowH * 0.04f))
            if (maxScroll > 0f) {
                val r = d / 2f - d * 0.03f
                val span = 70f
                val thumb = (span * (listH / (n * rowH))).coerceIn(10f, span)
                val start = -span / 2 + (span - thumb) * (s / maxScroll)
                val tl = Offset(d / 2f - r, d / 2f - r)
                val sz = Size(r * 2, r * 2)
                drawArc(Pod.subText.copy(0.25f), -span / 2, span, false, tl, sz, style = Stroke(d * 0.022f, cap = StrokeCap.Round))
                drawArc(Pod.text.copy(0.8f), start, thumb, false, tl, sz, style = Stroke(d * 0.022f, cap = StrokeCap.Round))
            }
        }
        val first = max(0, floor((s - rowH) / rowH).toInt())
        val last = minOf(n - 1, ((s + listH) / rowH).toInt() + 1)
        val labelSp = with(density) { (d * 0.056f).toSp() }
        val detailSp = with(density) { (d * 0.045f).toSp() }
        for (i in first..last) {
            val item = items[i]
            val y = top + i * rowH - s
            val far = max(abs(y + rowH * 0.18f - d / 2f), abs(y + rowH * 0.82f - d / 2f))
            val half = sqrt(max(0f, (d / 2f) * (d / 2f) - far * far))
            if (half < rowH * 0.8f) continue
            val pad = d * 0.045f
            val left = d / 2f - half + pad
            val w = half * 2 - pad * 2 - (if (maxScroll > 0f && y > d * 0.3f && y < d * 0.7f) d * 0.03f else 0f)
            val selected = i == sel
            val color = if (selected) Pod.selText else item.color ?: Pod.text
            Row(
                Modifier.offset { IntOffset(left.roundToInt(), y.roundToInt()) }.size(podPx(w), podPx(rowH)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.dot?.let {
                    Box(Modifier.size(podPx(d * 0.022f)).clip(CircleShape).background(it))
                    Spacer(Modifier.width(podPx(d * 0.018f)))
                }
                BasicText(
                    item.label,
                    Modifier.weight(1f),
                    style = TextStyle(color = color, fontSize = labelSp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                item.detail?.let {
                    BasicText(
                        it, Modifier.padding(start = podPx(d * 0.02f)),
                        style = TextStyle(color = if (selected) Pod.selText.copy(0.8f) else Pod.subText, fontSize = detailSp),
                        maxLines = 1,
                    )
                }
                if (item.chevron) {
                    BasicText(
                        "›", Modifier.padding(start = podPx(d * 0.015f)),
                        style = TextStyle(color = if (selected) Pod.selText else Pod.subText, fontSize = labelSp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun PodTitleBar(title: String, syncing: Boolean) {
    val g = LocalPodGeom.current
    val density = LocalDensity.current
    val ctx = LocalContext.current
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val fmt = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
            clock = android.text.format.DateFormat.format(fmt, System.currentTimeMillis()).toString()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    // Sync spinner, driven off the frame clock so it turns even with animations off.
    var spin by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(syncing) {
        if (!syncing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos {
            spin = (spin + (it - last) / 1_000_000f * 0.4f) % 360f
            last = it
        }
    }
    val d = g.d
    Box(Modifier.fillMaxWidth().height(podPx(g.barH))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Pod.barHi, Pod.barLo)))
            drawRect(Pod.grid.copy(0.8f), Offset(0f, size.height - d * 0.006f), Size(size.width, d * 0.006f))
            if (syncing) {
                val cy = size.height * 0.66f
                val s = d * 0.032f
                val cx = d / 2f - d * 0.27f
                drawArc(
                    Pod.selHi, spin, 270f, false, Offset(cx - s, cy - s), Size(s * 2, s * 2),
                    style = Stroke(d * 0.012f, cap = StrokeCap.Round),
                )
            }
        }
        BasicText(
            clock,
            Modifier.align(Alignment.TopCenter).offset(y = podPx(d * 0.035f)),
            style = TextStyle(color = Pod.barText.copy(0.6f), fontSize = with(density) { (d * 0.045f).toSp() }, fontWeight = FontWeight.Medium),
        )
        AnimatedContent(
            title,
            Modifier.align(Alignment.TopCenter).offset(y = podPx(g.barH * 0.66f - d * 0.042f)).width(podPx(d * 0.44f)),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "title",
        ) { t ->
            BasicText(
                t,
                style = TextStyle(color = Pod.barText, fontSize = with(density) { (d * 0.058f).toSp() }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun podPx(v: Float): Dp = with(LocalDensity.current) { v.toDp() }
