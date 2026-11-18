package com.volunteerdash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ---------------- Community ----------------
data class Rank(val person: Person, val minutes: Int, val isMe: Boolean)

fun leaderboard(): List<Rank> {
    val meEntry = Rank(Demo.me.copy(name = Store.myName), Store.weekMinutes, true)
    return (Demo.people.filter { it.weekMinutes > 0 }.map { Rank(it, it.weekMinutes, false) } + meEntry)
        .sortedWith(compareByDescending<Rank> { it.minutes }.thenBy { it.person.name })
}

@Composable
fun CommunityScreen(nav: Nav) {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Text("Community", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Tempe volunteers making it happen this week", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .16f)).padding(4.dp)) {
                listOf("Weekly Top 10", "Teammates", "Invite").forEachIndexed { i, t ->
                    Text(t, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == i) Color.White else Color.Transparent)
                        .clickable { tab = i }.padding(vertical = 10.dp), textAlign = TextAlign.Center,
                        color = if (tab == i) Cobalt else Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        when (tab) {
            0 -> {
                val all = leaderboard()
                val top = all.take(10)
                val myRank = all.indexOfFirst { it.isMe } + 1
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("${monday.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} · Arizona time · organizer-confirmed hours",
                            style = MaterialTheme.typography.bodySmall, color = Slate)
                    }
                    item { Podium(top.take(3)) { nav.go(Route.PersonR(it)) } }
                    items(top.drop(3).withIndex().toList()) { (i, r) -> RankRow(i + 4, r) { if (!r.isMe) nav.go(Route.PersonR(r.person.id)) } }
                    if (myRank > 10) item {
                        Text("Your progress", style = MaterialTheme.typography.labelLarge, color = Slate, modifier = Modifier.padding(top = 8.dp))
                        RankRow(myRank, all[myRank - 1]) {}
                        Text("${hoursText((top.last().minutes - Store.weekMinutes).coerceAtLeast(0) + 15)} more hours this week to reach the Top 10.",
                            style = MaterialTheme.typography.bodySmall, color = Slate)
                    }
                    item { Text("Ties are broken by name. A new week starts Monday 12:00 AM.", style = MaterialTheme.typography.bodySmall, color = Slate.copy(alpha = .7f)) }
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("People you've volunteered with", style = MaterialTheme.typography.titleMedium) }
                items(Demo.people.filter { it.shared > 0 }.sortedByDescending { it.shared }) { p ->
                    Card(Modifier.fillMaxWidth(), onClick = { nav.go(Route.PersonR(p.id)) }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p, 52.dp, Mint); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text("${p.shared} shared shifts · ${p.hours} total hours", style = MaterialTheme.typography.bodySmall, color = Slate)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Slate)
                        }
                    }
                }
            }
            else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Text("Invite friends to help", style = MaterialTheme.typography.titleLarge)
                    Text("When a friend joins with your link, you both get closer to the Community Connector badge.", color = Slate, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CobaltSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your invite code", style = MaterialTheme.typography.labelMedium, color = Slate)
                            Text("JORDAN7K", style = MaterialTheme.typography.headlineSmall, color = Cobalt)
                        }
                        IconButton({ copyLink(ctx) }) { Icon(Icons.Filled.ContentCopy, "Copy link", tint = Cobalt) }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Share invite", Modifier.fillMaxWidth(), icon = Icons.Filled.Share) {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "I'm volunteering in Tempe with VolunteerDash — shifts as short as 10 minutes. Join me: https://volunteerdash.app/i/JORDAN7K"), "Invite a friend"))
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Text("Friends who joined: ${Store.referrals}", style = MaterialTheme.typography.titleMedium)
                    Text("Demo: simulate a friend registering with your link.", style = MaterialTheme.typography.bodySmall, color = Slate)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ Store.addReferral(); Store.toast = "Alex joined with your invite!" }) { Text("Simulate friend joining") }
                }
            }
        }
    }
}

fun copyLink(ctx: Context) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Invite", "https://volunteerdash.app/i/JORDAN7K"))
    Store.toast = "Invite link copied"
}

