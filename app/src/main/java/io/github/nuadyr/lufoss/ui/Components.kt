package io.github.nuadyr.lufoss.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.data.Place
import io.github.nuadyr.lufoss.data.Route
import io.github.nuadyr.lufoss.data.arrowFor
import io.github.nuadyr.lufoss.data.fmtDist
import io.github.nuadyr.lufoss.data.instruction
import io.github.nuadyr.lufoss.geo.GeoMath
import io.github.nuadyr.lufoss.nav.NavUi
import kotlin.math.abs
import kotlin.math.roundToInt

private val fabSize = 48.dp
private val cardShape = RoundedCornerShape(14.dp)

/** A round button floating over the map. */
@Composable
fun MapFab(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(fabSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Shows which way north is. Only appears while the map is turned, or while walking. */
@Composable
fun CompassFab(vm: LufossViewModel, modifier: Modifier = Modifier) {
    val bearing = vm.map.bearing
    val turned = abs(GeoMath.angDiff(0.0, bearing)) > Cfg.COMPASS_SHOW_DEG
    if (!turned && !vm.navActive) return
    val extras = LocalLufossColors.current
    val south = MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (turned) {
        "Compass. The map is turned ${bearing.roundToInt()} degrees. Press to put north at the top."
    } else {
        "Compass. North is at the top."
    }
    Surface(
        modifier = modifier.size(fabSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        IconButton(
            onClick = vm::onCompass,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = label }
        ) {
            Canvas(Modifier.size(28.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val half = size.width * NEEDLE_HALF_WIDTH
                rotate(degrees = (-bearing).toFloat(), pivot = c) {
                    val north = Path().apply {
                        moveTo(c.x, size.height * NEEDLE_TIP)
                        lineTo(c.x + half, c.y)
                        lineTo(c.x - half, c.y)
                        close()
                    }
                    val southPath = Path().apply {
                        moveTo(c.x, size.height * (1 - NEEDLE_TIP))
                        lineTo(c.x + half, c.y)
                        lineTo(c.x - half, c.y)
                        close()
                    }
                    drawPath(southPath, south)
                    drawPath(north, extras.pin)
                }
            }
        }
    }
}

private const val NEEDLE_HALF_WIDTH = 0.18f
private const val NEEDLE_TIP = 0.06f

/** A chip that is either on or off, with a tick when on. */
@Composable
fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = LufossIcons.check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}

/** The panel at the bottom of the screen (or the left on a wide screen). */
@Composable
fun SheetSurface(modifier: Modifier, wide: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = if (wide) cardShape else RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** A small floating card, for the permission card and the pick-on-map card. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** The list under a search box. */
@Composable
fun SuggestionList(state: Suggest, onPick: (Place) -> Unit, modifier: Modifier = Modifier, maxHeight: Dp = 320.dp) {
    if (state is Suggest.Hidden) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        when (state) {
            Suggest.Hidden -> Unit
            Suggest.Searching -> ListMessage("Searching…")
            is Suggest.Hint -> ListMessage(state.text)
            is Suggest.Empty -> ListMessage(state.text)
            is Suggest.Found -> LazyColumn(Modifier.heightIn(max = maxHeight)) {
                if (!state.note.isNullOrEmpty()) item { ListMessage(state.note) }
                items(state.places) { place -> PlaceRow(place, onPick) }
            }
        }
    }
}

@Composable
private fun ListMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(14.dp)
    )
}

@Composable
private fun PlaceRow(place: Place, onPick: (Place) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(place) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(place.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (place.addr.isNotBlank()) {
            Text(
                text = place.addr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (place.approx) {
            Text(
                text = "Street only; house number not verified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The turn banner shown while walking. */
@Composable
fun NavBanner(ui: NavUi, modifier: Modifier = Modifier) {
    val extras = LocalLufossColors.current
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = extras.brand,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = ui.arrow.ifEmpty { "↑" }, fontSize = 36.sp, color = extras.onBrand)
            Column {
                Text(
                    text = ui.text.ifEmpty { "Starting navigation" },
                    style = MaterialTheme.typography.titleMedium,
                    color = extras.onBrand
                )
                if (ui.dist.isNotEmpty()) {
                    Text(text = ui.dist, style = MaterialTheme.typography.bodyMedium, color = extras.onBrandSoft)
                }
            }
        }
    }
}

/** The list of steps. Steps already done are marked, and the next one is scrolled into view while walking. */
@Composable
fun StepList(route: Route, doneUpTo: Int, nextIdx: Int?, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(nextIdx) {
        if (nextIdx != null) {
            listState.animateScrollToItem(nextIdx.coerceIn(0, route.steps.lastIndex.coerceAtLeast(0)))
        }
    }
    LazyColumn(state = listState, modifier = modifier.heightIn(max = 240.dp)) {
        itemsIndexed(route.steps) { i, step ->
            val done = i <= doneUpTo
            val next = nextIdx != null && i == nextIdx
            val dim = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .semantics(mergeDescendants = true) {
                        val state = if (done) {
                            "Done"
                        } else if (next) {
                            "Next"
                        } else {
                            ""
                        }
                        if (state.isNotEmpty()) stateDescription = state
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(arrowFor(step), fontSize = 22.sp, color = dim, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = instruction(step, route.loop),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (next) FontWeight.Bold else FontWeight.Normal,
                        color = dim
                    )
                    if (step.type != "arrive") {
                        Text(
                            text = fmtDist(step.d),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
