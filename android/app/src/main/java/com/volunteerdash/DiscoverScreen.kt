package com.volunteerdash

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

fun matchingSlot(op: Opportunity): Slot? {
    val now = LocalDateTime.now()
    return Demo.slots.firstOrNull { s ->
        s.oppId == op.id && s.start.isAfter(now) && !s.cancelled &&
            (!Filters.openOnly || Store.spotsLeft(s) > 0) &&
            (Filters.maxDuration == null || s.minutes <= Filters.maxDuration!!) &&
            (Filters.day == null || s.start.toLocalDate() == Filters.day) &&
            (Filters.timeOfDay == null || when (Filters.timeOfDay) {
                "Morning" -> s.start.hour < 11; "Midday" -> s.start.hour in 11..13
                "Afternoon" -> s.start.hour in 14..16; else -> s.start.hour >= 17 }) &&
            (!Filters.fitsOnly || Store.conflictFor(s) == null)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(nav: Nav) {
    val q = Filters.query.trim().lowercase()
    val results = Demo.opportunities.filter { op ->
        (q.isEmpty() || listOf(op.title, op.org.name, op.category.label, op.summary, op.place).any { it.lowercase().contains(q) }) &&
            (!Filters.freeOnly || op.isFree) && op.distanceMi <= Filters.radius &&
            (Filters.categories.isEmpty() || op.category in Filters.categories)
    }.mapNotNull { op -> matchingSlot(op)?.let { op to it } }
        .sortedWith(compareBy({ it.second.start.toLocalDate() }, { it.first.distanceMi }))

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Schedule Matching", style = MaterialTheme.typography.headlineMedium, color = Coral)
            Text("Find volunteer shifts that fit your time.", style = MaterialTheme.typography.bodyMedium, color = Slate)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(Filters.query, { Filters.query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search pantry, dogs, trail…") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cobalt) },
                trailingIcon = { if (Filters.query.isNotEmpty()) IconButton(onClick = { Filters.query = "" }) { Icon(Icons.Filled.Close, "Clear search") } },
                shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
            Text("Volunteer duration", style = MaterialTheme.typography.labelMedium, color = Slate)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30, 60, 90).forEach { m ->
                    val sel = Filters.maxDuration == m
                    FilterChip(sel, { Filters.maxDuration = if (sel) null else m }, label = { Text(if (m < 60) "$m min" else "${m / 60f}".removeSuffix(".0") + " hr") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Color.White))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip({ Filters.showSheet = true }, label = { Text("Filters" + if (Filters.activeCount > 0) " · ${Filters.activeCount}" else "") },
                    leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) })
                FilterChip(Filters.fitsOnly, { Filters.fitsOnly = !Filters.fitsOnly }, label = { Text("Fits my calendar") },
                    leadingIcon = { Icon(Icons.Filled.EventAvailable, null, Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MintSoft, selectedLabelColor = Color(0xFF167A4C)))
                FilterChip(Filters.freeOnly, { Filters.freeOnly = !Filters.freeOnly }, label = { Text("Free only") })
                Filters.day?.let { InputChip(true, { Filters.day = null }, label = { Text(relDay(it)) }, trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) }) }
                Filters.timeOfDay?.let { InputChip(true, { Filters.timeOfDay = null }, label = { Text(it) }, trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) }) }
                Filters.categories.toList().forEach { c -> InputChip(true, { Filters.categories.remove(c) }, label = { Text(c.label) }, trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) }) }
                if (Filters.activeCount > 0) TextButton({ Filters.reset() }) { Text("Reset", color = Coral) }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${results.size} Matching ${if (results.size == 1) "Opportunity" else "Opportunities"}",
                    style = MaterialTheme.typography.titleLarge, color = Coral)
            }
            if (results.isEmpty()) item {
                EmptyState(Icons.Filled.SearchOff, "No shifts match", "Try a longer duration, a wider radius, or turn off calendar matching.", "Reset filters") { Filters.reset() }
            }
            items(results, key = { it.first.id }) { (op, s) -> OpportunityCard(op, s) { nav.go(Route.Detail(op.id, s.id)) } }
        }
    }

    if (Filters.showSheet) {
        ModalBottomSheet(onDismissRequest = { Filters.showSheet = false }, containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text("Distance", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10).forEach { r -> FilterChip(Filters.radius == r, { Filters.radius = r }, label = { Text("Within $r mi") }) }
                }
                Text("Date", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                val today = LocalDate.now()
                val sat = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Any" to null, "Today" to today, "Tomorrow" to today.plusDays(1), "Saturday" to sat, "Sunday" to sat.plusDays(1)).forEach { (l, d) ->
                        FilterChip(Filters.day == d, { Filters.day = d }, label = { Text(l) })
                    }
                }
                Text("Start time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, "Morning", "Midday", "Afternoon", "Evening").forEach { t -> FilterChip(Filters.timeOfDay == t, { Filters.timeOfDay = t }, label = { Text(t ?: "Any time") }) }
                }
                Text("Cause", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        val sel = c in Filters.categories
                        FilterChip(sel, { if (sel) Filters.categories.remove(c) else Filters.categories.add(c) }, label = { Text(c.label) },
                            leadingIcon = { Icon(c.icon, null, Modifier.size(16.dp), tint = c.color) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Only shifts with open spots", Modifier.weight(1f)); Switch(Filters.openOnly, { Filters.openOnly = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Free only", Modifier.weight(1f)); Switch(Filters.freeOnly, { Filters.freeOnly = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Fits my calendar"); Text("Includes travel time each way", style = MaterialTheme.typography.bodySmall, color = Slate) }
                    Switch(Filters.fitsOnly, { Filters.fitsOnly = it })
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ Filters.reset() }, Modifier.weight(1f).height(50.dp)) { Text("Reset") }
                    PrimaryButton("Show results", Modifier.weight(1f), color = Cobalt) { Filters.showSheet = false }
                }
            }
        }
    }
}