@Composable
fun Podium(top: List<Rank>, onTap: (String) -> Unit) {
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        order.forEach { r ->
            val rank = top.indexOf(r) + 1
            val ring = when (rank) { 1 -> Gold; 2 -> Silver; else -> Bronze }
            Column(Modifier.weight(1f).clickable { if (!r.isMe) onTap(r.person.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (rank == 1) Icon(Icons.Filled.EmojiEvents, null, tint = Gold, modifier = Modifier.size(28.dp))
                Avatar(r.person, if (rank == 1) 84.dp else 66.dp, ring, if (r.isMe) Store.myPhoto else null)
                Text(r.person.name.substringBefore(" "), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("${hoursText(r.minutes)} h", style = MaterialTheme.typography.labelLarge, color = Cobalt)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.85f).height(if (rank == 1) 56.dp else if (rank == 2) 40.dp else 28.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Brush.verticalGradient(listOf(ring, ring.copy(alpha = .5f)))),
                    contentAlignment = Alignment.Center) { Text("$rank", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
fun RankRow(rank: Int, r: Rank, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (r.isMe) CoralSoft else Color.White)
        .border(1.dp, if (r.isMe) Coral else Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, color = Slate)
        Avatar(r.person, 44.dp, photoPath = if (r.isMe) Store.myPhoto else null); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (r.isMe) "You" else r.person.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (if (r.isMe) BADGES.filter { it.id in Store.earnedBadges() } else BADGES.filter { it.id in r.person.badges }).take(3).forEach { BadgeArt(it, true, 20.dp) }
            }
        }
        Text("${hoursText(r.minutes)} h", style = MaterialTheme.typography.titleMedium, color = Cobalt)
    }
}

// ---------------- Profile ----------------
@Composable
fun ProfileScreen(nav: Nav) {
    val ctx = LocalContext.current
    val (lvl, next, prog) = levelFor(Store.totalMinutes)
    val earned = Store.earnedBadges()
    var badgeInfo by remember { mutableStateOf<Badge?>(null) }
    var editName by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            val f = File(ctx.filesDir, "avatar.jpg")
            val src = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
            val side = minOf(src.width, src.height)
            val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = android.graphics.Bitmap.createScaledBitmap(sq, 512, 512, true)
            f.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            Store.myPhoto = null; Store.myPhoto = f.absolutePath; Store.save(); Store.toast = "Profile photo updated"
        }.onFailure { Store.toast = "Couldn't use that image. Try a JPEG or PNG." }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(20.dp)) {
                Text("Profile", style = MaterialTheme.typography.headlineMedium, color = Coral)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Box(Modifier.clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { MyAvatar(96.dp, Coral) }
                        Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(Cobalt), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoCamera, "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Store.myName, style = MaterialTheme.typography.headlineSmall)
                        InfoLine(Icons.Filled.Place, "Tempe, AZ")
                        Text("Level ${lvl.n} · ${lvl.name}", style = MaterialTheme.typography.labelLarge, color = Cobalt)
                    }
                    OutlinedIconButton({ editName = true }, border = androidx.compose.foundation.BorderStroke(1.dp, Coral)) { Icon(Icons.Filled.Edit, "Edit profile", tint = Coral) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Store.interests.forEach { CategoryPill(it) } }
                if (Store.myPhoto != null) TextButton({ File(Store.myPhoto!!).delete(); Store.myPhoto = null; Store.save() }) { Text("Remove photo", color = Slate) }
            }
        }
        item { Column(Modifier.padding(16.dp)) { HoursCard(lvl, next, prog) } }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(Icons.Filled.MilitaryTech, "Achievements", "${earned.size}/${BADGES.size}")
                BADGES.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { b ->
                            val has = b.id in earned
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))
                                .clickable { badgeInfo = b }.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                BadgeArt(b, has, 58.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(b.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, color = if (has) Ink else Slate)
                                if (has) Icon(Icons.Filled.Check, null, tint = Mint, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(Icons.Filled.WorkspacePremium, "Volunteer Certificate")
                Card(Modifier.fillMaxWidth(), onClick = { nav.go(Route.Certificate) }) {
                    CertificatePreview(Modifier.fillMaxWidth().height(170.dp))
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("View certificate", Modifier.fillMaxWidth(), color = Ink, icon = Icons.Filled.OpenInNew) { nav.go(Route.Certificate) }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Card(Modifier.fillMaxWidth(), padding = 4.dp) {
                    MenuRow(Icons.Filled.EventNote, "My bookings") { nav.go(Route.Bookings) }
                    MenuRow(Icons.Filled.ChatBubbleOutline, "Messages") { nav.go(Route.Messages) }
                    MenuRow(Icons.Filled.MenuBook, "First-time volunteering guide") { nav.go(Route.Guide) }
                    MenuRow(Icons.Filled.AdminPanelSettings, "Organizer tools (demo)") { nav.go(Route.Organizer) }
                    MenuRow(Icons.Filled.Settings, "Settings & privacy") { nav.go(Route.Settings) }
                    MenuRow(Icons.Filled.Logout, "Sign out", Coral) { Store.signedIn = false; Store.save() }
                }
            }
        }
    }
    badgeInfo?.let { b -> BadgeSheet(b, b.id in earned) { badgeInfo = null } }
    if (editName) {
        var n by remember { mutableStateOf(Store.myName) }
        AlertDialog(onDismissRequest = { editName = false }, containerColor = Color.White, title = { Text("Edit profile") },
            text = {
                Column {
                    OutlinedTextField(n, { n = it.take(40) }, label = { Text("Display name") }, isError = n.trim().length < 2, singleLine = true)
                    Text("Interests", style = MaterialTheme.typography.labelMedium, color = Slate, modifier = Modifier.padding(top = 8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Category.entries.forEach { c -> FilterChip(c in Store.interests, { if (c in Store.interests) Store.interests.remove(c) else Store.interests.add(c) }, label = { Text(c.label) }) }
                    }
                }
            },
            confirmButton = { Button({ if (n.trim().length >= 2) { Store.myName = n.trim(); Store.save(); editName = false } }) { Text("Save") } },
            dismissButton = { TextButton({ editName = false }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeSheet(b: Badge, earned: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeArt(b, earned, 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(b.name, style = MaterialTheme.typography.headlineSmall)
            Text(b.rule, style = MaterialTheme.typography.bodyMedium, color = Slate, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            if (earned) Pill("Earned", MintSoft, Color(0xFF167A4C), Icons.Filled.CheckCircle) else Pill("Not yet earned", Color(0xFFF0F3FA), Slate, Icons.Filled.Lock)
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = Cobalt, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint); Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = if (tint == Coral) Coral else Ink)
        Icon(Icons.Filled.ChevronRight, null, tint = Slate)
    }
}

@Composable
fun HoursCard(lvl: Level, next: Level?, prog: Float) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(22.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val wave = Path().apply {
                moveTo(w * 0.35f, h); cubicTo(w * 0.55f, h * 0.55f, w * 0.7f, h * 0.95f, w, h * 0.35f); lineTo(w, h); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(CobaltSoft, Color(0xFFCFDCFF))))
        }
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = Ink); Spacer(Modifier.width(8.dp))
                Text("Volunteer Hours", style = MaterialTheme.typography.titleMedium)
            }
            Text("${hoursText(Store.totalMinutes)} hours", style = MaterialTheme.typography.displaySmall, color = Ink)
            Text("${Store.shiftCount} confirmed shifts", style = MaterialTheme.typography.bodySmall, color = Slate)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)), color = Coral, trackColor = CoralSoft)
            Spacer(Modifier.height(4.dp))
            Text(next?.let { "Level ${lvl.n} ${lvl.name} · ${it.minHours - Store.totalMinutes / 60} h to ${it.name}" } ?: "Top level reached!",
                style = MaterialTheme.typography.labelMedium, color = Slate)
        }
    }
}

