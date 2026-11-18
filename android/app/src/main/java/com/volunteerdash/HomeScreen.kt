package com.volunteerdash

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun openDirections(ctx: Context, op: Opportunity) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${op.lat},${op.lng}&travelmode=driving")
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { Store.toast = "No maps app available" }
}

fun nextSlotFor(op: Opportunity): Slot? = Demo.slots.firstOrNull {
    it.oppId == op.id && it.start.isAfter(LocalDateTime.now()) && !it.cancelled && Store.spotsLeft(it) > 0
}

var calendarFocus by mutableStateOf<LocalDate?>(null)

@Composable
fun HomeScreen(nav: Nav) {
    var selected by remember { mutableStateOf<Opportunity?>(null) }
    val mapState = remember { MapState() }
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { nav.go(Route.Messages) }) {
                    BadgedBox(badge = { Badge(containerColor = Coral) { Text("1") } }) { Icon(Icons.Filled.ChatBubbleOutline, "Messages", tint = Ink) }
                }
                Box(Modifier.clickable { nav.tab(Route.Profile) }) { MyAvatar(36.dp) }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("A little time to help?", style = MaterialTheme.typography.titleLarge)
                Text("Compare travel, check-in, and shift time.", color = Slate)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Find a shift that fits", color = Cobalt, onClick = { nav.go(Route.ShiftFit) })
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))
                    .clickable { nav.tab(Route.Discover) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = Cobalt)
                    Spacer(Modifier.width(8.dp))
                    Text("Search shifts, causes, places", color = Slate, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                Row(Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(Cobalt)
                    .clickable { Filters.showSheet = true; nav.tab(Route.Discover) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Filter", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp), padding = 0.dp) {
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    TempeMap(mapState, selected, onSelect = { selected = it }, modifier = Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        MapButton(Icons.Filled.Add, "Zoom in") { mapState.zoomBy(1.4f) }
                        Spacer(Modifier.height(6.dp))
                        MapButton(Icons.Filled.Remove, "Zoom out") { mapState.zoomBy(1 / 1.4f) }
                    }
                    if (selected == null) Pill("${Demo.opportunities.size} opportunities nearby", Color.White, Ink, Icons.Filled.Place,
                        Modifier.align(Alignment.TopStart).padding(10.dp).shadow(3.dp, RoundedCornerShape(50)))
                    selected?.let { op ->
                        LaunchedEffect(op.id) { mapState.focusOn(pinLatLng(op)) }
                        PinPopup(op, mapState, onOpen = { nav.go(Route.Detail(op.id, nextSlotFor(op)?.id)) },
                            onDirections = { openDirections(ctx, op) }, onClose = { selected = null })
                    }
                    Text("© OpenStreetMap contributors · stylized", fontSize = 9.sp, color = Slate.copy(alpha = .7f),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { mapState.recenter(); Store.toast = "Centered on downtown Tempe" }, Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.MyLocation, null, tint = Ink); Spacer(Modifier.width(6.dp)); Text("Recenter", color = Ink)
                    }
                    Button(onClick = { selected?.let { openDirections(ctx, it) } ?: run { Store.toast = "Tap a pin on the map first" } },
                        Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                        Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(6.dp)); Text("Directions")
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = Coral); Spacer(Modifier.width(8.dp))
                    Text("My Schedule", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { nav.tab(Route.Calendar) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Filled.UploadFile, null, Modifier.size(16.dp), tint = Coral); Spacer(Modifier.width(4.dp))
                        Text("Import schedule", color = Ink, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                WeekStrip { calendarFocus = it; nav.tab(Route.Calendar) }
                Spacer(Modifier.height(14.dp))
                NextUpCard(nav)
            }
        }
        item {
            Column(Modifier.padding(start = 16.dp)) {
                SectionTitle(Icons.Filled.AutoAwesome, "Recommended for you", "See all") { nav.tab(Route.Discover) }
            }
            val recs = remember { Demo.opportunities.filter { it.durations.min() <= 30 }.sortedBy { it.distanceMi }.take(8) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recs, key = { it.id }) { op -> RecCard(op) { nav.go(Route.Detail(op.id, nextSlotFor(op)?.id)) } }
            }
        }
        item { Column(Modifier.padding(16.dp)) { ProfileSummary { nav.tab(Route.Profile) } } }
    }
}

