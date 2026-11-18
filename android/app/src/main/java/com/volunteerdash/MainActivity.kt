package com.volunteerdash

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

sealed interface Route {
    data object ShiftFit : Route; data object Home : Route; data object Calendar : Route; data object Discover : Route
    data object Community : Route; data object Profile : Route
    data class Detail(val oppId: String, val slotId: String? = null) : Route
    data class PersonR(val id: String) : Route
    data class Chat(val oppId: String) : Route
    data object Certificate : Route; data object Organizer : Route; data object Guide : Route
    data object Bookings : Route; data object Settings : Route; data object Messages : Route
}

val tabs: List<Triple<Route, String, ImageVector>> = listOf(
    Triple(Route.Home, "Home", Icons.Filled.Home), Triple(Route.Calendar, "Calendar", Icons.Filled.CalendarMonth),
    Triple(Route.Discover, "Discover", Icons.Filled.Search), Triple(Route.Community, "Community", Icons.Filled.Groups),
    Triple(Route.Profile, "Profile", Icons.Filled.Person),
)

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current get() = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun tab(r: Route) { stack.clear(); stack.add(Route.Home); if (r != Route.Home) stack.add(r) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
}

/** Discover filters shared between Home, Calendar and Discover. */
object Filters {
    var query by mutableStateOf("")
    var maxDuration by mutableStateOf<Int?>(null)
    var freeOnly by mutableStateOf(false)
    var fitsOnly by mutableStateOf(false)
    var openOnly by mutableStateOf(true)
    var radius by mutableStateOf(10)
    var day by mutableStateOf<java.time.LocalDate?>(null)
    var timeOfDay by mutableStateOf<String?>(null)
    val categories = mutableStateListOf<Category>()
    var showSheet by mutableStateOf(false)
    fun reset() { query = ""; maxDuration = null; freeOnly = false; fitsOnly = false; openOnly = true; radius = 10; day = null; timeOfDay = null; categories.clear() }
    val activeCount get() = listOf(maxDuration != null, freeOnly, fitsOnly, radius != 10, day != null, timeOfDay != null, categories.isNotEmpty()).count { it }
}

var reducedMotion = false

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        reducedMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { VolunteerDashTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    if (!Store.signedIn) { WelcomeScreen(); return }
    val nav = remember { Nav() }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Store.toast) { Store.toast?.let { snack.showSnackbar(it); Store.toast = null } }

    val isTab = tabs.any { it.first == nav.current }
    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (isTab) BottomBar(nav) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(nav.current, label = "nav",
                transitionSpec = {
                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(150))
                }) { r ->
                when (r) {
                    Route.Home -> HomeScreen(nav)
                    Route.ShiftFit -> ShiftFitScreen(nav)
                    Route.Calendar -> CalendarScreen(nav)
                    Route.Discover -> DiscoverScreen(nav)
                    Route.Community -> CommunityScreen(nav)
                    Route.Profile -> ProfileScreen(nav)
                    is Route.Detail -> DetailScreen(nav, r.oppId, r.slotId)
                    is Route.PersonR -> PublicProfileScreen(nav, r.id)
                    is Route.Chat -> ChatScreen(nav, r.oppId)
                    Route.Certificate -> CertificateScreen(nav)
                    Route.Organizer -> OrganizerScreen(nav)
                    Route.Guide -> GuideScreen(onDone = { Store.guideSeen = true; Store.save(); nav.back() })
                    Route.Bookings -> BookingsScreen(nav)
                    Route.Settings -> SettingsScreen(nav)
                    Route.Messages -> MessagesScreen(nav)
                }
            }
        }
    }
    if (!Store.guideSeen && nav.current == Route.Home) LaunchedEffect(Unit) { nav.go(Route.Guide) }
    Store.celebrate?.let { BadgeCelebration(it) { Store.celebrate = null } }
}

@Composable
fun BottomBar(nav: Nav) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        tabs.forEach { (r, label, icon) ->
            val sel = nav.current == r
            NavigationBarItem(selected = sel, onClick = { nav.tab(r) }, icon = { Icon(icon, label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Cobalt,
                    selectedTextColor = Cobalt, unselectedIconColor = Slate, unselectedTextColor = Slate))
        }
    }
}

@Composable
fun BackBar(title: String, nav: Nav, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.weight(1f))
        actions()
    }
}

