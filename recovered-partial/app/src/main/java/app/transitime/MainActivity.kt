package app.transitime

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Light-green Material 3 scheme (Material You tones seeded from #A6F2A0). */
private val GreenScheme = ColorScheme(
    primary = Color(0xFFA6F2A0),
    primaryDim = Color(0xFF8AD884),
    primaryContainer = Color(0xFF1E4D26),
    onPrimary = Color(0xFF07310F),
    onPrimaryContainer = Color(0xFFC6F8C0),
    secondary = Color(0xFFBCD7B6),
    secondaryDim = Color(0xFFA1BC9B),
    secondaryContainer = Color(0xFF2B3E2B),
    onSecondary = Color(0xFF1F3520),
    onSecondaryContainer = Color(0xFFD7F0D0),
    tertiary = Color(0xFF9FE3D2),
    tertiaryDim = Color(0xFF84C8B7),
    tertiaryContainer = Color(0xFF16463D),
    onTertiary = Color(0xFF00382F),
    onTertiaryContainer = Color(0xFFBDF8E8),
    surfaceContainerLow = Color(0xFF111812),
    surfaceContainer = Color(0xFF18211A),
    surfaceContainerHigh = Color(0xFF222D23),
    onSurface = Color(0xFFE2EEDF),
    onSurfaceVariant = Color(0xFFB4C4B1),
    outline = Color(0xFF7F907D),
    outlineVariant = Color(0xFF3F4C3E),
    background = Color.Black,
    onBackground = Color.White,
)

private val Warn = Color(0xFFFFB86B)

class MainActivity : ComponentActivity() {
    private lateinit var controller: Controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = Controller(applicationContext)

        // Sync loop runs only while the app is on screen; it stops (and so does
        // location) as soon as the app is closed or the screen goes off.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(controller.prefs.interval, controller.prefs.source) { sec, _ -> sec }.collectLatest { sec ->
                    while (true) {
                        controller.sync()
                        delay(sec * 1000L)
                    }
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = GreenScheme, motionScheme = MotionScheme.expressive()) {
                App(controller)
            }
        }
    }
}