@Composable
fun SlideUp(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) { content() }
}

@Composable
fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, desc, tint = Ink) }
}

@Composable
fun MapPreview(op: Opportunity, onOpen: () -> Unit, onClose: () -> Unit) {
    val s = nextSlotFor(op)
    Row(Modifier.padding(10.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
        .background(Color.White).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(op.photos.first()), null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(op.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(op.org.name, style = MaterialTheme.typography.bodySmall, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CostPill(op)
                if (s != null) Pill("${s.minutes} min", CobaltSoft, Cobalt)
            }
            Text((s?.let { "${relDay(it.start.toLocalDate())} ${it.start.t()} · " } ?: "") + String.format(Locale.US, "%.1f mi", op.distanceMi),
                style = MaterialTheme.typography.labelSmall, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            IconButton(onClick = onClose, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Close preview", tint = Slate, modifier = Modifier.size(18.dp)) }
            FilledIconButton(onClick = onOpen, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Coral)) {
                Icon(Icons.Filled.ArrowForward, "View", tint = Color.White)
            }
        }
    }
}

@Composable
fun WeekStrip(onDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            val isToday = d == today
            val hasVol = Store.bookings.keys.any { Demo.slot(it)?.start?.toLocalDate() == d }
            val hasEv = Store.events.any { it.date == d }
            Column(Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(14.dp))
                .background(if (isToday) Cobalt else Color.White).border(1.dp, if (isToday) Cobalt else Line, RoundedCornerShape(14.dp))
                .clickable { onDay(d) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US), style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) Color.White.copy(alpha = .8f) else Slate)
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = if (isToday) Color.White else Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp).padding(top = 2.dp)) {
                    if (hasVol) Box(Modifier.size(6.dp).clip(CircleShape).background(Coral))
                    if (hasEv) Box(Modifier.size(6.dp).clip(CircleShape).background(if (isToday) Color.White else Cobalt.copy(alpha = .5f)))
                }
            }
        }
    }
}