// ---------------- Detail ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: Nav, oppId: String, initialSlot: String?) {
    val op = Demo.opp(oppId)
    val ctx = LocalContext.current
    val now = LocalDateTime.now()
    val slots = Demo.slots.filter { it.oppId == op.id && it.start.isAfter(now) }.take(24)
    var selId by remember { mutableStateOf(initialSlot ?: slots.firstOrNull { !it.cancelled && Store.spotsLeft(it) > 0 }?.id) }
    val sel = selId?.let { Demo.slot(it) }
    var confirm by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var cancelAsk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var contact by remember { mutableStateOf(false) }
    val booked = sel != null && Store.bookings.containsKey(sel.id)

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(290.dp)) {
                    val pager = rememberPagerState { op.photos.size }
                    HorizontalPager(pager) { i -> Image(painterResource(op.photos[i]), op.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x990F1A3D)))))
                    Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth()) {
                        FilledIconButton({ nav.back() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                        }
                        Spacer(Modifier.weight(1f))
                        FilledIconButton({ shareOpportunity(ctx, op) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) {
                            Icon(Icons.Filled.Share, "Share", tint = Ink)
                        }
                    }
                    if (op.photos.size > 1) Row(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        repeat(op.photos.size) { i -> Box(Modifier.padding(2.dp).size(8.dp).clip(CircleShape).background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = .4f))) }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(op.category.label, Color.White, op.category.color, op.category.icon); CostPill(op) }
                        Spacer(Modifier.height(6.dp))
                        Text(op.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.clip(RoundedCornerShape(14.dp)).clickable { contact = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(op.category.color.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            Icon(op.category.icon, null, tint = op.category.color)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(op.org.name, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.width(6.dp))
                                Pill("Demo", Color(0xFFF0F3FA), Slate)
                            }
                            Text("Contact ${op.org.contact}", style = MaterialTheme.typography.bodySmall, color = Cobalt)
                        }
                        IconButton({ nav.go(Route.Chat(op.id)) }) { Icon(Icons.Filled.ChatBubbleOutline, "Message organizer", tint = Cobalt) }
                    }
                    Text(op.description, style = MaterialTheme.typography.bodyLarge, color = Ink)

                    Card(Modifier.fillMaxWidth()) {
                        Text("Your full commitment", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (sel != null) InfoLine(Icons.Filled.Timer, "${durText(sel.minutes)} volunteering", Ink)
                        if (op.arrivalLead > 0) InfoLine(Icons.Filled.DirectionsWalk, "Arrive ${op.arrivalLead} min early for check-in", Ink)
                        InfoLine(Icons.Filled.DirectionsCar, String.format(Locale.US, "Travel ~%d min each way (%.1f mi), not counted as volunteering", op.travelMin, op.distanceMi), Ink, maxLines = 2)
                        if (sel != null) InfoLine(Icons.Filled.HourglassBottom,
                            "Total time away: about ${durText(sel.minutes + op.arrivalLead + op.travelMin * 2)}", Cobalt)
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Line)
                        LabeledText(Icons.Filled.Backpack, "Preparation", op.preparation)
                        LabeledText(Icons.Filled.Accessible, "Accessibility", op.accessibility)
                        LabeledText(Icons.Filled.Place, op.place, op.address)
                        LabeledText(Icons.Filled.Payments, "Cost", op.costNote)
                    }

                    Text("Choose a time", style = MaterialTheme.typography.titleLarge)
                    if (slots.isEmpty()) EmptyState(Icons.Filled.EventBusy, "No upcoming shifts", "The organizer hasn't published new slots yet.")
                    slots.groupBy { it.start.toLocalDate() }.entries.take(6).forEach { (day, list) ->
                        Text(relDay(day), style = MaterialTheme.typography.labelLarge, color = Slate)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { s -> SlotChip(s, s.id == selId) { selId = s.id } }
                        }
                    }

                    if (sel != null) {
                        val conflict = Store.conflictFor(sel)
                        val fitText = if (conflict == null) "Fits — you're free around ${sel.start.minusMinutes((op.travelMin + op.arrivalLead).toLong()).t()} – ${sel.end.plusMinutes(op.travelMin.toLong()).t()} (includes ${op.travelMin} min travel each way)"
                        else "Conflicts with “${conflict.title}” ${conflict.start.t()} – ${conflict.end.t()} once travel and arrival time are included"
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (conflict == null) MintSoft else CoralSoft).padding(12.dp)) {
                            Icon(if (conflict == null) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (conflict == null) Mint else Coral)
                            Spacer(Modifier.width(8.dp))
                            Text(fitText, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        }
                        AttendeesSection(sel, nav)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Bg.copy(alpha = .92f)))
        // Sticky CTA
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
            when {
                sel == null -> PrimaryButton("Pick a time above", Modifier.fillMaxWidth(), enabled = false) {}
                booked -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ cancelAsk = true }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Cancel booking", color = Coral) }
                    PrimaryButton("Directions", Modifier.weight(1f), color = Ink, icon = Icons.Filled.Navigation) { openDirections(ctx, op) }
                }
                sel.cancelled -> PrimaryButton("Shift cancelled by organizer", Modifier.fillMaxWidth(), enabled = false) {}
                Store.spotsLeft(sel) <= 0 -> PrimaryButton("This shift is full", Modifier.fillMaxWidth(), enabled = false) {}
                else -> PrimaryButton("Sign up · ${relDay(sel.start.toLocalDate())} ${sel.start.t()} · ${sel.minutes} min", Modifier.fillMaxWidth()) { confirm = true }
            }
        }
    }

    if (confirm && sel != null) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color.White,
        title = { Text("Confirm your shift", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(op.title, style = MaterialTheme.typography.titleMedium)
                Text("${relDay(sel.start.toLocalDate())}, ${sel.start.t()} – ${sel.end.t()}", color = Slate)
                Spacer(Modifier.height(8.dp))
                if (op.arrivalLead > 0) Text("• Arrive by ${sel.start.minusMinutes(op.arrivalLead.toLong()).t()}")
                Text("• ${op.preparation}")
                Text("• ~${op.travelMin} min travel each way")
                Store.conflictFor(sel)?.let { Text("• Heads up: overlaps “${it.title}”", color = Coral) }
            }
        },
        confirmButton = {
            Button({
                confirm = false
                val err = Store.book(sel)
                if (err == null) success = true else error = err
            }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("Book shift") }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Not now") } })

    if (cancelAsk && sel != null) AlertDialog(onDismissRequest = { cancelAsk = false }, containerColor = Color.White,
        title = { Text("Cancel this shift?") },
        text = { Text("Your spot will open for another volunteer and the shift is removed from your calendar.") },
        confirmButton = { Button({ Store.cancel(sel.id); cancelAsk = false; Store.toast = "Booking cancelled" }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("Cancel booking") } },
        dismissButton = { TextButton({ cancelAsk = false }) { Text("Keep it") } })

    error?.let { e -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Couldn't book") }, text = { Text(e) },
        confirmButton = { TextButton({ error = null }) { Text("OK") } }) }

    if (contact) AlertDialog(onDismissRequest = { contact = false }, containerColor = Color.White,
        title = { Text(op.org.name) },
        text = { Column { InfoLine(Icons.Filled.Person, op.org.contact, Ink); InfoLine(Icons.Filled.Email, op.org.email, Ink); InfoLine(Icons.Filled.Phone, op.org.phone, Ink)
            Text("Fictional demo contact details.", style = MaterialTheme.typography.bodySmall, color = Slate) } },
        confirmButton = { Button({ contact = false; nav.go(Route.Chat(op.id)) }) { Text("Message organizer") } },
        dismissButton = { TextButton({ contact = false }) { Text("Close") } })

    if (success && sel != null) androidx.compose.ui.window.Dialog(onDismissRequest = { success = false }) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SuccessBurst()
            Text("You're in!", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("${op.title}\n${relDay(sel.start.toLocalDate())} · ${sel.start.t()}", style = MaterialTheme.typography.bodyMedium, color = Slate,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Pill("Added to your calendar", MintSoft, Color(0xFF167A4C), Icons.Filled.EventAvailable)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("View in calendar", Modifier.fillMaxWidth(), color = Cobalt) { success = false; calendarFocus = sel.start.toLocalDate(); nav.tab(Route.Calendar) }
            Row(Modifier.padding(top = 8.dp)) {
                TextButton({ success = false; nav.go(Route.Chat(op.id)) }) { Text("Message organizer") }
                TextButton({ shareOpportunity(ctx, op) }) { Text("Invite a friend") }
            }
        }
    }
}