@Composable
private fun App(controller: Controller) {
    val state by controller.state.collectAsStateWithLifecycle()
    val interval by controller.prefs.interval.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()

    // 1 Hz tick so countdowns and "synced Xs ago" stay live between syncs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    AppScaffold(timeText = { SyncTimeText(state, now) }) {
        // Each screen is its own swipe-to-dismiss page, so swiping right goes back
        // one screen instead of closing the app.
        SwipeDismissableNavHost(navController = nav, startDestination = "main") {
            composable("main") {
                MainScreen(
                    controller, state, now,
                    onOpen = { route, dir -> nav.navigate("detail/${Uri.encode(route.id)}/$dir") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("detail/{route}/{dir}") { entry ->
                val routeId = Uri.decode(entry.arguments?.getString("route").orEmpty())
                val dir = entry.arguments?.getString("dir")?.toIntOrNull() ?: 0
                DetailScreen(controller, state, routeId, dir, now)
            }
            composable("settings") { SettingsScreen(controller) }
        }
        NextSyncRing(state, interval, now)
    }
}

/** Last sync, curved along the top bezel next to the clock. */
@Composable
private fun SyncTimeText(state: UiState, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val label = when {
        state.syncing -> "syncing…"
        state.lastSyncMs == 0L -> "not synced"
        else -> "synced ${ago(now - state.lastSyncMs)}"
    }
    val color = if (state.error != null && !state.syncing) Warn else scheme.primary
    TimeText { time ->
        timeTextCurvedText(label, CurvedTextStyle(color = color))
        timeTextSeparator()
        timeTextCurvedText(time)
    }
}

/** Thin arc on the left bezel that fills up until the next automatic sync. */
@Composable
private fun NextSyncRing(state: UiState, interval: Int, now: Long) {
    if (state.attemptMs == 0L) return
    val target = ((now - state.attemptMs) / (interval * 1000f)).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        target, animationSpec = if (target < 0.05f) spring() else tween(1000, easing = LinearEasing), label = "ring",
    )
    CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxSize().padding(3.dp),
        startAngle = 125f,
        endAngle = 235f,
        strokeWidth = 4.dp,
        colors = ProgressIndicatorDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun MainScreen(
    controller: Controller,
    state: UiState,
    now: Long,
    onOpen: (RouteCard, Int) -> Unit,
    onSettings: () -> Unit,
) {
    val pins by controller.prefs.pins.collectAsStateWithLifecycle()
    val interval by controller.prefs.interval.collectAsStateWithLifecycle()
    val source by controller.prefs.source.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scope.launch { controller.sync() } }

    ScreenScaffold(
        scrollState = listState,
        // Tight top padding keeps the stop header right under the bezel.
        contentPadding = PaddingValues(top = 28.dp, start = 10.dp, end = 10.dp, bottom = 8.dp),
        edgeButton = {
            EdgeButton(onClick = { scope.launch { controller.sync() } }) {
                SpinningRefresh(state.syncing)
            }
        },
    ) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.needsPermission) {
                item(key = "perm") {
                    FilledTonalButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.LocationOn, null) },
                        label = { Text("Allow location") },
                    )
                }
            }

            item(key = "header") { StationHeader(state, spec) }

            val station = state.station
            if (station != null) {
                val sorted = station.routes.sortedWith(
                    compareBy<RouteCard> { r -> pins.indexOf(r.shortName).let { if (it < 0) Int.MAX_VALUE else it } }
                        .thenBy { r -> r.directions.flatMap { it.departures }.minOfOrNull { it.epochSec } ?: Long.MAX_VALUE }
                )
                items(sorted, key = { it.id }) { route ->
                    RouteCardView(
                        route = route,
                        pinned = route.shortName in pins,
                        stationName = station.name,
                        now = now,
                        spec = spec,
                        modifier = Modifier.animateItem(),
                        onOpen = { dir -> onOpen(route, dir) },
                        onTogglePin = { controller.prefs.togglePin(route.shortName) },
                    )
                }
                item(key = "hint") {
                    Text(
                        "Swipe ⇄ direction · Tap for times\nHold to pin",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }

            item(key = "settings") {
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("Settings") },
                    secondaryLabel = { Text("Every ${interval}s · ${source.short}") },
                )
            }
        }
    }
}

@Composable
private fun SpinningRefresh(spinning: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(700, easing = LinearEasing))
        } else {
            // Settle on the next full turn with a little overshoot.
            rotation.animateTo(
                kotlin.math.ceil(rotation.value / 360f) * 360f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
    }
    Icon(Icons.Rounded.Refresh, "Refresh now", Modifier.graphicsLayer { rotationZ = rotation.value })
}

@Composable
private fun TransformingLazyColumnItemScope.StationHeader(state: UiState, spec: TransformationSpec) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val station = state.station
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingIcon()
            Spacer(Modifier.width(4.dp))
            Text(
                "NEAREST STOP", style = MaterialTheme.typography.labelSmall,
                color = scheme.primary, fontWeight = FontWeight.Bold,
            )
        }
        AnimatedContent(
            targetState = station?.name ?: if (state.error == null) "Finding stop…" else "—",
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "stop",
        ) { name ->
            Text(
                name, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (station != null) {
            Text(
                "${station.distanceM} m · %.4f, %.4f".format(station.lat, station.lon),
                style = MaterialTheme.typography.bodyExtraSmall, color = scheme.onSurfaceVariant,
            )
            if (state.lastSyncMs > 0) {
                Text(
                    "Synced ${clockFormat(context, seconds = true).format(Date(state.lastSyncMs))} · ${station.source}",
                    style = MaterialTheme.typography.bodyExtraSmall, color = scheme.primaryDim,
                )
            }
        }
        AnimatedVisibility(visible = state.error != null) {
            Text(
                state.error.orEmpty(), style = MaterialTheme.typography.bodyExtraSmall, color = Warn,
                textAlign = TextAlign.Center, maxLines = 2,
            )
        }
    }
}

