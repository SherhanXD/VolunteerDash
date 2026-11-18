package com.volunteerdash

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

data class CalItem(val id: String, val title: String, val start: LocalDateTime, val end: LocalDateTime, val color: Color,
                   val isVol: Boolean, val oppId: String? = null, val slotId: String? = null, val busy: Boolean = true,
                   val event: PersonalEvent? = null)

fun calItems(): List<CalItem> {
    val ev = Store.events.map { CalItem(it.id, it.title, it.start, it.end, if (it.busy) Cobalt else Slate, false, busy = it.busy, event = it) }
    val vol = Store.bookings.values.mapNotNull { b -> Demo.slot(b.slotId)?.let { s ->
        val op = Demo.opp(s.oppId)
        CalItem(s.id, op.title, s.start, s.end, op.category.color, true, op.id, s.id) } }
    val past = Demo.pastShifts.map { CalItem("p-${it.date}-${it.title}", it.title, it.date.atTime(12, 0), it.date.atTime(12, 0).plusMinutes(it.minutes.toLong()), it.category.color, true) }
    return (ev + vol + past).sortedBy { it.start }
}

fun itemsOn(d: LocalDate, all: List<CalItem>) = all.filter { it.start.toLocalDate() <= d && it.end.toLocalDate() >= d && !(it.end.toLocalDate() == d && it.end.toLocalTime() == LocalTime.MIDNIGHT && it.start.toLocalDate() != d) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(nav: Nav) {
    var view by remember { mutableStateOf("Month") }
    var selected by remember { mutableStateOf(calendarFocus ?: LocalDate.now()) }
    LaunchedEffect(Unit) { calendarFocus = null }
    var editing by remember { mutableStateOf<PersonalEvent?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importText = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.getOrNull() ?: ""
    }
    val all = calItems()
    fun open(it: CalItem) { when { it.event != null -> editing = it.event; it.oppId != null -> nav.go(Route.Detail(it.oppId, it.slotId)); else -> Store.toast = "Completed shift · hours confirmed" } }

    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton({ editing = PersonalEvent("", "", selected, 12 * 60, 60) }, containerColor = Coral, contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)) { Icon(Icons.Filled.Add, "Create event") }
        }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton({ selected = shift(selected, view, -1) }) { Icon(Icons.Filled.ChevronLeft, "Previous") }
                    IconButton({ selected = shift(selected, view, 1) }) { Icon(Icons.Filled.ChevronRight, "Next") }
                    Button({ selected = LocalDate.now() }, colors = ButtonDefaults.buttonColors(containerColor = CobaltSoft, contentColor = Cobalt),
                        contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Today") }
                    IconButton({ showImport = true }) { Icon(Icons.Filled.UploadFile, "Import calendar", tint = Coral) }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val opts = listOf("Month", "Week", "Day", "Agenda")
                    opts.forEachIndexed { i, o ->
                        SegmentedButton(view == o, { view = o }, SegmentedButtonDefaults.itemShape(i, opts.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Cobalt, activeContentColor = Color.White),
                            icon = {}) { Text(o, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            when (view) {
                "Month" -> MonthView(selected, all, onSelect = { selected = it }, onOpen = ::open)
                "Week" -> Timeline((0..6).map { selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusDays(it.toLong()) }, all, ::open) { d, h -> editing = PersonalEvent("", "", d, h * 60, 60) }
                "Day" -> Timeline(listOf(selected), all, ::open) { d, h -> editing = PersonalEvent("", "", d, h * 60, 60) }
                else -> AgendaView(all, ::open) { Filters.fitsOnly = true; nav.tab(Route.Discover) }
            }
        }
    }

    editing?.let { e -> EventEditor(e, onDismiss = { editing = null }) }

    if (showImport) AlertDialog(onDismissRequest = { showImport = false }, containerColor = Color.White,
        icon = { Icon(Icons.Filled.UploadFile, null, tint = Coral) },
        title = { Text("Import a calendar") },
        text = { Text("Choose an .ics file exported from Google Calendar, Outlook or Samsung Calendar. Events stay private on this phone and are only used to find shifts that fit.") },
        confirmButton = { Button({ showImport = false; picker.launch(arrayOf("text/calendar", "text/plain", "application/octet-stream", "*/*")) }) { Text("Choose file") } },
        dismissButton = { TextButton({ showImport = false; importText = SAMPLE_ICS }) { Text("Use sample work schedule") } })

    importText?.let { txt ->
        val parsed = remember(txt) { parseIcs(txt) }
        val dupes = parsed.count { p -> Store.events.any { it.title == p.title && it.date == p.date && it.startMin == p.startMin } }
        AlertDialog(onDismissRequest = { importText = null }, containerColor = Color.White,
            title = { Text("Import preview") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (parsed.isEmpty()) Text("No events found in this file. Make sure it's a standard .ics calendar file.", color = Coral)
                    else {
                        Text("${parsed.size} events found · $dupes already in your calendar (will be skipped)", style = MaterialTheme.typography.bodyMedium, color = Slate)
                        Spacer(Modifier.height(8.dp))
                        parsed.take(30).forEach { p ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(Cobalt)); Spacer(Modifier.width(8.dp))
                                Column { Text(p.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${p.date.d()} · ${p.start.t()} – ${p.end.t()}" + if (!p.busy) " · free" else "", style = MaterialTheme.typography.bodySmall, color = Slate) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button({
                    val new = parsed.filter { p -> Store.events.none { it.title == p.title && it.date == p.date && it.startMin == p.startMin } }
                    Store.events.addAll(new); Store.save(); importText = null
                    Store.toast = "Imported ${new.size} events · skipped $dupes duplicates"
                }, enabled = parsed.isNotEmpty()) { Text("Import") }
            },
            dismissButton = { TextButton({ importText = null }) { Text("Cancel") } })
    }
}

fun shift(d: LocalDate, view: String, dir: Int): LocalDate = when (view) {
    "Month" -> d.plusMonths(dir.toLong()); "Week" -> d.plusWeeks(dir.toLong()); else -> d.plusDays(dir.toLong())
}

@Composable
fun MonthView(selected: LocalDate, all: List<CalItem>, onSelect: (LocalDate) -> Unit, onOpen: (CalItem) -> Unit) {
    val first = selected.withDayOfMonth(1)
    val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
        item {
            Column(Modifier.background(Color.White).padding(horizontal = 6.dp)) {
                Row { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f).padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = Slate) } }
                for (w in 0 until 6) Row {
                    for (i in 0..6) {
                        val d = gridStart.plusDays((w * 7 + i).toLong())
                        val inMonth = d.month == selected.month
                        val list = itemsOn(d, all)
                        val isSel = d == selected
                        Column(Modifier.weight(1f).height(78.dp).padding(1.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) CobaltSoft else Color.Transparent)
                            .border(if (isSel) 1.5.dp else 0.dp, if (isSel) Cobalt else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onSelect(d) }.semantics { contentDescription = "${d.d()}, ${list.size} events" }.padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(if (d == today) Cobalt else Color.Transparent), contentAlignment = Alignment.Center) {
                                Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                                    color = when { d == today -> Color.White; inMonth -> Ink; else -> Slate.copy(alpha = .45f) })
                            }
                            list.take(2).forEach { e ->
                                Text(e.title, Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(4.dp)).background(e.color.copy(alpha = if (inMonth) .16f else .07f)).padding(horizontal = 2.dp),
                                    fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Clip, color = e.color, fontWeight = FontWeight.Bold)
                            }
                            if (list.size > 2) Text("+${list.size - 2}", fontSize = 9.sp, color = Slate, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { Text(selected.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
        val day = itemsOn(selected, all)
        if (day.isEmpty()) item { EmptyState(Icons.Filled.EventAvailable, "Nothing scheduled", "A great day to help out. Tap + to add your own event.") }
        items(day, key = { it.id }) { AgendaRow(it, onOpen) }
    }
}

@Composable
fun AgendaRow(e: CalItem, onOpen: (CalItem) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
        .border(1.dp, Line, RoundedCornerShape(14.dp)).clickable { onOpen(e) }.height(IntrinsicSize.Min)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(e.color))
        Column(Modifier.padding(12.dp).weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall)
            Text("${e.start.t()} – ${e.end.t()}" + if (!e.busy) " · Free" else "", style = MaterialTheme.typography.bodySmall, color = Slate)
        }
        if (e.isVol) Box(Modifier.align(Alignment.CenterVertically).padding(end = 12.dp)) {
            Pill(if (e.oppId == null) "Confirmed" else "Volunteering", e.color.copy(alpha = .12f), e.color, Icons.Filled.VolunteerActivism)
        }
    }
}

