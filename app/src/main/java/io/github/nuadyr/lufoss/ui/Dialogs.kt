package io.github.nuadyr.lufoss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private const val ABOUT_USE = "Lufoss is a small map that finds a walking route and talks you along it. " +
    "The map is drawn by the app itself, with no mapping library. Drag to move, pinch to zoom, " +
    "twist two fingers to turn the map, double-tap to zoom in, and tap the map to drop a pin. " +
    "The round-trip button plans a walk of 10 to 60 minutes that starts and ends at your saved start point."

private const val ABOUT_WALK = "While you walk, the map follows you, turns to the way you are facing and zooms in " +
    "for turns. Drag the map to look around and it comes back to you after a few seconds. " +
    "Preview walks the route by itself so you can try it without GPS."

private const val ABOUT_DATA = "Map pictures come from the OpenStreetMap standard layer. Place suggestions use " +
    "Photon, exact address searches use Nominatim, numbered-building lookups use OpenStreetMap Overpass, and " +
    "walking routes use the OpenStreetMap Germany routing service. Search words and route end points are sent " +
    "to those services. GPS is used only when you choose it as your start point or press Start. " +
    "A chosen start point is saved on this device; live tracking positions are not saved."

private const val FIX_THE_MAP_URL = "https://www.openstreetmap.org/fixthemap"

/** Choose where walks begin: search, GPS, or a spot on the map. */
@Composable
fun StartDialog(vm: LufossViewModel) {
    if (!vm.startDialog) return
    Dialog(onDismissRequest = vm::dismissStart) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Set your start point",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalLufossColors.current.heading
                )
                Text(
                    text = "Your walks begin here. Search for an address, use your GPS position, " +
                        "or pick a spot on the map. It is saved on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                vm.start?.let { saved ->
                    Text(
                        text = "Current start: ${saved.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = vm.startSearch.query,
                    onValueChange = vm.startSearch::onQueryChange,
                    label = { Text("Search for your start address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.startSearch.submit() }),
                    trailingIcon = {
                        IconButton(onClick = vm.startSearch::submit) {
                            Icon(LufossIcons.search, contentDescription = "Search")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                SuggestionList(vm.startSearch.state, vm::pickStartSuggestion, maxHeight = 220.dp)
                if (vm.startStatus.isNotEmpty()) {
                    Text(
                        text = vm.startStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.dialogError.isNotEmpty()) ErrorText(vm.dialogError)
                Button(onClick = vm::useGpsForStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Use my GPS location")
                }
                OutlinedButton(onClick = vm::chooseStartOnMap, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose on map")
                }
                TextButton(onClick = vm::dismissStart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.start == null) "Not now" else "Cancel")
                }
            }
        }
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "Error: $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive }
    )
}

/** What the app is, how to use it, and which free services it talks to. */
@Composable
fun AboutDialog(vm: LufossViewModel) {
    if (!vm.aboutOpen) return
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { vm.aboutOpen = false },
        title = { Text("About Lufoss") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(ABOUT_USE, style = MaterialTheme.typography.bodyMedium)
                Text(ABOUT_WALK, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = ABOUT_DATA,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { uriHandler.openUri(FIX_THE_MAP_URL) }) {
                    Text("Report or correct a map issue")
                }
                Text(
                    text = "Lufoss by Nuadyr. © 2026.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.aboutOpen = false }) { Text("Close") } }
    )
}