fun shareOpportunity(ctx: android.content.Context, op: Opportunity) {
    val text = "Join me volunteering: ${op.title} with ${op.org.name} in Tempe. https://volunteerdash.app/o/${op.id}?ref=JORDAN7K"
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share opportunity"))
}

@Composable
fun LabeledText(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, body: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, tint = Cobalt, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Column { Text(label, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodyMedium, color = Slate) }
    }
}

@Composable
fun SlotChip(s: Slot, selected: Boolean, onClick: () -> Unit) {
    val left = Store.spotsLeft(s)
    val mine = Store.bookings.containsKey(s.id)
    val disabled = s.cancelled || (left <= 0 && !mine)
    val bg = when { selected -> Cobalt; mine -> MintSoft; disabled -> Color(0xFFF0F2F6); else -> Color.White }
    val fg = when { selected -> Color.White; disabled -> Slate.copy(alpha = .6f); else -> Ink }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, if (selected) Cobalt else Line, RoundedCornerShape(14.dp))
        .clickable(enabled = !s.cancelled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.start.t(), style = MaterialTheme.typography.titleSmall.copy(
            textDecoration = if (s.cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null), color = fg)
        Text(when { s.cancelled -> "Cancelled"; mine -> "Booked ✓"; left <= 0 -> "Full"; else -> "${s.minutes} min · $left left" },
            style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = .85f) else if (left <= 0) Coral else Slate)
    }
}