@Composable
fun AgendaView(all: List<CalItem>, onOpen: (CalItem) -> Unit, onFind: () -> Unit) {
    val today = LocalDate.now()
    val days = (0..27).map { today.plusDays(it.toLong()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
        days.forEach { d ->
            val list = itemsOn(d, all)
            if (list.isNotEmpty()) {
                item(key = "h$d") { Text(relDay(d).uppercase(), style = MaterialTheme.typography.labelLarge, color = if (d == today) Cobalt else Slate,
                    modifier = Modifier.fillMaxWidth().background(Bg).padding(start = 16.dp, top = 14.dp, bottom = 4.dp)) }
                items(list, key = { "$d-${it.id}" }) { AgendaRow(it, onOpen) }
            }
        }
        item { EmptyState(Icons.Filled.VolunteerActivism, "Got a free window?", "Find short shifts that fit around everything above.", "Find shifts that fit", onFind) }
    }
}

@Composable
fun Timeline(days: List<LocalDate>, all: List<CalItem>, onOpen: (CalItem) -> Unit, onCreate: (LocalDate, Int) -> Unit) {
    val hourH: Dp = 56.dp
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo((7 * 56 * 2.75f).toInt()) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.padding(start = 48.dp)) {
            days.forEach { d ->
                Column(Modifier.weight(1f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US), style = MaterialTheme.typography.labelSmall, color = Slate)
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (d == today) Cobalt else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleSmall, color = if (d == today) Color.White else Ink)
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.verticalScroll(scroll).padding(bottom = 90.dp)) {
            Column(Modifier.width(48.dp)) {
                for (h in 0..23) Box(Modifier.height(hourH)) {
                    Text(if (h == 0) "" else LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern("h a", Locale.US)), fontSize = 10.sp, color = Slate,
                        modifier = Modifier.padding(start = 6.dp).offset(y = (-7).dp))
                }
            }
            days.forEach { d ->
                BoxWithConstraints(Modifier.weight(1f).height(hourH * 24).border(0.5.dp, Line)) {
                    Column { for (h in 0..23) Box(Modifier.fillMaxWidth().height(hourH).border(0.25.dp, Line.copy(alpha = .7f)).clickable { onCreate(d, h) }) }
                    // Clip each item to this day (handles cross-midnight events), then pack overlaps into columns.
                    val segs = itemsOn(d, all).map { e ->
                        val s = if (e.start.toLocalDate() < d) d.atStartOfDay() else e.start
                        val en = if (e.end.toLocalDate() > d) d.plusDays(1).atStartOfDay() else e.end
                        Triple(e, s, en)
                    }
                    val cols = mutableListOf<MutableList<Triple<CalItem, LocalDateTime, LocalDateTime>>>()
                    segs.forEach { seg -> (cols.firstOrNull { c -> c.last().third <= seg.second } ?: mutableListOf<Triple<CalItem, LocalDateTime, LocalDateTime>>().also { cols += it }).add(seg) }
                    val colW = maxWidth / maxOf(1, cols.size)
                    cols.forEachIndexed { ci, col ->
                        col.forEach { (e, s, en) ->
                            val top = hourH * (s.hour + s.minute / 60f)
                            val mins = Duration.between(s, en).toMinutes().coerceAtLeast(20)
                            Column(Modifier.offset(x = colW * ci, y = top).width(colW).height(hourH * (mins / 60f)).padding(1.5.dp)
                                .clip(RoundedCornerShape(8.dp)).background(e.color.copy(alpha = .16f)).clickable { onOpen(e) }
                                .semantics { contentDescription = "${e.title}, ${e.start.t()} to ${e.end.t()}" }
                                .border(width = 0.dp, color = Color.Transparent).padding(start = 4.dp, top = 2.dp, end = 2.dp)) {
                                Text(e.title, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = e.color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (mins >= 40) Text(s.t(), fontSize = 9.sp, color = e.color.copy(alpha = .8f), maxLines = 1)
                            }
                        }
                    }
                    if (d == today) {
                        val n = LocalTime.now()
                        Box(Modifier.offset(y = hourH * (n.hour + n.minute / 60f)).fillMaxWidth().height(2.dp).background(Coral))
                    }
                }
            }
        }
    }
}