@Composable
fun CertificatePreview(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFFDF8)).border(3.dp, Cobalt.copy(alpha = .6f), RoundedCornerShape(10.dp))
        .padding(6.dp).border(1.dp, Coral.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(10.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { LogoMark(14.dp); Spacer(Modifier.width(4.dp)); Text("VolunteerDash", fontFamily = Serif, fontSize = 11.sp, color = Ink) }
            Text("Certificate of Service", fontFamily = Serif, fontSize = 18.sp, color = Cobalt)
            Text("This certifies that", fontSize = 9.sp, color = Slate)
            Text(Store.myName, fontFamily = Serif, fontSize = 20.sp, color = Ink)
            Text("has completed ${hoursText(Store.totalMinutes)} volunteer hours of community service with VolunteerDash.", fontSize = 9.sp, color = Slate, textAlign = TextAlign.Center)
        }
        Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Coral), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

// ---------------- Certificate ----------------
@Composable
fun CertificateScreen(nav: Nav) {
    val ctx = LocalContext.current
    val certId = remember { "VD-" + (Store.totalMinutes * 7919 % 9000 + 1000) + "-" + Store.myName.hashCode().toUInt().toString(36).uppercase().take(4) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Certificate", nav)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CertificatePreview(Modifier.fillMaxWidth().aspectRatio(1.414f))
            Card(Modifier.fillMaxWidth()) {
                LabeledText(Icons.Filled.Badge, "Certificate ID", certId)
                LabeledText(Icons.Filled.DateRange, "Period", "${Demo.pastShifts.minOf { it.date }.d()} – ${LocalDate.now().d()}")
                LabeledText(Icons.Filled.Verified, "Confirmed hours", "${hoursText(Store.totalMinutes)} hours across ${Store.shiftCount} shifts")
                Text("Generated from organizer-confirmed attendance. If hours are corrected later, this certificate is marked superseded and you can issue an updated one.",
                    style = MaterialTheme.typography.bodySmall, color = Slate)
            }
            PrimaryButton("Share PDF certificate", Modifier.fillMaxWidth(), icon = Icons.Filled.PictureAsPdf) {
                val f = makeCertificatePdf(ctx, certId)
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share certificate"))
            }
            OutlinedButton({
                val f = makeCertificatePdf(ctx, certId)
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    .onFailure { Store.toast = "No PDF viewer installed — use Share instead" }
            }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) { Text("Open PDF preview") }
        }
    }
}

