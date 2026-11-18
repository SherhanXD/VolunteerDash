package com.volunteerdash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalDate

data class ShiftFit(val slot: Slot, val travel: Int, val checkIn: Int) {
    val leave = slot.start.minusMinutes((travel + checkIn).toLong())
    val back = slot.end.plusMinutes(travel.toLong())
}

@Composable
fun ShiftFitScreen(nav: Nav) {
    var budget by rememberSaveable { mutableIntStateOf(30) }
    val day = LocalDate.now().plusDays(1)
    val breakStart = day.atTime(12, 0)
    val deadline = breakStart.plusMinutes(budget.toLong())
    val options = Demo.slots.filter { it.id.startsWith("fit-$day-") }.map { slot ->
        val op = Demo.opp(slot.oppId)
        ShiftFit(slot, op.travelMin + Store.travelBuffer, op.arrivalLead)
    }.sortedBy { it.back }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        BackBar("Find a shift that fits", nav)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your lunch break", style = MaterialTheme.typography.headlineSmall)
            Text("${day.d()}  •  ${breakStart.t()} to ${deadline.t()}  •  Free shifts")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 30, 45).forEach { minutes ->
                    FilterChip(budget == minutes, { budget = minutes }, label = { Text("$minutes min") })
                }
            }
            Text("Sample Tempe shifts. Travel times are estimates.", color = Slate,
                style = MaterialTheme.typography.bodySmall)
            options.forEach { fit ->
                val op = Demo.opp(fit.slot.oppId)
                val late = Duration.between(deadline, fit.back).toMinutes()
                val booked = Store.bookings.containsKey(fit.slot.id)
                val clash = Store.conflictFor(fit.slot)
                val elapsed = Duration.between(breakStart, fit.back).toMinutes()
                val overlapsBooking = Store.bookings.keys.any { id ->
                    id != fit.slot.id && Demo.slot(id)?.let { it.start < fit.back && fit.leave < it.end } == true
                }
                val fits = !booked && Store.spotsLeft(fit.slot) > 0 && fit.leave >= breakStart &&
                    late <= 0 && clash == null && !overlapsBooking
                val status = when {
                    booked -> "Already booked"
                    Store.spotsLeft(fit.slot) <= 0 -> "Shift is full"
                    fit.leave < breakStart -> "You'd need to leave before your break"
                    late > 0 -> "You'd get back $late min late"
                    clash != null -> "Conflicts with ${clash.title}"
                    overlapsBooking -> "Conflicts with another booking"
                    else -> "Fits · Back by ${fit.back.t()}"
                }

                Card(Modifier.fillMaxWidth()) {
                    Text(op.title, style = MaterialTheme.typography.titleLarge)
                    Text(status, color = if (fits) Cobalt else Slate,
                        style = MaterialTheme.typography.titleMedium)
                    Text("$elapsed min from break start to return", style = MaterialTheme.typography.labelLarge)
                    Text("${fit.travel * 2} min round trip + ${fit.checkIn} min check-in + ${fit.slot.minutes} min helping")
                    val beforeLeaving = Duration.between(breakStart, fit.leave).toMinutes()
                    if (beforeLeaving > 0) Text("$beforeLeaving min before you need to leave",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Leave ${fit.leave.t()} · Shift ${fit.slot.start.t()} · Back ${fit.back.t()}",
                        style = MaterialTheme.typography.bodySmall)
                    if (fits) Text(op.accessibility, style = MaterialTheme.typography.bodySmall)
                    if (fits || booked) {
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(if (booked) "View booking" else "View shift and sign up",
                            onClick = { nav.go(Route.Detail(op.id, fit.slot.id)) })
                    }
                }
            }
        }
    }
}