// ---------------- Welcome ----------------
@Composable
fun WelcomeScreen() {
    val pages = listOf(
        Triple(R.drawable.city_tempe_1, "Help Tempe in\n10-minute moments", "Find nearby shifts that fit a lunch break, an evening, or a slow Saturday."),
        Triple(R.drawable.opp_pantry_boxes_1, "Matched to your\nreal schedule", "Import your calendar and we'll only show shifts you can actually make."),
        Triple(R.drawable.opp_mural_1, "See your impact\ngrow", "Confirmed hours, badges, a weekly Top 10 and certificates you can share."),
    )
    val pager = rememberPagerState { pages.size }
    var showCreate by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Ink)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            Box(Modifier.fillMaxSize()) {
                Image(painterResource(pages[i].first), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x331638A8), Color(0xCC0F1A3D), Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 250.dp)) {
                    Text(pages[i].second, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text(pages[i].third, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .85f))
                }
            }
        }
        Box(Modifier.statusBarsPadding().padding(20.dp)) { Wordmark(dark = false) }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    Box(Modifier.padding(3.dp).height(8.dp).width(if (pager.currentPage == i) 24.dp else 8.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) Coral else Color.White.copy(alpha = .4f)))
                }
            }
            PrimaryButton("Explore as Jordan (demo)", Modifier.fillMaxWidth(), icon = Icons.Filled.Explore) {
                Store.myName = "Jordan Reyes"; Store.signedIn = true; Store.save()
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showCreate = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White)) {
                Text("Create an account", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Text("Demo community: people and organizations are fictional.", color = Color.White.copy(alpha = .6f),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
    if (showCreate) CreateAccountDialog { showCreate = false }
}

@Composable
fun CreateAccountDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val nameErr = tried && name.trim().length < 2
    val emailErr = tried && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val pwErr = tried && pw.length < 8
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Create your account", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.imePadding()) {
                OutlinedTextField(name, { name = it }, label = { Text("Display name") }, isError = nameErr, singleLine = true,
                    supportingText = { if (nameErr) Text("Enter at least 2 characters") })
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, isError = emailErr, singleLine = true,
                    supportingText = { if (emailErr) Text("Enter a valid email address") })
                OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, isError = pwErr, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    supportingText = { Text(if (pwErr) "Use at least 8 characters" else "8+ characters") })
                OutlinedTextField(invite, { invite = it.uppercase() }, label = { Text("Invite code (optional)") }, singleLine = true)
                Text("City: Tempe, AZ", style = MaterialTheme.typography.bodySmall, color = Slate)
            }
        },
        confirmButton = {
            Button(onClick = {
                tried = true
                if (name.trim().length >= 2 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && pw.length >= 8) {
                    Store.myName = name.trim(); Store.signedIn = true; Store.save(); onDismiss()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Cobalt)) { Text("Create account") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

// ---------------- Guide ----------------
@Composable
fun GuideScreen(onDone: () -> Unit) {
    val steps = listOf(
        Triple(Icons.Filled.Map, "Discover", "Browse the map or search for shifts near you. Filter by how much time you have, down to 10 minutes."),
        Triple(Icons.Filled.EventAvailable, "Book a slot", "Pick an organizer-created time. We check it against your calendar, including travel time."),
        Triple(Icons.Filled.Backpack, "Prepare", "Read the preparation notes and arrival time. Message the organizer with any questions."),
        Triple(Icons.Filled.HowToReg, "Check in", "Find the organizer on arrival. They check you in on their roster."),
        Triple(Icons.Filled.Verified, "Hours confirmed", "After the shift, the organizer confirms your minutes. Hours, badges and certificates update automatically."),
    )
    Column(Modifier.fillMaxSize().background(Color.White)) {
        GradientHeader {
            Text("Your first shift", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Five quick steps from finding a shift to confirmed hours.", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            steps.forEachIndexed { i, (ic, t, b) ->
                Row {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (i == 4) MintSoft else CobaltSoft), contentAlignment = Alignment.Center) {
                        Icon(ic, null, tint = if (i == 4) Mint else Cobalt)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column { Text("${i + 1}. $t", style = MaterialTheme.typography.titleMedium); Text(b, style = MaterialTheme.typography.bodyMedium, color = Slate) }
                }
            }
        }
        PrimaryButton("Start exploring", Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), onClick = onDone)
    }
}

// ---------------- Celebration ----------------
@Composable
fun BadgeCelebration(badge: Badge, onDismiss: () -> Unit) {
    val scale = remember { Animatable(if (reducedMotion) 1f else 0.3f) }
    val burst = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) { launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }; burst.animateTo(1f, tween(900)) }
    }
    val pieces = remember { List(36) { Triple(Random.nextFloat() * 360f, 0.5f + Random.nextFloat() * 0.5f, listOf(Coral, Cobalt, Mint, Gold)[it % 4]) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(340.dp)) {
                pieces.forEach { (ang, dist, col) ->
                    val r = size.minDimension / 2 * dist * burst.value
                    val a = Math.toRadians(ang.toDouble())
                    drawCircle(col.copy(alpha = 1f - burst.value * 0.4f), radius = 7f, center = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
                }
            }
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Badge unlocked!", style = MaterialTheme.typography.labelLarge, color = Coral)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.scale(scale.value)) { BadgeArt(badge, true, 110.dp) }
                Spacer(Modifier.height(14.dp))
                Text(badge.name, style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text(badge.rule, style = MaterialTheme.typography.bodyMedium, color = Slate, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                PrimaryButton("Awesome", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun SuccessBurst(modifier: Modifier = Modifier) {
    val p = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reducedMotion) p.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Box(modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val r1 = size.minDimension * 0.32f; val r2 = r1 + size.minDimension * 0.18f * p.value
                drawLine(if (i % 2 == 0) Coral else Gold, Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                    Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()), strokeWidth = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Box(Modifier.size(72.dp).scale(0.6f + 0.4f * p.value).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, "Booked", tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}