@Composable
fun AttendeesSection(s: Slot, nav: Nav) {
    val people = s.attendees.mapNotNull { Demo.person(it) }
    val familiar = people.filter { it.shared > 0 && !it.isPrivate }
    val others = people.filter { it.shared == 0 && !it.isPrivate }
    val hidden = people.count { it.isPrivate } + (s.baseBooked - people.size).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth()) {
        Text("Who's going", style = MaterialTheme.typography.titleMedium)
        if (people.isEmpty() && s.baseBooked == 0) {
            Text("Be the first to sign up for this time.", color = Slate, style = MaterialTheme.typography.bodyMedium)
        }
        if (familiar.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Pill("Familiar faces", MintSoft, Color(0xFF167A4C), Icons.Filled.Favorite)
            familiar.forEach { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.go(Route.PersonR(p.id)) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 44.dp, Mint); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text("You've volunteered together ${p.shared} ${if (p.shared == 1) "time" else "times"}", style = MaterialTheme.typography.bodySmall, color = Slate)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Slate)
                }
            }
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Also attending", style = MaterialTheme.typography.labelLarge, color = Slate)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.take(5).forEach { p -> Box(Modifier.clickable { nav.go(Route.PersonR(p.id)) }) { Avatar(p, 40.dp) } }
                if (hidden > 0) Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF0F3FA)), contentAlignment = Alignment.Center) {
                    Text("+$hidden", style = MaterialTheme.typography.labelMedium, color = Slate)
                }
            }
        }
        Text("${s.baseBooked + if (Store.bookings.containsKey(s.id)) 1 else 0} of ${s.capacity} spots filled", style = MaterialTheme.typography.bodySmall, color = Slate,
            modifier = Modifier.padding(top = 8.dp))
    }
}