fun makeCertificatePdf(ctx: Context, certId: String): File {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
    val c = page.canvas
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawColor(android.graphics.Color.rgb(255, 253, 248))
    p.style = Paint.Style.STROKE; p.strokeWidth = 10f; p.color = android.graphics.Color.rgb(31, 79, 224); c.drawRect(24f, 24f, 818f, 571f, p)
    p.strokeWidth = 2f; p.color = android.graphics.Color.rgb(255, 107, 91); c.drawRect(40f, 40f, 802f, 555f, p)
    p.style = Paint.Style.FILL
    fun text(s: String, y: Float, size: Float, color: Int, serif: Boolean = false, bold: Boolean = false) {
        p.textSize = size; p.color = color; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, 421f, y, p)
    }
    val ink = android.graphics.Color.rgb(15, 26, 61); val slate = android.graphics.Color.rgb(90, 103, 135)
    text("VolunteerDash", 110f, 22f, ink, serif = true, bold = true)
    text("Certificate of Service", 175f, 44f, android.graphics.Color.rgb(31, 79, 224), serif = true, bold = true)
    text("This certifies that", 225f, 16f, slate)
    text(Store.myName, 290f, 48f, ink, serif = true)
    text("has completed ${hoursText(Store.totalMinutes)} hours of organizer-confirmed community service", 340f, 16f, slate)
    text("across ${Store.shiftCount} shifts with ${Demo.pastShifts.map { it.orgName }.distinct().size} Tempe organizations", 364f, 16f, slate)
    text("Period: ${Demo.pastShifts.minOf { it.date }.d()} – ${LocalDate.now().d()}", 410f, 14f, ink)
    text("Issued ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))} · Certificate ID $certId", 480f, 12f, slate)
    text("Fictional demonstration community · volunteerdash.app/c/$certId", 520f, 10f, slate)
    p.color = android.graphics.Color.rgb(255, 107, 91); c.drawCircle(730f, 470f, 34f, p)
    doc.finishPage(page)
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "VolunteerDash-Certificate-$certId.pdf")
    f.outputStream().use { doc.writeTo(it) }; doc.close()
    return f
}

