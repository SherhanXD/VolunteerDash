package com.volunteerdash

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.*
import kotlin.random.Random

enum class Category(val label: String, val color: Color, val icon: ImageVector) {
    FOOD("Food security", Color(0xFFE8743B), Icons.Filled.Restaurant),
    ENVIRONMENT("Environment", Color(0xFF2BB673), Icons.Filled.Park),
    ANIMALS("Animals", Color(0xFFA35BD6), Icons.Filled.Pets),
    EDUCATION("Education", Color(0xFF1F8FE0), Icons.Filled.MenuBook),
    SENIORS("Seniors", Color(0xFFE0569B), Icons.Filled.Elderly),
    HEALTH("Health", Color(0xFFE04848), Icons.Filled.Favorite),
    HOUSING("Housing", Color(0xFFC98A1B), Icons.Filled.Construction),
    ARTS("Arts", Color(0xFF7A5CFA), Icons.Filled.Palette),
}

data class Org(val id: String, val name: String, val contact: String, val email: String, val phone: String)

data class Opportunity(
    val id: String, val title: String, val org: Org, val category: Category, val photos: List<Int>,
    val place: String, val address: String, val lat: Double, val lng: Double,
    val costCents: Int, val costNote: String, val summary: String, val description: String,
    val preparation: String, val accessibility: String, val arrivalLead: Int,
    val durations: List<Int>, val tags: List<String>,
) {
    val isFree get() = costCents == 0
    val distanceMi get() = haversineMi(HOME_LAT, HOME_LNG, lat, lng)
    val travelMin get() = max(3, ceil(distanceMi / 25.0 * 60).toInt())
}

data class Slot(val id: String, val oppId: String, val start: LocalDateTime, val minutes: Int, val capacity: Int,
                val baseBooked: Int, val attendees: List<String>, val cancelled: Boolean = false) {
    val end: LocalDateTime get() = start.plusMinutes(minutes.toLong())
}

data class Person(val id: String, val name: String, val portrait: Int?, val tint: Color, val hours: Int,
                  val weekMinutes: Int, val shared: Int, val interests: List<Category>, val bio: String,
                  val badges: List<String>, val isPrivate: Boolean = false) {
    val initials get() = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
}

data class PersonalEvent(val id: String, val title: String, val date: LocalDate, val startMin: Int, val minutes: Int,
                         val busy: Boolean = true, val notes: String = "", val source: String = "manual") {
    val start: LocalDateTime get() = date.atStartOfDay().plusMinutes(startMin.toLong())
    val end: LocalDateTime get() = start.plusMinutes(minutes.toLong())
}

data class PastShift(val title: String, val category: Category, val date: LocalDate, val minutes: Int, val orgName: String)

enum class BookingState { BOOKED, CHECKED_IN, CONFIRMED }
data class Booking(val slotId: String, val state: BookingState, val confirmedMinutes: Int = 0)

data class ChatMessage(val fromMe: Boolean, val text: String, val demo: Boolean = false, val time: LocalDateTime = LocalDateTime.now())

data class Badge(val id: String, val name: String, val rule: String, val icon: ImageVector, val c1: Color, val c2: Color)

data class Level(val n: Int, val name: String, val minHours: Int)
val LEVELS = listOf(
    Level(1, "Newcomer", 0), Level(2, "Helper", 2), Level(3, "Neighbor", 5), Level(4, "Contributor", 10),
    Level(5, "Champion", 20), Level(6, "Pillar", 35), Level(7, "Trailblazer", 50), Level(8, "Luminary", 75),
    Level(9, "Legend", 100), Level(10, "Hometown Hero", 150),
)
fun levelFor(minutes: Int): Triple<Level, Level?, Float> {
    val h = minutes / 60f
    val cur = LEVELS.last { h >= it.minHours }
    val next = LEVELS.firstOrNull { it.minHours > h }
    val p = if (next == null) 1f else ((h - cur.minHours) / (next.minHours - cur.minHours)).coerceIn(0f, 1f)
    return Triple(cur, next, p)
}

const val HOME_LAT = 33.4255
const val HOME_LNG = -111.9400