@Composable
fun NextUpCard(nav: Nav) {
    val next = Store.bookings.values.filter { it.state == BookingState.BOOKED }.mapNotNull { Demo.slot(it.slotId) }
        .filter { it.end.isAfter(LocalDateTime.now()) }.minByOrNull { it.start }
    if (next != null) {
        val op = Demo.opp(next.oppId)
        val until = Duration.between(LocalDateTime.now(), next.start)
        val countdown = when {
            until.isNegative -> "Happening now"
            until.toHours() < 24 -> "In ${until.toHours()} h ${until.toMinutes() % 60} min"
            else -> "In ${until.toDays()} days"
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(CobaltDark, Cobalt, CobaltBright)))
            .clickable { nav.go(Route.Detail(op.id, next.id)) }.padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("NEXT UP", Color.White.copy(alpha = .18f), Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(countdown, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(op.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("${relDay(next.start.toLocalDate())} · ${next.start.t()} – ${next.end.t()}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text("${op.place} · ~${op.travelMin} min travel" + if (op.arrivalLead > 0) " · arrive ${op.arrivalLead} min early" else "",
                    color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(op.category.icon, null, tint = Color.White.copy(alpha = .18f), modifier = Modifier.align(Alignment.TopEnd).size(64.dp))
        }
    } else {
        Card(Modifier.fillMaxWidth()) {
            Text("Your next free window", style = MaterialTheme.typography.titleMedium)
            Text("Today 11:45 AM – 1:15 PM · 90 minutes free", color = Slate, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Find shifts that fit", color = Cobalt) { Filters.fitsOnly = true; nav.tab(Route.Discover) }
        }
    }
}

@Composable
fun RecCard(op: Opportunity, onClick: () -> Unit) {
    val s = nextSlotFor(op)
    Column(Modifier.width(220.dp).shadow(5.dp, RoundedCornerShape(20.dp), spotColor = Cobalt.copy(alpha = .15f)).clip(RoundedCornerShape(20.dp))
        .background(Color.White).clickable(onClick = onClick)) {
        Box {
            Image(painterResource(op.photos.first()), op.title, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
            Box(Modifier.padding(8.dp)) { CostPill(op) }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(op.category.color), contentAlignment = Alignment.Center) {
                Icon(op.category.icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(op.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(op.org.name, style = MaterialTheme.typography.bodySmall, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (s != null) InfoLine(Icons.Filled.Schedule, "${relDay(s.start.toLocalDate())} · ${s.minutes} min")
            InfoLine(Icons.Filled.Place, String.format(Locale.US, "%.1f mi", op.distanceMi))
        }
    }
}

@Composable
fun ProfileSummary(onClick: () -> Unit) {
    val (lvl, next, prog) = levelFor(Store.totalMinutes)
    val earned = Store.earnedBadges()
    Card(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { prog }, Modifier.size(64.dp), color = Coral, trackColor = CoralSoft, strokeWidth = 4.dp)
                MyAvatar(52.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Store.myName, style = MaterialTheme.typography.titleMedium)
                Text("Level ${lvl.n} · ${lvl.name}", style = MaterialTheme.typography.bodySmall, color = Cobalt)
                Text("${hoursText(Store.totalMinutes)} confirmed hours" + (next?.let { " · ${it.minHours - Store.totalMinutes / 60} h to ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Slate)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                BADGES.filter { it.id in earned }.take(3).forEach { BadgeArt(it, true, 34.dp) }
            }
        }
    }
}

// ---------------- Stylized Tempe map ----------------
class MapState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var worldW = 1f
    fun recenter() {
        if (size == IntSize.Zero) return
        scale = 1.15f
        val p = project(HOME_LAT, HOME_LNG)
        offset = Offset(size.width / 2f - p.x * scale, size.height / 2f - p.y * scale)
    }
    fun zoomBy(f: Float) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val ns = (scale * f).coerceIn(0.7f, 5f)
        offset = c - (c - offset) * (ns / scale); scale = ns
    }
    /** Glide the map so the pin sits centered horizontally and low enough for the popup to fit above it. */
    suspend fun focusOn(ll: Pair<Double, Double>) {
        if (size == IntSize.Zero) return
        val target = Offset(size.width / 2f, size.height * 0.80f)
        val start = offset
        val end = offset + (target - toScreen(ll.first, ll.second))
        if (reducedMotion) { offset = end; return }
        androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(320)) { v, _ -> offset = start + (end - start) * v }
    }
    fun project(lat: Double, lng: Double): Offset {
        val x = ((lng - LNG0) / (LNG1 - LNG0)).toFloat() * worldW
        val y = ((LAT1 - lat) / (LAT1 - LAT0)).toFloat() * worldW * ASPECT
        return Offset(x, y)
    }
    fun toScreen(lat: Double, lng: Double) = project(lat, lng) * scale + offset

    companion object {
        const val LAT0 = 33.360; const val LAT1 = 33.468; const val LNG0 = -111.980; const val LNG1 = -111.880
        val ASPECT = (((LAT1 - LAT0) * 69.0) / ((LNG1 - LNG0) * 57.8)).toFloat()
    }
}

/** Pin positions; opportunities that share a venue are nudged apart so each pin is tappable. */
val PLACED: List<Pair<Opportunity, Pair<Double, Double>>> by lazy {
    val seen = mutableMapOf<String, Int>()
    Demo.opportunities.map { op ->
        val k = "${op.lat},${op.lng}"; val n = seen.getOrDefault(k, 0); seen[k] = n + 1
        op to (op.lat + n * 0.0011 to op.lng + n * 0.0014)
    }
}
fun pinLatLng(op: Opportunity) = PLACED.first { it.first.id == op.id }.second

/** Google-Maps-style info window anchored above the selected pin; follows the map while panning. */
@Composable
fun PinPopup(op: Opportunity, state: MapState, onOpen: () -> Unit, onDirections: () -> Unit, onClose: () -> Unit) {
    val (lat, lng) = pinLatLng(op)
    val anchor = state.toScreen(lat, lng)
    val density = LocalDensity.current
    val pinH = with(density) { 46.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val appear = remember(op.id) { androidx.compose.animation.core.Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(op.id) { if (!reducedMotion) appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f)) }
    val s = nextSlotFor(op)
    var tailX by remember { mutableFloatStateOf(0.5f) }

    Column(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val boxW = constraints.maxWidth.toFloat()
                val x = (anchor.x - p.width / 2f).coerceIn(margin, (boxW - p.width - margin).coerceAtLeast(margin))
                tailX = ((anchor.x - x) / p.width).coerceIn(0.12f, 0.88f)
                val y = anchor.y - pinH - p.height
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(x.toInt(), y.toInt()) }
            }
            .graphicsLayer {
                scaleX = 0.6f + 0.4f * appear.value; scaleY = scaleX; alpha = appear.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(tailX, 1f)
            }
            .width(256.dp)
            .semantics { contentDescription = "${op.title}, ${op.org.name}. Tap to view details." },
    ) {
        Column(Modifier.shadow(14.dp, RoundedCornerShape(18.dp), spotColor = Ink.copy(alpha = .35f)).clip(RoundedCornerShape(18.dp))
            .background(Color.White).clickable(onClick = onOpen)) {
            Box(Modifier.fillMaxWidth().height(92.dp)) {
                Image(painterResource(op.photos.first()), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))))
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = .92f))
                    .clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Ink, modifier = Modifier.size(16.dp))
                }
                Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(op.category.label, Color.White, op.category.color, op.category.icon); CostPill(op)
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(op.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(op.org.name, style = MaterialTheme.typography.bodySmall, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                if (s != null) {
                    val left = Store.spotsLeft(s)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = Cobalt, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                        Text("${relDay(s.start.toLocalDate())} ${s.start.t()} · ${s.minutes} min", style = MaterialTheme.typography.labelMedium, color = Ink, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Group, null, tint = if (left <= 2) Coral else Mint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (left == 1) "1 spot left" else "$left spots left", style = MaterialTheme.typography.labelMedium, color = if (left <= 2) Coral else Color(0xFF167A4C))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = Slate, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                    Text(String.format(Locale.US, "%s · %.1f mi", op.place, op.distanceMi), style = MaterialTheme.typography.labelMedium, color = Slate,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(Coral).clickable(onClick = onOpen),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("View & sign up", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(CobaltSoft).clickable(onClick = onDirections),
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.Navigation, "Directions", tint = Cobalt, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        // Speech-bubble tail pointing at the pin
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val cx = size.width * tailX; val half = 9.dp.toPx()
            drawPath(Path().apply { moveTo(cx - half, -1f); lineTo(cx + half, -1f); lineTo(cx, size.height); close() }, Color.White)
        }
    }
}

private data class Cluster(val pos: Offset, val ops: List<Opportunity>)

@Composable
fun TempeMap(state: MapState, selected: Opportunity?, onSelect: (Opportunity?) -> Unit, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val clusterPx = with(density) { 34.dp.toPx() }
    val pinW = with(density) { 30.dp.toPx() }
    val icons = Category.entries.associateWith { androidx.compose.ui.graphics.vector.rememberVectorPainter(it.icon) }
    val pulse by animateFloatAsState(if (selected != null) 1f else 0f, tween(if (reducedMotion) 0 else 250), label = "sel")

    val placed = PLACED
    fun clusters(): List<Cluster> {
        val out = mutableListOf<MutableList<Pair<Opportunity, Offset>>>()
        placed.forEach { (op, ll) ->
            val p = state.toScreen(ll.first, ll.second)
            val c = out.firstOrNull { (it.first().second - p).getDistance() < clusterPx }
            if (c != null) c += op to p else out += mutableListOf(op to p)
        }
        return out.map { g -> Cluster(Offset(g.map { it.second.x }.average().toFloat(), g.map { it.second.y }.average().toFloat()), g.map { it.first }) }
    }

    Canvas(modifier
        .clipToBounds()
        .onSizeChanged { s -> val first = state.size == IntSize.Zero; state.size = s; state.worldW = s.width.toFloat(); if (first) state.recenter() }
        .semantics { contentDescription = "Map of Tempe with ${Demo.opportunities.size} volunteering opportunities. Use Discover for a list." }
        .pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val ns = (state.scale * zoom).coerceIn(0.7f, 5f)
                state.offset = centroid - (centroid - state.offset) * (ns / state.scale) + pan
                state.scale = ns
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { state.zoomBy(1.6f) }) { tap ->
                val hit = clusters().minByOrNull { (it.pos - tap).getDistance() }?.takeIf { (it.pos - tap).getDistance() < clusterPx * 1.1f }
                when {
                    hit == null -> onSelect(null)
                    hit.ops.size > 1 -> {
                        val ns = (state.scale * 1.8f).coerceAtMost(5f)
                        state.offset = hit.pos - (hit.pos - state.offset) * (ns / state.scale); state.scale = ns
                        state.offset += Offset(state.size.width / 2f, state.size.height / 2f) - hit.pos
                    }
                    else -> onSelect(hit.ops.first())
                }
            }
        }) {
        drawRect(Color(0xFFF5F0E6))
        fun s(lat: Double, lng: Double) = state.toScreen(lat, lng)
        fun rect(a0: Double, o0: Double, a1: Double, o1: Double, c: Color, r: Float = 18f) {
            val tl = s(a1, o0); val br = s(a0, o1)
            drawRoundRect(c, tl, Size(br.x - tl.x, br.y - tl.y), CornerRadius(r * state.scale))
        }
        // Parks and campus
        rect(33.440, -111.962, 33.466, -111.934, Color(0xFFD5EBC9), 40f)          // Papago Park
        rect(33.369, -111.945, 33.378, -111.932, Color(0xFFD5EBC9))               // Kiwanis Park
        rect(33.411, -111.941, 33.427, -111.918, Color(0xFFF1E2C4), 24f)          // ASU
        rect(33.4255, -111.9395, 33.4315, -111.9335, Color(0xFFE3D2B5), 30f)      // Tempe Butte
        rect(33.405, -111.956, 33.413, -111.946, Color(0xFFDDEFD3))               // Daley / Mitchell parks
        // Salt River bed and Town Lake
        val river = Path().apply {
            val pts = listOf(-111.980 to 33.4335, -111.960 to 33.4325, -111.948 to 33.4330, -111.936 to 33.4340, -111.925 to 33.4320, -111.912 to 33.4305, -111.895 to 33.4290, -111.880 to 33.4285)
            pts.forEachIndexed { i, (lng, lat) -> val p = s(lat, lng); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(river, Color(0xFFE6E0D2), style = Stroke(width = 42f * state.scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lake = Path().apply {
            val pts = listOf(-111.957 to 33.4328, -111.948 to 33.4332, -111.936 to 33.4342, -111.925 to 33.4322, -111.915 to 33.4308)
            pts.forEachIndexed { i, (lng, lat) -> val p = s(lat, lng); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(lake, Color(0xFF8EC3EC), style = Stroke(width = 30f * state.scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(lake, Color(0xFFA9D3F2), style = Stroke(width = 18f * state.scale, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Roads
        val arterialW = (5f * state.scale).coerceIn(3f, 14f)
        fun road(a: Offset, b: Offset, w: Float, c: Color = Color.White) {
            drawLine(Color(0xFFE2DACB), a, b, w + 3f, StrokeCap.Round); drawLine(c, a, b, w, StrokeCap.Round)
        }
        val ns = listOf(-111.9554 to "Priest Dr", -111.9480 to "Hardy Dr", -111.9400 to "Mill Ave", -111.9264 to "Rural Rd", -111.9097 to "McClintock Dr", -111.8920 to "Price Rd")
        val ew = listOf(33.4220 to "University Dr", 33.4075 to "Broadway Rd", 33.3930 to "Southern Ave", 33.3780 to "Baseline Rd", 33.3640 to "Guadalupe Rd", 33.4148 to "Apache Blvd")
        ns.forEach { (lng, _) -> road(s(MapState.LAT0, lng), s(MapState.LAT1, lng), arterialW) }
        ew.forEach { (lat, _) -> road(s(lat, MapState.LNG0), s(lat, MapState.LNG1), arterialW) }
        // Freeways
        val fwW = (9f * state.scale).coerceIn(5f, 22f); val fw = Color(0xFFFFD98A)
        road(s(33.4380, MapState.LNG0), s(33.4395, MapState.LNG1), fwW, fw)    // Loop 202
        road(s(MapState.LAT0, -111.9690), s(MapState.LAT1, -111.9700), fwW, fw) // I-10
        road(s(33.3815, MapState.LNG0), s(33.3815, MapState.LNG1), fwW, fw)    // US-60
        road(s(MapState.LAT0, -111.8935), s(MapState.LAT1, -111.8935), fwW, fw) // Loop 101

        // Labels
        val roadStyle = TextStyle(fontFamily = Jakarta, fontSize = 10.sp, color = Color(0xFF8A8F9E), fontWeight = FontWeight.SemiBold)
        if (state.scale > 1.25f) {
            ew.forEach { (lat, name) -> val p = s(lat, -111.9515); drawText(measurer, name, Offset(p.x + 4, p.y - 16), roadStyle) }
            ns.forEach { (lng, name) -> val p = s(33.3995, lng); drawText(measurer, name, Offset(p.x + 5, p.y), roadStyle) }
        }
        fun label(text: String, lat: Double, lng: Double, style: TextStyle) {
            val l = measurer.measure(text, style); val p = s(lat, lng)
            drawText(l, topLeft = Offset(p.x - l.size.width / 2, p.y - l.size.height / 2))
        }
        label("Tempe Town Lake", 33.4362, -111.9440, TextStyle(fontFamily = Jakarta, fontSize = 11.sp, color = Color(0xFF2F78B8), fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold))
        label("Papago Park", 33.4560, -111.9480, TextStyle(fontFamily = Jakarta, fontSize = 11.sp, color = Color(0xFF3E8A4F), fontWeight = FontWeight.Bold))
        label("Kiwanis Park", 33.3700, -111.9385, TextStyle(fontFamily = Jakarta, fontSize = 10.sp, color = Color(0xFF3E8A4F), fontWeight = FontWeight.Bold))
        if (state.scale > 0.9f) label("Arizona State\nUniversity", 33.4190, -111.9295, TextStyle(fontFamily = Jakarta, fontSize = 10.sp, color = Color(0xFF9A7A45), fontWeight = FontWeight.Bold))
        label("Tempe, AZ", 33.4012, -111.9180, TextStyle(fontFamily = Serif, fontSize = 22.sp, color = Ink.copy(alpha = .85f)))
        fun shield(t: String, lat: Double, lng: Double) {
            val p = s(lat, lng); val l = measurer.measure(t, TextStyle(fontFamily = Jakarta, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Ink))
            val w = l.size.width + 14f; val h = l.size.height + 6f
            drawRoundRect(Color.White, Offset(p.x - w / 2, p.y - h / 2), Size(w, h), CornerRadius(8f))
            drawRoundRect(Ink, Offset(p.x - w / 2, p.y - h / 2), Size(w, h), CornerRadius(8f), style = Stroke(2f))
            drawText(l, topLeft = Offset(p.x - l.size.width / 2, p.y - l.size.height / 2))
        }
        shield("202", 33.4390, -111.9620); shield("10", 33.4000, -111.9695); shield("60", 33.3815, -111.9150); shield("101", 33.4100, -111.8935)

        // "You are here"
        val me = s(HOME_LAT, HOME_LNG)
        drawCircle(Cobalt.copy(alpha = .18f), 26f, me); drawCircle(Color.White, 12f, me); drawCircle(Cobalt, 8f, me)

        // Pins & clusters
        clusters().forEach { c ->
            if (c.ops.size > 1) {
                drawCircle(Cobalt.copy(alpha = .22f), clusterPx * 0.62f, c.pos)
                drawCircle(Cobalt, clusterPx * 0.45f, c.pos)
                drawCircle(Color.White, clusterPx * 0.45f, c.pos, style = Stroke(4f))
                val l = measurer.measure("${c.ops.size}", TextStyle(fontFamily = Jakarta, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White))
                drawText(l, topLeft = Offset(c.pos.x - l.size.width / 2, c.pos.y - l.size.height / 2))
            } else {
                val op = c.ops.first()
                val isSel = selected?.id == op.id
                val w = pinW * (if (isSel) 1f + 0.35f * pulse else 1f); val h = w * 1.05f
                translate(c.pos.x - w / 2, c.pos.y - h) { drawPin(op.category, w, h, icons.getValue(op.category), isSel) }
            }
        }
    }
}

private fun DrawScope.drawPin(cat: Category, w: Float, h: Float, icon: androidx.compose.ui.graphics.vector.VectorPainter, selected: Boolean) {
    val path = heartPinPath(w, h)
    translate(2f, 4f) { drawPath(path, Color.Black.copy(alpha = .18f)) }
    drawPath(path, if (selected) Coral else cat.color)
    drawPath(path, Color.White, style = Stroke(width = 3.5f))
    val isz = w * 0.42f
    translate((w - isz) / 2, h * 0.17f) { with(icon) { draw(Size(isz, isz), colorFilter = ColorFilter.tint(Color.White)) } }
}