@Composable
fun EventEditor(initial: PersonalEvent, onDismiss: () -> Unit) {
    val isNew = initial.id.isEmpty()
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(initial.date) }
    var startMin by remember { mutableIntStateOf(initial.startMin) }
    var minutes by remember { mutableIntStateOf(initial.minutes) }
    var busy by remember { mutableStateOf(initial.busy) }
    var notes by remember { mutableStateOf(initial.notes) }
    var tried by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text(if (isNew) "New event" else "Edit event", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, isError = tried && title.isBlank(),
                    supportingText = { if (tried && title.isBlank()) Text("Add a title") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(date.d(), maxLines = 1)
                    }
                    OutlinedButton({ TimePickerDialog(ctx, { _, h, m -> startMin = h * 60 + m }, startMin / 60, startMin % 60, false).show() }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(LocalTime.of(startMin / 60, startMin % 60).format(timeFmt), maxLines = 1)
                    }
                }
                Text("Length", style = MaterialTheme.typography.labelMedium, color = Slate, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60, 120, 480).forEach { m -> FilterChip(minutes == m, { minutes = m }, label = { Text(if (m < 60) "${m}m" else "${m / 60}h") }) }
                }
                Text("Ends ${LocalTime.of(startMin / 60, startMin % 60).plusMinutes(minutes.toLong()).format(timeFmt)}" +
                    if (startMin + minutes > 24 * 60) " next day" else "", style = MaterialTheme.typography.bodySmall, color = Slate)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(busy, { busy = true }, label = { Text("Busy") }); FilterChip(!busy, { busy = false }, label = { Text("Free") })
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button({
                tried = true
                if (title.isNotBlank()) {
                    val e = PersonalEvent(if (isNew) UUID.randomUUID().toString() else initial.id, title.trim(), date, startMin, minutes, busy, notes)
                    Store.events.removeAll { it.id == e.id }; Store.events.add(e); Store.save(); onDismiss()
                    Store.toast = if (isNew) "Event added" else "Event updated"
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton({ Store.events.removeAll { it.id == initial.id }; Store.save(); onDismiss(); Store.toast = "Event deleted" }) { Text("Delete", color = Coral) }
                TextButton(onDismiss) { Text("Cancel") }
            }
        })
}