fun haversineMi(a1: Double, o1: Double, a2: Double, o2: Double): Double {
    val r = 3958.8
    val dLat = Math.toRadians(a2 - a1); val dLon = Math.toRadians(o2 - o1)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a1)) * cos(Math.toRadians(a2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

val BADGES = listOf(
    Badge("first_shift", "First Shift", "Complete your first organizer-confirmed shift.", Icons.Filled.Groups, Coral, CoralWarm),
    Badge("helper", "Community Helper", "Volunteer with 3 different organizations.", Icons.Filled.VolunteerActivism, Cobalt, CobaltBright),
    Badge("hours_10", "10-Hour Milestone", "Reach 10 confirmed volunteer hours.", Icons.Filled.Schedule, Color(0xFF1638A8), Color(0xFF4F7BFF)),
    Badge("lunch", "Lunch Break Hero", "Complete 3 shifts of 15 minutes or less.", Icons.Filled.LunchDining, Color(0xFFE8743B), Gold),
    Badge("green", "Green Guardian", "Complete an environment shift.", Icons.Filled.Eco, Mint, Color(0xFF6FD6A0)),
    Badge("pantry", "Pantry Pal", "Complete a food security shift.", Icons.Filled.Restaurant, Color(0xFFE8743B), CoralWarm),
    Badge("animals", "Animal Ally", "Complete an animal care shift.", Icons.Filled.Pets, Color(0xFFA35BD6), Color(0xFFC792EA)),
    Badge("connector", "Community Connector", "Invite a friend who joins VolunteerDash.", Icons.Filled.Share, Color(0xFF0E9AA7), Color(0xFF4DD0C8)),
    Badge("hours_25", "25-Hour Milestone", "Reach 25 confirmed volunteer hours.", Icons.Filled.EmojiEvents, Gold, Color(0xFFFFD27A)),
    Badge("explorer", "Explorer", "Volunteer in 5 different categories.", Icons.Filled.Explore, Color(0xFF7A5CFA), Color(0xFFA48BFF)),
    Badge("mentor", "Bright Mentor", "Complete an education shift.", Icons.Filled.MenuBook, Color(0xFF1F8FE0), Color(0xFF6CC0FF)),
    Badge("builder", "Home Builder", "Complete a housing shift.", Icons.Filled.Construction, Color(0xFFC98A1B), Gold),
)

object Demo {
    val orgs = listOf(
        Org("pantry", "Tempe Community Pantry", "Maya Torres", "maya@pantry.example.org", "(480) 555-0110"),
        Org("cleanup", "Salt River Cleanup Crew", "Devon Park", "crew@saltriver.example.org", "(480) 555-0121"),
        Org("trails", "Papago Trail Keepers", "Rosa Delgado", "hello@trailkeepers.example.org", "(480) 555-0132"),
        Org("paws", "Desert Paws Rescue", "Sam Whitfield", "walks@desertpaws.example.org", "(480) 555-0143"),
        Org("readers", "Mill Avenue Reading Buddies", "Priya Nair", "read@millreaders.example.org", "(480) 555-0154"),
        Org("youth", "Apache Boulevard Youth Hub", "Marcus Bell", "hub@apacheyouth.example.org", "(480) 555-0165"),
        Org("seniors", "Sunrise Senior Circle", "Helen Okafor", "circle@sunrise.example.org", "(480) 555-0176"),
        Org("wellness", "Valley Blood & Wellness Collective", "Ana Ruiz", "give@valleywellness.example.org", "(480) 555-0187"),
        Org("builders", "Rio Salado Homebuilders", "Grant Lee", "build@riosalado.example.org", "(480) 555-0198"),
        Org("kitchen", "Tempe Neighbors Kitchen", "Lena Brooks", "cook@neighborskitchen.example.org", "(480) 555-0109"),
        Org("garden", "Tempe Green Plots", "Owen Castillo", "grow@greenplots.example.org", "(480) 555-0118"),
        Org("arts", "Desert Bloom Arts Alliance", "Isla Moreno", "art@desertbloom.example.org", "(480) 555-0127"),
    )
    private fun o(id: String) = orgs.first { it.id == id }

    val opportunities = listOf(
        Opportunity("pantry-boxes", "Pack pantry boxes", o("pantry"), Category.FOOD, listOf(R.drawable.opp_pantry_boxes_1, R.drawable.opp_pantry_boxes_2),
            "Escalante Community Center", "2150 E Orange St, Tempe, AZ", 33.4237, -111.9014, 0, "Free",
            "Fill grocery boxes for 120 neighbor families.",
            "Join a friendly packing line that turns donated groceries into balanced family boxes. Each station takes a few minutes to learn, so even a 10-minute visit makes a real dent in the day's list.",
            "Closed-toe shoes. Hair tied back. Water bottle recommended.", "Step-free entrance, seated packing station available.", 5,
            listOf(10, 15, 30), listOf("Lunch-break friendly", "Indoors")),
        Opportunity("pantry-greeters", "Greet pantry guests", o("pantry"), Category.FOOD, listOf(R.drawable.opp_pantry_greeters_1, R.drawable.opp_pantry_greeters_2),
            "Escalante Community Center", "2150 E Orange St, Tempe, AZ", 33.4237, -111.9014, 0, "Free",
            "Welcome families and help them check in.",
            "Be the first friendly face guests see. You'll help with check-in, explain how the pantry works, and walk new visitors to the distribution tables.",
            "Bilingual Spanish speakers especially welcome. Wear comfortable shoes.", "Wheelchair accessible. Chairs available at the welcome desk.", 10,
            listOf(60), listOf("People-facing")),
        Opportunity("lake-litter", "Tempe Town Lake litter sweep", o("cleanup"), Category.ENVIRONMENT, listOf(R.drawable.opp_lake_litter_1, R.drawable.opp_lake_litter_2),
            "Tempe Beach Park", "80 W Rio Salado Pkwy, Tempe, AZ", 33.4318, -111.9425, 0, "Free",
            "Clear trash along the lake path before it reaches the water.",
            "Grab a grabber and a bucket and walk a stretch of the Town Lake path. We supply gloves and bags; you bring the energy. Great after work as the sun goes down.",
            "Sun hat, sunscreen and water. Gloves provided.", "Paved, level path. Benches every 200 ft.", 5,
            listOf(30, 60), listOf("Outdoors", "After work")),
        Opportunity("kayak-cleanup", "Shoreline kayak cleanup", o("cleanup"), Category.ENVIRONMENT, listOf(R.drawable.opp_kayak_cleanup_1),
            "Tempe Town Lake Marina", "550 E Tempe Town Lake, Tempe, AZ", 33.4330, -111.9330, 1000, "$10 kayak rental",
            "Paddle the shoreline and scoop floating debris.",
            "Experienced and first-time paddlers welcome. After a short safety briefing you'll paddle in pairs, collecting floating litter from coves the walking crews can't reach.",
            "Swimwear or quick-dry clothes. Life jackets provided. Must be 16+.", "Requires ability to get in and out of a kayak.", 15,
            listOf(90), listOf("Weekend", "On the water")),
        Opportunity("papago-trail", "Papago trail restoration", o("trails"), Category.ENVIRONMENT, listOf(R.drawable.opp_papago_trail_1),
            "Papago Park", "1802 N College Ave, Tempe, AZ", 33.4560, -111.9450, 0, "Free",
            "Repair erosion and rebuild trail edges among the red buttes.",
            "Help keep Papago's iconic trails safe. Tasks include moving rocks to line paths, raking loose gravel and removing invasive weeds.",
            "Sturdy shoes, long pants, 2 liters of water. Tools provided.", "Uneven terrain. Not wheelchair accessible.", 15,
            listOf(90), listOf("Weekend", "Outdoors")),
        Opportunity("butte-tidy", "Hayden Butte trail tidy", o("trails"), Category.ENVIRONMENT, listOf(R.drawable.opp_butte_tidy_1),
            "Tempe Butte Trailhead", "Mill Ave & 3rd St, Tempe, AZ", 33.4282, -111.9356, 0, "Free",
            "A quick litter loop on 'A' Mountain with downtown views.",
            "A short, satisfying loop: pick up litter on the lower trail of Hayden Butte and enjoy the best view of downtown Tempe.",
            "Closed-toe shoes and water.", "Steep steps. Not wheelchair accessible.", 0,
            listOf(30), listOf("Lunch-break friendly", "Outdoors")),
        Opportunity("dog-walks", "Shelter dog walks", o("paws"), Category.ANIMALS, listOf(R.drawable.opp_dog_walks_1),
            "Desert Paws Rescue", "1350 E Baseline Rd, Tempe, AZ", 33.3780, -111.9260, 0, "Free",
            "Give a shelter pup a walk and some love.",
            "Our dogs wait all day for a walk. Pick a buddy, loop the shaded path, and log notes about their behavior to help them get adopted.",
            "Closed-toe shoes. 5-minute orientation on your first visit.", "Path is paved and flat.", 5,
            listOf(15, 30), listOf("Lunch-break friendly", "Outdoors")),
        Opportunity("kitten-social", "Kitten socializing", o("paws"), Category.ANIMALS, listOf(R.drawable.opp_kitten_social_1),
            "Desert Paws Rescue", "1350 E Baseline Rd, Tempe, AZ", 33.3780, -111.9260, 0, "Free",
            "Play with kittens so they're ready for their forever homes.",
            "Socialized kittens get adopted faster. Spend quiet time playing, brushing and helping them get used to people.",
            "Wash hands on arrival. No perfume please.", "Seated activity, fully accessible.", 5,
            listOf(15), listOf("Lunch-break friendly", "Indoors")),
        Opportunity("reading-buddies", "Read with a second grader", o("readers"), Category.EDUCATION, listOf(R.drawable.opp_reading_buddies_1),
            "Tempe Public Library", "3500 S Rural Rd, Tempe, AZ", 33.3934, -111.9266, 0, "Free",
            "Share a picture book with a young reader.",
            "Partner with a second grader for a half hour of reading out loud. Books and conversation prompts provided.",
            "Background check required before your first session (free).", "Library is fully accessible.", 10,
            listOf(30), listOf("Indoors", "After school")),
        Opportunity("homework-help", "Homework help drop-in", o("youth"), Category.EDUCATION, listOf(R.drawable.opp_homework_help_1),
            "Apache Boulevard Youth Hub", "1700 E Apache Blvd, Tempe, AZ", 33.4148, -111.9097, 0, "Free",
            "Help middle schoolers with math and writing.",
            "Drop in after work and sit with students working through homework. No teaching experience needed, just patience.",
            "Bring a laptop if you have one.", "Ground floor, accessible restroom.", 5,
            listOf(60), listOf("After work")),
        Opportunity("senior-calls", "Friendly check-in calls", o("seniors"), Category.SENIORS, listOf(R.drawable.opp_senior_calls_1),
            "Pyle Adult Recreation Center", "655 E Southern Ave, Tempe, AZ", 33.3929, -111.9305, 0, "Free",
            "Call isolated seniors for a warm chat.",
            "Use our phone room to make short, friendly check-in calls to older neighbors who live alone. A call script is ready for you.",
            "None. A quiet voice and a kind ear.", "Seated, quiet room, fully accessible.", 0,
            listOf(10, 15), listOf("Lunch-break friendly", "Indoors")),
        Opportunity("senior-tech", "Smartphone help for seniors", o("seniors"), Category.SENIORS, listOf(R.drawable.opp_senior_tech_1),
            "Pyle Adult Recreation Center", "655 E Southern Ave, Tempe, AZ", 33.3929, -111.9305, 0, "Free",
            "Help older adults video-call family and stay safe online.",
            "Sit one-on-one with seniors to set up video calls, photos and scam-safe habits.",
            "Familiarity with iPhone and Android.", "Fully accessible.", 5,
            listOf(60), listOf("Indoors")),
        Opportunity("blood-drive", "Blood drive greeter", o("wellness"), Category.HEALTH, listOf(R.drawable.opp_blood_drive_1),
            "Tempe Marketplace Pavilion", "2000 E Rio Salado Pkwy, Tempe, AZ", 33.4296, -111.8995, 0, "Free",
            "Welcome donors and keep the snack table stocked.",
            "Help donors feel comfortable: greet, hand out water and snacks, and watch for anyone feeling light-headed.",
            "Comfortable shoes.", "Accessible pavilion.", 10,
            listOf(60), listOf("Weekend")),
        Opportunity("hygiene-kits", "Assemble hygiene kits", o("wellness"), Category.HEALTH, listOf(R.drawable.opp_hygiene_kits_1),
            "Wellness Collective Hub", "500 W University Dr, Tempe, AZ", 33.4180, -111.9330, 500, "$5 suggested donation",
            "Pack toothbrushes, socks and soap for neighbors in need.",
            "An easy assembly line producing kits for local shelters. Your optional donation covers materials for 4 kits.",
            "None.", "Seated tables available.", 0,
            listOf(15, 30), listOf("Lunch-break friendly", "Indoors")),
        Opportunity("build-day", "Neighborhood build day", o("builders"), Category.HOUSING, listOf(R.drawable.opp_build_day_1),
            "Rio Salado Build Site", "1100 W 1st St, Tempe, AZ", 33.4120, -111.9550, 0, "Free",
            "Frame walls for an affordable family home.",
            "Work alongside skilled crew leads framing walls and installing siding. A safety briefing is required before starting.",
            "Closed-toe boots, long pants. Hard hats provided.", "Active construction site.", 15,
            listOf(90), listOf("Weekend", "Outdoors")),
        Opportunity("fence-paint", "Paint a neighbor's fence", o("builders"), Category.HOUSING, listOf(R.drawable.opp_fence_paint_1),
            "Maple-Ash Neighborhood", "W Broadway Rd, Tempe, AZ", 33.4010, -111.9480, 0, "Free",
            "Refresh a fence for a senior homeowner.",
            "Bring some cheer to a neighborhood block by repainting fences for seniors who can't do it themselves.",
            "Clothes you don't mind getting paint on.", "Outdoors, grass and gravel.", 5,
            listOf(60), listOf("Weekend")),
        Opportunity("dinner-prep", "Community dinner prep", o("kitchen"), Category.FOOD, listOf(R.drawable.opp_dinner_prep_1),
            "Neighbors Kitchen", "21 E 6th St, Tempe, AZ", 33.4255, -111.9385, 0, "Free",
            "Chop, stir and plate a hot meal for 80 guests.",
            "Our kitchen serves a free community dinner every weeknight. Help prep vegetables, serve and clean up.",
            "Hair tie, closed-toe shoes. Food handler card not required.", "Kitchen is step-free.", 10,
            listOf(60, 90), listOf("After work", "Indoors")),
        Opportunity("sandwich-line", "Lunch-break sandwich line", o("kitchen"), Category.FOOD, listOf(R.drawable.opp_sandwich_line_1),
            "Neighbors Kitchen", "21 E 6th St, Tempe, AZ", 33.4255, -111.9385, 0, "Free",
            "Make 20 sandwiches in 15 minutes.",
            "Our fastest volunteer gig: join the sandwich line during lunch. Every sandwich goes to a neighbor that evening.",
            "Wash hands, gloves provided.", "Standing or seated stations.", 0,
            listOf(10, 15), listOf("Lunch-break friendly", "Indoors")),
        Opportunity("garden-water", "Water the Kiwanis garden plots", o("garden"), Category.ENVIRONMENT, listOf(R.drawable.opp_garden_water_1),
            "Kiwanis Park Community Garden", "6111 S All-America Way, Tempe, AZ", 33.3727, -111.9387, 0, "Free",
            "Keep the shared vegetable beds alive in the desert heat.",
            "Water and check the shared plots. Harvest is donated to the Community Pantry.",
            "Hat and water.", "Raised beds reachable from a wheelchair.", 0,
            listOf(15), listOf("Lunch-break friendly", "Outdoors")),
        Opportunity("seed-library", "Sort seed library packets", o("garden"), Category.ENVIRONMENT, listOf(R.drawable.opp_seed_library_1),
            "Tempe Public Library", "3500 S Rural Rd, Tempe, AZ", 33.3934, -111.9266, 0, "Free",
            "Label and sort seed packets for home gardeners.",
            "A calm, seated task: sort donated seeds into labeled packets for the library's free seed library.",
            "None.", "Fully accessible, seated.", 0,
            listOf(10, 15), listOf("Lunch-break friendly", "Indoors")),
        Opportunity("mural", "Help paint a community mural", o("arts"), Category.ARTS, listOf(R.drawable.opp_mural_1),
            "Tempe Center for the Arts", "700 W Rio Salado Pkwy, Tempe, AZ", 33.4325, -111.9479, 0, "Free",
            "Fill in color on a 60-foot lakeside mural.",
            "Local artists have sketched the design; volunteers fill in the color blocks. No art skills needed.",
            "Old clothes. Aprons provided.", "Ground-level work available.", 10,
            listOf(60), listOf("Weekend", "Outdoors")),
        Opportunity("concert-ushers", "Usher a free lakeside concert", o("arts"), Category.ARTS, listOf(R.drawable.opp_concert_ushers_1),
            "Tempe Beach Park Stage", "80 W Rio Salado Pkwy, Tempe, AZ", 33.4310, -111.9440, 0, "Free",
            "Guide guests and hand out programs at sunset.",
            "Help the free Friday concert series run smoothly: greet guests, hand out programs and point folks to seating.",
            "Wear a bright shirt. Volunteer lanyard provided.", "Paved paths.", 20,
            listOf(90), listOf("Weekend", "Evening")),
    )

    private val p = listOf(R.drawable.portrait_p01, R.drawable.portrait_p02, R.drawable.portrait_p03, R.drawable.portrait_p04,
        R.drawable.portrait_p05, R.drawable.portrait_p06, R.drawable.portrait_p07, R.drawable.portrait_p08)
    private val tints = listOf(Color(0xFFC7D7FE), Color(0xFFFFD9CF), Color(0xFFC9F2E1), Color(0xFFFDE7B0), Color(0xFFE5D9FF))

    val people: List<Person> = run {
        val named = listOf(
            Triple("Diego Morales", p[0], 34), Triple("Lukas Brandt", p[1], 29), Triple("Nicole Ellis", p[2], 41),
            Triple("Tom Halvorsen", p[3], 38), Triple("Ravi Kumar", p[4], 27), Triple("Clara Novak", p[5], 22),
            Triple("Ethan Wright", p[6], 31), Triple("Sofia Alvarez", null, 36),
        )
        val others = listOf("Aisha Bello", "Kenji Watanabe", "Grace Kim", "Malik Johnson", "Hannah Schultz", "Omar Haddad",
            "Lucía Fernández", "Ben Carter", "Mei Lin", "Jamal Robinson", "Fatima Zahra", "Noah Becker", "Ivy Tran",
            "Carlos Mendoza", "Zoe Mitchell", "Arjun Patel", "Leah Goldberg", "Mateo Silva", "Nora Lindqvist",
            "Samuel Osei", "Tessa Brooks", "Hiro Sato", "Amara Nwosu", "Riley Chen", "Elena Petrova", "Jonah Rivera")
        val rnd = Random(7)
        val cats = Category.entries
        named.mapIndexed { i, (n, por, hrs) ->
            Person("u$i", n, por, tints[i % tints.size], hrs, listOf(310, 285, 285, 240, 215, 190, 175, 160)[i],
                listOf(4, 3, 0, 2, 0, 1, 0, 5)[i], listOf(cats[i % 8], cats[(i + 3) % 8]),
                "Tempe local who likes ${cats[i % 8].label.lowercase()} projects and short, useful shifts.",
                BADGES.shuffled(Random(i)).take(4).map { it.id })
        } + others.mapIndexed { j, n ->
            val i = j + 8
            Person("u$i", n, null, tints[i % tints.size], 3 + rnd.nextInt(30), max(0, 150 - j * 9 + rnd.nextInt(10)),
                if (j % 7 == 0) 1 else 0, listOf(cats[i % 8]), "Volunteering around Tempe on weekends.",
                BADGES.shuffled(Random(i)).take(1 + j % 4).map { it.id }, isPrivate = j == 5 || j == 17)
        }
    }

    val me = Person("me", "Jordan Reyes", R.drawable.portrait_p08, Color(0xFFFFD9CF), 0, 0, 0,
        listOf(Category.FOOD, Category.ENVIRONMENT, Category.ANIMALS), "Product designer, Tempe local, dog person.", emptyList())

    val pastShifts: List<PastShift> = run {
        val today = LocalDate.now()
        listOf(
            PastShift("Pack pantry boxes", Category.FOOD, today.minusDays(3), 30, "Tempe Community Pantry"),
            PastShift("Tempe Town Lake litter sweep", Category.ENVIRONMENT, today.minusDays(6), 60, "Salt River Cleanup Crew"),
            PastShift("Shelter dog walks", Category.ANIMALS, today.minusDays(9), 30, "Desert Paws Rescue"),
            PastShift("Lunch-break sandwich line", Category.FOOD, today.minusDays(12), 15, "Tempe Neighbors Kitchen"),
            PastShift("Community dinner prep", Category.FOOD, today.minusDays(16), 90, "Tempe Neighbors Kitchen"),
            PastShift("Papago trail restoration", Category.ENVIRONMENT, today.minusDays(20), 90, "Papago Trail Keepers"),
            PastShift("Pack pantry boxes", Category.FOOD, today.minusDays(24), 15, "Tempe Community Pantry"),
            PastShift("Kitten socializing", Category.ANIMALS, today.minusDays(27), 15, "Desert Paws Rescue"),
            PastShift("Greet pantry guests", Category.FOOD, today.minusDays(33), 60, "Tempe Community Pantry"),
            PastShift("Usher a free lakeside concert", Category.ARTS, today.minusDays(38), 90, "Desert Bloom Arts Alliance"),
            PastShift("Neighborhood build day", Category.HOUSING, today.minusDays(45), 90, "Rio Salado Homebuilders"),
            PastShift("Tempe Town Lake litter sweep", Category.ENVIRONMENT, today.minusDays(52), 60, "Salt River Cleanup Crew"),
            PastShift("Sort seed library packets", Category.ENVIRONMENT, today.minusDays(58), 15, "Tempe Green Plots"),
            PastShift("Hayden Butte trail tidy", Category.ENVIRONMENT, today.minusDays(63), 30, "Papago Trail Keepers"),
            PastShift("Lunch-break sandwich line", Category.FOOD, today.minusDays(68), 15, "Tempe Neighbors Kitchen"),
        )
    }

    val slots: List<Slot> = run {
        val rnd = Random(42)
        val today = LocalDate.now()
        val familiar = people.filter { it.shared > 0 }.map { it.id }
        val all = people.map { it.id }
        val out = mutableListOf<Slot>()
        opportunities.forEachIndexed { oi, op ->
            for (d in 0..27) {
                val date = today.plusDays(d.toLong())
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                val long = op.durations.min() >= 60
                val runsToday = if (long) (weekend || (d + oi) % 3 == 0) else !weekend || oi % 2 == 0
                if (!runsToday) continue
                val times = when {
                    long && weekend -> listOf(LocalTime.of(8, 0), LocalTime.of(10, 30))
                    long -> listOf(LocalTime.of(17, 30))
                    else -> listOf(LocalTime.of(12, 0), LocalTime.of(12, 15), LocalTime.of(17, 45))
                }
                times.forEachIndexed { ti, t ->
                    val dur = op.durations[(d + ti) % op.durations.size]
                    val cap = if (long) 8 + rnd.nextInt(6) else 3 + rnd.nextInt(5)
                    val full = rnd.nextInt(9) == 0
                    val booked = if (full) cap else rnd.nextInt(cap)
                    val att = buildList {
                        if (booked > 0 && rnd.nextInt(3) == 0) add(familiar[rnd.nextInt(familiar.size)])
                        while (size < min(booked, 5)) { val x = all[rnd.nextInt(all.size)]; if (x !in this) add(x) }
                    }
                    out += Slot("${op.id}-$d-$ti", op.id, date.atTime(t), dur, cap, booked, att, cancelled = rnd.nextInt(40) == 0)
                }
            }
        }
        for (offset in -30..7) {
            val day = today.plusDays(offset.toLong())
            listOf("sandwich-line" to 25, "pantry-boxes" to 35).forEach { (oppId, returnMinute) ->
                val op = opportunities.first { it.id == oppId }
                val start = day.atTime(12, 0).plusMinutes((returnMinute - 15 - op.travelMin).toLong())
                out += Slot("fit-$day-$oppId", oppId, start, 15, 5, 1, listOf("u0"))
            }
        }
        out.sortedBy { it.start }
    }

    fun opp(id: String) = opportunities.first { it.id == id }
    fun slot(id: String) = slots.firstOrNull { it.id == id }
    fun person(id: String) = if (id == "me") me else people.firstOrNull { it.id == id }
}

/** App-wide prototype state, persisted to SharedPreferences. */
object Store {
    var signedIn by mutableStateOf(false)
    var myName by mutableStateOf("Jordan Reyes")
    var myPhoto by mutableStateOf<String?>(null)
    var guideSeen by mutableStateOf(false)
    val bookings = mutableStateMapOf<String, Booking>()
    val events = mutableStateListOf<PersonalEvent>()
    val chats = mutableStateMapOf<String, List<ChatMessage>>()
    var referrals by mutableIntStateOf(0)
    var celebrate by mutableStateOf<Badge?>(null)
    var toast by mutableStateOf<String?>(null)
    val interests = mutableStateListOf(Category.FOOD, Category.ENVIRONMENT, Category.ANIMALS)
    var travelBuffer by mutableIntStateOf(0)

    private lateinit var prefs: android.content.SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("vd", Context.MODE_PRIVATE)
        signedIn = prefs.getBoolean("signedIn", false)
        myName = prefs.getString("name", "Jordan Reyes")!!
        myPhoto = prefs.getString("photo", null)
        guideSeen = prefs.getBoolean("guide", false)
        referrals = prefs.getInt("refs", 0)
        prefs.getStringSet("bookings", emptySet())!!.forEach {
            val parts = it.split("|")
            if (parts.size == 3 && Demo.slot(parts[0]) != null)
                bookings[parts[0]] = Booking(parts[0], BookingState.valueOf(parts[1]), parts[2].toInt())
        }
        val ev = prefs.getStringSet("events", null)
        if (ev == null) seedEvents() else ev.forEach { s ->
            val f = s.split("\u001F")
            if (f.size >= 7) events += PersonalEvent(f[0], f[1], LocalDate.parse(f[2]), f[3].toInt(), f[4].toInt(), f[5].toBoolean(), f[6], f.getOrElse(7) { "manual" })
        }
        if (bookings.isEmpty() && !prefs.getBoolean("seededBookings", false)) {
            // Two upcoming bookings so the demo starts lively.
            Demo.slots.firstOrNull { it.oppId == "pantry-boxes" && it.start.isAfter(LocalDateTime.now().plusHours(20)) && !it.cancelled && it.baseBooked < it.capacity }
                ?.let { bookings[it.id] = Booking(it.id, BookingState.BOOKED) }
            Demo.slots.firstOrNull { it.oppId == "lake-litter" && it.start.isAfter(LocalDateTime.now().plusDays(3)) && !it.cancelled && it.baseBooked < it.capacity }
                ?.let { bookings[it.id] = Booking(it.id, BookingState.BOOKED) }
            prefs.edit().putBoolean("seededBookings", true).apply()
        }
        save()
    }

    private fun seedEvents() {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        for (i in 0..13) {
            val d = monday.plusDays(i.toLong())
            if (d.dayOfWeek.value <= 5) {
                events += PersonalEvent("work-$i", "Work (morning)", d, 8 * 60 + 30, 195, true, "Office", "ics")
                events += PersonalEvent("workpm-$i", "Work (afternoon)", d, 13 * 60 + 15, 225, true, "Office", "ics")
                if (d.dayOfWeek == DayOfWeek.TUESDAY || d.dayOfWeek == DayOfWeek.THURSDAY)
                    events += PersonalEvent("gym-$i", "Gym", d, 18 * 60 + 30, 60, true)
            }
        }
        events += PersonalEvent("dinner", "Dinner with Sam", monday.plusDays(5), 19 * 60, 120, true, "Mill Ave")
    }

    fun save() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putBoolean("signedIn", signedIn).putString("name", myName).putString("photo", myPhoto)
            .putBoolean("guide", guideSeen).putInt("refs", referrals)
            .putStringSet("bookings", bookings.values.map { "${it.slotId}|${it.state}|${it.confirmedMinutes}" }.toSet())
            .putStringSet("events", events.map { listOf(it.id, it.title, it.date, it.startMin, it.minutes, it.busy, it.notes, it.source).joinToString("\u001F") }.toSet())
            .apply()
    }

    // ---- derived progress ----
    val confirmedFromBookings get() = bookings.values.filter { it.state == BookingState.CONFIRMED }
    val totalMinutes get() = Demo.pastShifts.sumOf { it.minutes } + confirmedFromBookings.sumOf { it.confirmedMinutes }
    val shiftCount get() = Demo.pastShifts.size + confirmedFromBookings.size
    val weekMinutes: Int get() {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return Demo.pastShifts.filter { !it.date.isBefore(monday) }.sumOf { it.minutes } +
            confirmedFromBookings.filter { Demo.slot(it.slotId)!!.start.toLocalDate() >= monday }.sumOf { it.confirmedMinutes }
    }

    fun earnedBadges(): Set<String> {
        val cats = Demo.pastShifts.map { it.category }.toMutableSet()
        val shorts = Demo.pastShifts.count { it.minutes <= 15 } + confirmedFromBookings.count { it.confirmedMinutes <= 15 }
        confirmedFromBookings.forEach { cats += Demo.opp(Demo.slot(it.slotId)!!.oppId).category }
        val orgs = Demo.pastShifts.map { it.orgName }.toSet()
        return buildSet {
            if (shiftCount >= 1) add("first_shift")
            if (orgs.size >= 3) add("helper")
            if (totalMinutes >= 600) add("hours_10")
            if (totalMinutes >= 1500) add("hours_25")
            if (shorts >= 3) add("lunch")
            if (Category.ENVIRONMENT in cats) add("green")
            if (Category.FOOD in cats) add("pantry")
            if (Category.ANIMALS in cats) add("animals")
            if (Category.EDUCATION in cats) add("mentor")
            if (Category.HOUSING in cats) add("builder")
            if (cats.size >= 5) add("explorer")
            if (referrals >= 1) add("connector")
        }
    }

    fun spotsLeft(s: Slot) = s.capacity - s.baseBooked - (if (bookings.containsKey(s.id)) 1 else 0)

    fun book(s: Slot): String? {
        if (s.cancelled) return "This shift was cancelled by the organizer."
        if (s.start.isBefore(LocalDateTime.now())) return "This shift has already started."
        if (bookings.containsKey(s.id)) return null // idempotent
        if (spotsLeft(s) <= 0) return "This shift just filled up. Pick another time."
        val clash = bookings.keys.mapNotNull { Demo.slot(it) }.firstOrNull { it.start < s.end && s.start < it.end }
        if (clash != null) return "Overlaps your shift “${Demo.opp(clash.oppId).title}”."
        bookings[s.id] = Booking(s.id, BookingState.BOOKED); save(); return null
    }

    fun cancel(slotId: String) { bookings.remove(slotId); save() }

    fun confirm(slotId: String, minutes: Int) {
        val before = earnedBadges()
        bookings[slotId] = Booking(slotId, BookingState.CONFIRMED, minutes); save()
        val newOnes = earnedBadges() - before
        newOnes.firstOrNull()?.let { id -> celebrate = BADGES.first { it.id == id } }
    }

    fun addReferral() {
        val before = earnedBadges(); referrals++; save()
        (earnedBadges() - before).firstOrNull()?.let { id -> celebrate = BADGES.first { it.id == id } }
    }

    /** Busy personal events overlapping slot window (with travel + arrival buffer). */
    fun conflictFor(s: Slot): PersonalEvent? {
        val op = Demo.opp(s.oppId)
        val pad = (op.travelMin + travelBuffer).toLong()
        val from = s.start.minusMinutes(pad + op.arrivalLead); val to = s.end.plusMinutes(pad)
        return events.firstOrNull { it.busy && it.start < to && from < it.end }
    }
}
