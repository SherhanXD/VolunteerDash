package com.volunteerdash

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
fun LocalDateTime.t(): String = format(timeFmt)
fun LocalDate.d(): String = format(dayFmt)
fun relDay(d: LocalDate): String = when (d) {
    LocalDate.now() -> "Today"
    LocalDate.now().plusDays(1) -> "Tomorrow"
    else -> d.d()
}
fun hoursText(min: Int): String = if (min % 60 == 0) "${min / 60}" else String.format(Locale.US, "%.1f", min / 60f)
fun durText(min: Int) = if (min < 60) "$min min" else if (min % 60 == 0) "${min / 60} hr" else "${min / 60} hr ${min % 60} min"

/** Heart-shaped map pin path inside a w x h box. */
fun heartPinPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w / 2, h)
    cubicTo(w * 0.1f, h * 0.62f, 0f, h * 0.42f, 0f, h * 0.3f)
    cubicTo(0f, h * 0.12f, w * 0.16f, 0f, w * 0.3f, 0f)
    cubicTo(w * 0.4f, 0f, w * 0.47f, h * 0.06f, w / 2, h * 0.14f)
    cubicTo(w * 0.53f, h * 0.06f, w * 0.6f, 0f, w * 0.7f, 0f)
    cubicTo(w * 0.84f, 0f, w, h * 0.12f, w, h * 0.3f)
    cubicTo(w, h * 0.42f, w * 0.9f, h * 0.62f, w / 2, h)
    close()
}

@Composable
fun LogoMark(size: Dp = 28.dp, color: Color = Coral) {
    Canvas(Modifier.size(size)) {
        drawPath(heartPinPath(this.size.width, this.size.height), color)
        drawCircle(Color.White, radius = this.size.width * 0.14f, center = Offset(this.size.width / 2, this.size.height * 0.36f))
    }
}

@Composable
fun Wordmark(dark: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark(26.dp, if (dark) Coral else Color.White)
        Spacer(Modifier.width(8.dp))
        Text("VolunteerDash", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
            color = if (dark) Ink else Color.White)
    }
}

@Composable
fun Avatar(person: Person?, size: Dp, ring: Color? = null, photoPath: String? = null) {
    val mod = Modifier.size(size).let { if (ring != null) it.border(2.5.dp, ring, CircleShape).padding(3.dp) else it }
        .clip(CircleShape)
    val bmp = remember(photoPath) { photoPath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } }
    Box(mod.background(person?.tint ?: CoralSoft), contentAlignment = Alignment.Center) {
        when {
            bmp != null -> Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            person?.isPrivate == true -> Icon(Icons.Filled.Lock, null, tint = Slate, modifier = Modifier.size(size / 2.6f))
            person?.portrait != null -> Image(painterResource(person.portrait), person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Text(person?.initials ?: "JR", fontWeight = FontWeight.Bold, color = Ink.copy(alpha = .75f),
                fontSize = (size.value / 2.6f).sp)
        }
    }
}

@Composable
fun MyAvatar(size: Dp, ring: Color? = null) =
    Avatar(Demo.me.copy(name = Store.myName, portrait = if (Store.myName == "Jordan Reyes") Demo.me.portrait else null), size, ring, Store.myPhoto)

fun hexPath(w: Float, h: Float): Path = Path().apply {
    val cx = w / 2; val cy = h / 2; val r = minOf(w, h) / 2
    for (i in 0 until 6) {
        val a = Math.toRadians((60 * i - 90).toDouble())
        val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun BadgeArt(badge: Badge, earned: Boolean, size: Dp = 64.dp) {
    val painter = rememberVectorPainter(badge.icon)
    Box(Modifier.size(size).semantics { contentDescription = badge.name + if (earned) ", earned" else ", locked" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = hexPath(this.size.width, this.size.height)
            val c1 = if (earned) badge.c1 else Color(0xFFCDD3E0); val c2 = if (earned) badge.c2 else Color(0xFFE6EAF2)
            drawPath(outer, Brush.linearGradient(listOf(c2, c1), Offset.Zero, Offset(this.size.width, this.size.height)))
            val inset = this.size.width * 0.12f
            translate(inset, inset) {
                drawPath(hexPath(this.size.width - inset * 2, this.size.height - inset * 2), Color.White.copy(alpha = .22f))
            }
            drawPath(outer, Color.White.copy(alpha = .9f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = this.size.width * 0.05f))
            val iconSize = this.size.width * 0.42f
            translate((this.size.width - iconSize) / 2, (this.size.height - iconSize) / 2) {
                with(painter) { draw(androidx.compose.ui.geometry.Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White)) }
            }
        }
        if (!earned) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.32f).clip(CircleShape).background(Slate), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(size * 0.18f))
        }
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun SectionTitle(icon: ImageVector, title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Coral, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Cobalt, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.shadow(6.dp, shape, ambientColor = Cobalt.copy(alpha = .08f), spotColor = Cobalt.copy(alpha = .12f))
            .clip(shape).background(Color.White).border(1.dp, Line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(padding),
        content = content
    )
}

@Composable
fun InfoLine(icon: ImageVector, text: String, color: Color = Slate, maxLines: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CostPill(op: Opportunity) =
    if (op.isFree) Pill("Free", MintSoft, Color(0xFF167A4C)) else Pill(op.costNote, Color(0xFFFFF3D6), Color(0xFF8A5A00))

@Composable
fun CategoryPill(c: Category) = Pill(c.label, c.color.copy(alpha = .12f), c.color, c.icon)

@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CobaltDark, Cobalt, CobaltBright)))
        .statusBarsPadding().padding(20.dp), content = content)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(CobaltSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Cobalt, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Slate, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(14.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Coral, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
    }
}

/** Opportunity result card in the style of the reference mock. */
@Composable
fun OpportunityCard(op: Opportunity, slot: Slot?, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onOpen, padding = 12.dp) {
        Row {
            Image(painterResource(op.photos.first()), op.title, Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(op.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(op.org.name, style = MaterialTheme.typography.bodySmall, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CostPill(op); CategoryPill(op.category) }
                Spacer(Modifier.height(6.dp))
                if (slot != null) {
                    InfoLine(Icons.Filled.CalendarMonth, relDay(slot.start.toLocalDate()))
                    InfoLine(Icons.Filled.Schedule, "${slot.start.t()} · ${durText(slot.minutes)} shift")
                }
                InfoLine(Icons.Filled.Place, String.format(Locale.US, "%.1f mi · ~%d min travel", op.distanceMi, op.travelMin))
            }
        }
        if (slot != null) {
            val conflict = Store.conflictFor(slot)
            val familiar = slot.attendees.mapNotNull { Demo.person(it) }.filter { it.shared > 0 }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conflict == null) Pill("Fits your schedule", MintSoft, Color(0xFF167A4C), Icons.Filled.CheckCircle)
                else Pill("Conflicts: ${conflict.title}", CoralSoft, Color(0xFFB0352A), Icons.Filled.EventBusy)
                Spacer(Modifier.weight(1f))
                if (familiar.isNotEmpty()) {
                    Avatar(familiar.first(), 26.dp, Mint)
                    Spacer(Modifier.width(4.dp))
                    Text("${familiar.first().name.substringBefore(" ")} is going", style = MaterialTheme.typography.labelSmall, color = Slate)
                }
            }
        }
    }
}