// ---------------- Public profile ----------------
@Composable
fun PublicProfileScreen(nav: Nav, id: String) {
    val p = Demo.person(id) ?: return
    var badgeInfo by remember { mutableStateOf<Badge?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackBar("", nav)
        if (p.isPrivate) { EmptyState(Icons.Filled.Lock, "Private profile", "This volunteer keeps their profile private."); return }
        val (lvl, _, prog) = levelFor(p.hours * 60)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(p, 112.dp, if (p.shared > 0) Mint else Cobalt)
                    Spacer(Modifier.height(10.dp))
                    Text(p.name, style = MaterialTheme.typography.headlineMedium)
                    Text("Tempe, AZ · Level ${lvl.n} ${lvl.name}", color = Slate)
                    Spacer(Modifier.height(6.dp))
                    Pill("Demo profile", Color(0xFFF0F3FA), Slate)
                    if (p.shared > 0) { Spacer(Modifier.height(8.dp)); Pill("You've volunteered together ${p.shared} times", MintSoft, Color(0xFF167A4C), Icons.Filled.Favorite) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("${p.hours}", "hours", Modifier.weight(1f)); StatBox("${p.badges.size}", "badges", Modifier.weight(1f))
                    StatBox("${(prog * 100).toInt()}%", "to next level", Modifier.weight(1f))
                }
            }
            item { Text(p.bio, style = MaterialTheme.typography.bodyLarge) }
            item {
                Text("Badges", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BADGES.filter { it.id in p.badges }.forEach { b -> Box(Modifier.clickable { badgeInfo = b }) { BadgeArt(b, true, 60.dp) } }
                }
            }
            item {
                Text("Interests", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.interests.forEach { CategoryPill(it) } }
            }
        }
    }
    badgeInfo?.let { BadgeSheet(it, true) { badgeInfo = null } }
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Cobalt); Text(label, style = MaterialTheme.typography.bodySmall, color = Slate)
    }
}