/** Minimal ICS reader: VEVENT with SUMMARY, DTSTART/DTEND (UTC, TZID or floating, all-day), TRANSP. */
fun parseIcs(text: String): List<PersonalEvent> {
    val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
    val out = mutableListOf<PersonalEvent>()
    var cur: MutableMap<String, String>? = null
    val zone = ZoneId.systemDefault()
    fun parseDt(key: String, value: String): LocalDateTime? = runCatching {
        val tz = Regex("TZID=([^;:]+)").find(key)?.groupValues?.get(1)
        when {
            value.length == 8 -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime()
            tz != null -> LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(runCatching { ZoneId.of(tz) }.getOrDefault(zone)).withZoneSameInstant(zone).toLocalDateTime()
            else -> LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        }
    }.getOrNull()
    for (l in lines) {
        when {
            l.startsWith("BEGIN:VEVENT") -> cur = mutableMapOf()
            l.startsWith("END:VEVENT") -> cur?.let { m ->
                val sk = m.keys.firstOrNull { it.startsWith("DTSTART") }; val ek = m.keys.firstOrNull { it.startsWith("DTEND") }
                val s = sk?.let { parseDt(it, m[it]!!) }
                val e = ek?.let { parseDt(it, m[it]!!) } ?: s?.plusHours(1)
                if (s != null && e != null && m["STATUS"] != "CANCELLED") {
                    out += PersonalEvent(UUID.randomUUID().toString(), (m["SUMMARY"] ?: "Busy").replace("\\,", ",").replace("\\n", " "),
                        s.toLocalDate(), s.hour * 60 + s.minute, Duration.between(s, e).toMinutes().toInt().coerceAtLeast(5),
                        m["TRANSP"] != "TRANSPARENT", "", "ics")
                }
                cur = null
            }
            cur != null && l.contains(":") -> { val i = l.indexOf(':'); cur!![l.substring(0, i)] = l.substring(i + 1).trim() }
        }
    }
    return out
}

val SAMPLE_ICS: String get() {
    val f = DateTimeFormatter.ofPattern("yyyyMMdd")
    val mon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    return buildString {
        append("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Sample//Work//EN\n")
        for (i in 0..4) {
            val d = mon.plusDays(i.toLong()).format(f)
            append("BEGIN:VEVENT\nUID:w$i@sample\nSUMMARY:Client meetings\nDTSTART;TZID=America/Phoenix:${d}T090000\nDTEND;TZID=America/Phoenix:${d}T113000\nEND:VEVENT\n")
            append("BEGIN:VEVENT\nUID:s$i@sample\nSUMMARY:Team standup\nDTSTART;TZID=America/Phoenix:${d}T133000\nDTEND;TZID=America/Phoenix:${d}T140000\nEND:VEVENT\n")
        }
        append("END:VCALENDAR\n")
    }
}
