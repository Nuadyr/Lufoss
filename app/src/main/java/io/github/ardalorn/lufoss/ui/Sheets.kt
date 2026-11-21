package io.github.ardalorn.lufoss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.data.Place
import io.github.ardalorn.lufoss.data.Route
import io.github.ardalorn.lufoss.data.fmtDist
import io.github.ardalorn.lufoss.data.fmtTime
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun SheetTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = LocalLufossColors.current.heading)
}

@Composable
private fun SheetHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A place was chosen: offer directions. */
@Composable
fun PlaceSheet(vm: LufossViewModel, dest: Place) {
    SheetTitle(dest.name)
    if (dest.addr.isNotBlank()) SheetHint(dest.addr)
    if (vm.planning) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        SheetHint("Finding a walking route…")
    }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::directions, enabled = !vm.planning, modifier = Modifier.weight(1f)) {
            Text("Directions")
        }
        OutlinedButton(onClick = vm::closeSheet, modifier = Modifier.weight(1f)) { Text("Close") }
    }
}

/** Pick how long a round-trip walk should take. */
@Composable
fun LoopSheet(vm: LufossViewModel) {
    SheetTitle("Round-trip walk")
    val from = vm.start?.label.orEmpty()
    SheetHint("From $from. Pick how long to walk. The route brings you back to the same spot in about that time.")
    if (vm.planning) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        SheetHint("Planning your walk. This can take a few seconds.")
    }
    Cfg.loopMinutes.chunked(LOOP_BUTTONS_PER_ROW).forEach { row ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            row.forEach { minutes ->
                OutlinedButton(
                    onClick = { vm.runLoop(minutes) },
                    enabled = !vm.planning,
                    modifier = Modifier.weight(1f)
                ) { Text("$minutes min") }
            }
        }
    }
    OutlinedButton(onClick = vm::closeSheet, modifier = Modifier.fillMaxWidth()) { Text("Close") }
}

private const val LOOP_BUTTONS_PER_ROW = 3

/** The planned route: time, distance, and what to do with it. */
@Composable
fun PlanSheet(vm: LufossViewModel, route: Route, dest: Place) {
    val time = fmtTime(route.dur)
    val dist = fmtDist(route.dist)
    SheetTitle(if (route.loop) "$time round trip ($dist)" else "$time ($dist) walking")
    SheetHint(
        if (route.loop) {
            "Leaves and returns to ${dest.name}. You chose ${route.minutes} min." + loopNote(route)
        } else {
            "From ${route.from} to ${dest.name}"
        }
    )
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.startWalk(false) }, modifier = Modifier.weight(1f)) { Text("Start") }
        OutlinedButton(onClick = { vm.startWalk(true) }, modifier = Modifier.weight(1f)) { Text("Preview") }
        ToggleChip("Steps", vm.stepsOpen, { vm.stepsOpen = !vm.stepsOpen })
    }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        if (route.loop) {
            OutlinedButton(onClick = vm::anotherLoop, modifier = Modifier.weight(1f)) { Text("Another route") }
        }
        OutlinedButton(onClick = vm::closeSheet, modifier = Modifier.weight(1f)) { Text("Close") }
    }
    if (vm.stepsOpen) StepList(route, doneUpTo = -1, nextIdx = null)
}

/** Says when a round trip doubles back over itself or misses the chosen time by more than a minute. */
private fun loopNote(route: Route): String {
    val back = if (route.overlap > Cfg.LOOP_CLEAN_OVERLAP) {
        " No loop without doubling back was found from here, so part of this walk covers the same street twice."
    } else {
        ""
    }
    val gap = ((route.dur - route.minutes * SECONDS_PER_MINUTE) / SECONDS_PER_MINUTE).roundToInt()
    val off = if (abs(gap) > 1) {
        " The closest loop found is ${abs(gap)} min " + (if (gap > 0) "longer" else "shorter") + "."
    } else {
        ""
    }
    return back + off
}

private const val SECONDS_PER_MINUTE = 60.0

/** While walking: time left, voice and steps switches, and the End button. */
@Composable
fun WalkingSheet(vm: LufossViewModel) {
    val ui = vm.navUi
    val route = vm.route
    if (ui.eta.isNotEmpty()) SheetTitle(ui.eta)
    if (ui.etaDetail.isNotEmpty()) SheetHint(ui.etaDetail)
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        ToggleChip("Voice", vm.voice, vm::toggleVoice)
        ToggleChip("Steps", vm.stepsOpen, { vm.stepsOpen = !vm.stepsOpen })
        Button(onClick = vm::endWalk, modifier = Modifier.weight(1f)) { Text("End") }
    }
    if (vm.stepsOpen && route != null) StepList(route, doneUpTo = ui.idx, nextIdx = ui.idx + 1)
}

@Composable
fun ArrivedSheet(vm: LufossViewModel, name: String) {
    SheetTitle("You have arrived")
    if (name.isNotBlank()) SheetHint(name)
    Button(onClick = vm::closeSheet, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}