// ---------------- Messages ----------------
@Composable
fun MessagesScreen(nav: Nav) {
    val threads = (Store.chats.keys + "pantry-boxes").distinct()
    Column(Modifier.fillMaxSize()) {
        BackBar("Messages", nav)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(threads) { id ->
                val op = Demo.opp(id)
                val last = Store.chats[id]?.lastOrNull()?.text ?: "Hi Jordan! Thanks for signing up — see you at the side door."
                Card(Modifier.fillMaxWidth(), onClick = { nav.go(Route.Chat(id)) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(op.category.color.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Icon(op.category.icon, null, tint = op.category.color) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(op.org.name, style = MaterialTheme.typography.titleSmall)
                            Text(last, style = MaterialTheme.typography.bodySmall, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(nav: Nav, oppId: String) {
    val op = Demo.opp(oppId)
    var draft by remember { mutableStateOf("") }
    val msgs = Store.chats[oppId] ?: listOf(ChatMessage(false, "Hi! I'm ${op.org.contact} from ${op.org.name}. Message me with any questions about your shift.", demo = true))
    val list = rememberLazyListState()
    var pending by remember { mutableIntStateOf(0) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    LaunchedEffect(pending) {
        if (pending > 0) {
            delay(2200)
            Store.chats[oppId] = (Store.chats[oppId] ?: msgs) + ChatMessage(false,
                "Thanks for reaching out! ${if (op.arrivalLead > 0) "Please arrive ${op.arrivalLead} minutes early. " else ""}${op.preparation} See you soon!", demo = true)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(op.org.contact, nav)
        Text("${op.title} · ${op.org.name}", style = MaterialTheme.typography.bodySmall, color = Slate, modifier = Modifier.background(Color.White).fillMaxWidth().padding(start = 56.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
                    Text(m.text, Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp)).background(if (m.fromMe) Cobalt else Color.White)
                        .border(if (m.fromMe) 0.dp else 1.dp, Line, RoundedCornerShape(18.dp)).padding(12.dp),
                        color = if (m.fromMe) Color.White else Ink, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.demo) Pill("Demo reply", Color(0xFFF0F3FA), Slate)
                        Text(" " + m.time.t() + if (m.fromMe) " · Sent ✓" else "", style = MaterialTheme.typography.labelSmall, color = Slate)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it.take(1000) }, Modifier.weight(1f), placeholder = { Text("Message ${op.org.contact.substringBefore(" ")}…") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
            Spacer(Modifier.width(8.dp))
            FilledIconButton({
                if (draft.isNotBlank()) { Store.chats[oppId] = msgs + ChatMessage(true, draft.trim()); draft = ""; pending++ }
            }, Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Coral)) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        }
    }
}

// ---------------- Bookings ----------------
@Composable
fun BookingsScreen(nav: Nav) {
    val upcoming = Store.bookings.values.filter { it.state != BookingState.CONFIRMED }.mapNotNull { b -> Demo.slot(b.slotId)?.let { b to it } }.sortedBy { it.second.start }
    Column(Modifier.fillMaxSize()) {
        BackBar("My bookings", nav)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Upcoming", style = MaterialTheme.typography.titleLarge) }
            if (upcoming.isEmpty()) item { EmptyState(Icons.Filled.EventAvailable, "No upcoming shifts", "Find a short shift that fits your week.", "Discover") { nav.tab(Route.Discover) } }
            items(upcoming) { (_, s) -> val op = Demo.opp(s.oppId); OpportunityCard(op, s) { nav.go(Route.Detail(op.id, s.id)) } }
            item { Text("Completed · organizer-confirmed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            val done = Store.confirmedFromBookings.mapNotNull { b -> Demo.slot(b.slotId)?.let { PastShift(Demo.opp(it.oppId).title, Demo.opp(it.oppId).category, it.start.toLocalDate(), b.confirmedMinutes, Demo.opp(it.oppId).org.name) } } + Demo.pastShifts
            items(done) { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(p.category.icon, null, tint = p.category.color); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(p.title, style = MaterialTheme.typography.titleSmall); Text("${p.date.d()} · ${p.orgName}", style = MaterialTheme.typography.bodySmall, color = Slate) }
                    Pill("${p.minutes} min ✓", MintSoft, Color(0xFF167A4C))
                }
            }
        }
    }
}

// ---------------- Organizer ----------------
@Composable
fun OrganizerScreen(nav: Nav) {
    val roster = Store.bookings.values.mapNotNull { b -> Demo.slot(b.slotId)?.let { b to it } }.sortedBy { it.second.start }
    var confirming by remember { mutableStateOf<Pair<Booking, Slot>?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Organizer tools", nav)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF3D6)).padding(12.dp)) {
                    Icon(Icons.Filled.Info, null, tint = Color(0xFF8A5A00)); Spacer(Modifier.width(8.dp))
                    Text("Demo organizer view (Maya Torres). Check volunteers in and confirm the minutes they actually served. Volunteers can't confirm their own hours.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF5A3D00))
                }
            }
            item { Text("Roster · Jordan's shifts", style = MaterialTheme.typography.titleLarge) }
            if (roster.isEmpty()) item { EmptyState(Icons.Filled.Groups, "No bookings to confirm", "Book a shift as Jordan first, then confirm it here.") }
            items(roster) { (b, s) ->
                val op = Demo.opp(s.oppId)
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MyAvatar(44.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Store.myName, style = MaterialTheme.typography.titleSmall)
                            Text("${op.title} · ${relDay(s.start.toLocalDate())} ${s.start.t()}", style = MaterialTheme.typography.bodySmall, color = Slate)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    when (b.state) {
                        BookingState.BOOKED -> Button({ Store.bookings[s.id] = b.copy(state = BookingState.CHECKED_IN); Store.save(); Store.toast = "Checked in" },
                            colors = ButtonDefaults.buttonColors(containerColor = Cobalt)) { Text("Check in") }
                        BookingState.CHECKED_IN -> Button({ confirming = b to s }, colors = ButtonDefaults.buttonColors(containerColor = Mint)) { Text("Confirm minutes") }
                        BookingState.CONFIRMED -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("Confirmed ${b.confirmedMinutes} min", MintSoft, Color(0xFF167A4C), Icons.Filled.Verified)
                            Spacer(Modifier.width(8.dp))
                            TextButton({ confirming = b to s }) { Text("Correct") }
                        }
                    }
                }
            }
        }
    }
    confirming?.let { (b, s) ->
        var mins by remember { mutableStateOf(if (b.confirmedMinutes > 0) b.confirmedMinutes.toString() else s.minutes.toString()) }
        val valid = mins.toIntOrNull()?.let { it in 1..(s.minutes + 120) } == true
        AlertDialog(onDismissRequest = { confirming = null }, containerColor = Color.White,
            title = { Text(if (b.state == BookingState.CONFIRMED) "Correct minutes" else "Confirm attendance") },
            text = { Column {
                Text("${Demo.opp(s.oppId).title} · scheduled ${s.minutes} min", color = Slate)
                OutlinedTextField(mins, { mins = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes served") }, isError = !valid,
                    supportingText = { if (!valid) Text("Enter 1–${s.minutes + 120} minutes") }, singleLine = true)
                Text("Recorded in the audit trail as confirmed by Maya Torres.", style = MaterialTheme.typography.bodySmall, color = Slate)
            } },
            confirmButton = { Button({ if (valid) { Store.confirm(s.id, mins.toInt()); confirming = null; Store.toast = "Hours confirmed — Jordan's totals updated" } }, enabled = valid) { Text("Confirm") } },
            dismissButton = { TextButton({ confirming = null }) { Text("Cancel") } })
    }
}

// ---------------- Settings ----------------
@Composable
fun SettingsScreen(nav: Nav) {
    var profilePublic by remember { mutableStateOf(true) }
    var showAttendance by remember { mutableStateOf(true) }
    var leaderboard by remember { mutableStateOf(true) }
    var reduce by remember { mutableStateOf(reducedMotion) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Settings & privacy", nav)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Privacy", style = MaterialTheme.typography.titleLarge) }
            item { ToggleRow("Public profile", "Other volunteers can open your profile and badges.", profilePublic) { profilePublic = it } }
            item { ToggleRow("Show me on shift rosters", "Teammates see you're attending the same shift.", showAttendance) { showAttendance = it } }
            item { ToggleRow("Weekly Top 10", "Appear on the community leaderboard.", leaderboard) { leaderboard = it } }
            item { Text("Scheduling", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("Extra travel buffer: ${Store.travelBuffer} min", style = MaterialTheme.typography.titleSmall)
                    Text("Added on top of estimated travel time when matching shifts to your calendar.", style = MaterialTheme.typography.bodySmall, color = Slate)
                    Slider(Store.travelBuffer.toFloat(), { Store.travelBuffer = it.toInt() }, valueRange = 0f..30f, steps = 5)
                }
            }
            item { ToggleRow("Reduce motion", "Turn off celebration and transition animations.", reduce) { reduce = it; reducedMotion = it } }
            item { Text("About", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("VolunteerDash prototype 0.9", style = MaterialTheme.typography.titleSmall)
                    Text("All people, organizations and messages in this demo are fictional. Venue names refer to real public places in Tempe, AZ. Photos from Wikimedia Commons (see ASSET_LICENSES.md); fonts under SIL OFL; map is a stylized illustration based on OpenStreetMap geography.",
                        style = MaterialTheme.typography.bodySmall, color = Slate)
                }
            }
            item {
                OutlinedButton({
                    Store.bookings.clear(); Store.events.clear(); Store.chats.clear(); Store.referrals = 0
                    Store.signedIn = false; Store.guideSeen = false; Store.save()
                }, Modifier.fillMaxWidth()) { Text("Reset demo data", color = Coral) }
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, body: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodySmall, color = Slate) }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Cobalt))
    }
}