@Composable
private fun PulsingIcon() {
    val t = rememberInfiniteTransition(label = "loc")
    val scale by t.animateFloat(0.8f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Icon(
        Icons.Rounded.MyLocation, null,
        Modifier.size(12.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TransformingLazyColumnItemScope.RouteCardView(
    route: RouteCard,
    pinned: Boolean,
    stationName: String,
    now: Long,
    spec: TransformationSpec,
    modifier: Modifier,
    onOpen: (Int) -> Unit,
    onTogglePin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val pager = rememberPagerState { route.directions.size }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val container by animateColorAsState(if (pinned) scheme.secondaryContainer else scheme.surfaceContainer, label = "bg")
    val borderColor by animateColorAsState(if (pinned) scheme.primary else Color.Transparent, label = "border")

    // Tick on every direction flip.
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.currentPage }.collect {
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            first = false
        }
    }

    Card(
        onClick = { if (route.directions.isNotEmpty()) onOpen(pager.currentPage) },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onTogglePin()
        },
        onLongClickLabel = if (pinned) "Unpin" else "Pin",
        modifier = modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        transformation = SurfaceTransformation(spec),
        interactionSource = interaction,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.5.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) {
        RouteTitle(route, pinned)
        Spacer(Modifier.height(4.dp))
        // Swipe left/right to flip between directions.
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            DirectionRow(route.directions[page], stationName, now)
        }
        if (route.directions.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.Center) {
                repeat(route.directions.size) { i ->
                    val on = i == pager.currentPage
                    val w by animateDpAsState(
                        if (on) 14.dp else 5.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dot",
                    )
                    val c by animateColorAsState(if (on) scheme.primary else scheme.outlineVariant, label = "dotc")
                    Box(
                        Modifier.padding(horizontal = 2.dp).size(width = w, height = 5.dp)
                            .clip(CircleShape).background(c)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteTitle(route: RouteCard, pinned: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(route.color))
                .padding(horizontal = 8.dp, vertical = 1.dp),
        ) {
            Text(
                route.shortName, color = Color(route.textColor), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            route.longName.takeIf { it != route.shortName }.orEmpty(),
            style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = pinned,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioHighBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DirectionRow(dir: Direction, stationName: String, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val upcoming = dir.departures.filter { it.epochSec * 1000 > now - 30_000 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "→ ${dir.headsign}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (dir.stopName.isNotBlank() && !dir.stopName.equals(stationName, true)) {
                Text(
                    dir.stopName, style = MaterialTheme.typography.bodyExtraSmall, color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            val first = upcoming.firstOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (first?.realtime == true) {
                    LiveDot()
                    Spacer(Modifier.width(4.dp))
                }
                RollingText(
                    first?.let { minutesLabel(it.epochSec, now) } ?: "—",
                    color = if (first?.realtime == true) scheme.primary else scheme.onSurface,
                )
            }
            val next = upcoming.drop(1).take(2)
            if (next.isNotEmpty()) {
                Text(
                    next.joinToString(" · ") { minutesLabel(it.epochSec, now) },
                    style = MaterialTheme.typography.bodyExtraSmall, color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Odometer-style roll whenever the countdown changes. */
@Composable
private fun RollingText(text: String, color: Color) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn()) togetherWith
                (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut()) using
                SizeTransform(clip = false)
        },
        label = "roll",
    ) { t ->
        Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun LiveDot() {
    val color = MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "live")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring")
    Box(
        Modifier
            .size(7.dp)
            .drawBehind {
                // Expanding ring behind a solid dot.
                drawCircle(color.copy(alpha = (1f - ring) * 0.6f), radius = size.minDimension / 2 * (1f + ring * 1.4f))
                drawCircle(color, radius = size.minDimension / 2)
            }
    )
}

@Composable
private fun DetailScreen(controller: Controller, state: UiState, routeId: String, dirIndex: Int, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pins by controller.prefs.pins.collectAsStateWithLifecycle()
    val route = state.station?.routes?.firstOrNull { it.id == routeId }
    val dir = route?.directions?.getOrNull(dirIndex)
    val pinned = route != null && route.shortName in pins
    val clock = clockFormat(context)
    val upcoming = dir?.departures?.filter { it.epochSec * 1000 > now - 30_000 }.orEmpty()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        contentPadding = PaddingValues(top = 28.dp, start = 10.dp, end = 10.dp, bottom = 8.dp),
        edgeButton = {
            if (route != null) {
                EdgeButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.prefs.togglePin(route.shortName)
                    },
                    colors = if (pinned) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Rounded.PushPin, if (pinned) "Unpin" else "Pin")
                }
            }
        },
    ) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item(key = "head") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow)))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (route != null && dir != null) {
                        RouteTitle(route, pinned)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "→ ${dir.headsign}", style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (dir.stopName.isNotBlank()) {
                            Text(
                                dir.stopName, style = MaterialTheme.typography.bodyExtraSmall, color = scheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text("Route no longer nearby", color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
            if (route != null && upcoming.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No upcoming departures", style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(upcoming.size, key = { upcoming[it].epochSec }) { i ->
                StaggeredIn(i) {
                    DepartureRow(upcoming[i], now, clock, highlight = i == 0, spec = spec, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

/** Items slide + fade in one after another when the screen opens. */
@Composable
private fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40L * index.coerceAtMost(8))
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "a")
    val dy by animateDpAsState(
        if (shown) 0.dp else 18.dp, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "dy",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = dy.toPx() }) { content() }
}

@Composable
private fun TransformingLazyColumnItemScope.DepartureRow(
    dep: Departure, now: Long, clock: SimpleDateFormat, highlight: Boolean, spec: TransformationSpec, modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = {},
        modifier = modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        colors = CardDefaults.cardColors(containerColor = if (highlight) scheme.primaryContainer else scheme.surfaceContainer),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dep.realtime) {
                LiveDot()
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.weight(1f)) {
                RollingText(minutesOnly(dep.epochSec, now), if (dep.realtime) scheme.primary else scheme.onSurface)
            }
            Text(clock.format(Date(dep.epochSec * 1000)), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(controller: Controller) {
    val interval by controller.prefs.interval.collectAsStateWithLifecycle()
    val source by controller.prefs.source.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        contentPadding = PaddingValues(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 40.dp),
    ) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item(key = "h1") {
                ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text("Sync every")
                }
            }
            items(controller.prefs.intervalChoices, key = { "i$it" }) { sec ->
                RadioButton(
                    selected = sec == interval,
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        controller.prefs.setInterval(sec)
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text("$sec seconds") },
                    secondaryLabel = if (sec == 25) ({ Text("Default") }) else null,
                )
            }
            item(key = "h2") {
                ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text("Data source")
                }
            }
            items(SourceMode.entries, key = { it.name }) { mode ->
                RadioButton(
                    selected = mode == source,
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        controller.prefs.setSource(mode)
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(mode.short) },
                    secondaryLabel = { Text(mode.detail) },
                )
            }
        }
    }
}

private fun minutesLabel(epochSec: Long, now: Long): String {
    val mins = ((epochSec * 1000 - now) / 60_000).toInt()
    return when {
        mins <= 0 -> "Now"
        mins < 60 -> "$mins min"
        else -> "${mins / 60}h %02d".format(mins % 60)
    }
}

private fun minutesOnly(epochSec: Long, now: Long): String {
    val mins = ((epochSec * 1000 - now) / 60_000).toInt()
    return when {
        mins <= 0 -> "Now"
        mins < 60 -> "$mins min"
        else -> "${mins / 60} h ${mins % 60} min"
    }
}

/** 12h or 24h following the watch's system setting. */
private fun clockFormat(context: android.content.Context, seconds: Boolean = false): SimpleDateFormat {
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val pattern = when {
        is24 && seconds -> "HH:mm:ss"
        is24 -> "HH:mm"
        seconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    return SimpleDateFormat(pattern, Locale.getDefault())
}

private fun ago(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s ago" else "${s / 60}m ago"
}
