package io.github.ardalorn.lufoss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.map.MapController
import kotlin.math.min

private const val MAP_DESCRIPTION = "Map. Drag to move, pinch to zoom, twist two fingers to turn. " +
    "With a keyboard, arrow keys move the map, plus and minus zoom, " +
    "Shift with left or right arrow turns it, and N points north up."

/** Runs the camera's animations, one frame at a time, only while something is moving. */
@Composable
fun MapFrameLoop(map: MapController) {
    LaunchedEffect(map) {
        while (true) {
            map.frames.receive()
            var last = withFrameMillis { it }
            var busy = true
            while (busy) {
                val now = withFrameMillis { it }
                busy = map.frame(now, min(Cfg.MAX_FRAME_MS, now - last))
                last = now
            }
        }
    }
}

/** The map itself: our own tile engine drawn on a canvas, with touch, mouse, keyboard and screen reader input. */
@Composable
fun MapView(vm: LufossViewModel, modifier: Modifier = Modifier) {
    val map = vm.map
    val density = LocalDensity.current.density
    val extras = LocalLufossColors.current
    val dark = isSystemInDarkTheme()
    val palette = MapPalette(
        background = MaterialTheme.colorScheme.surfaceVariant,
        route = extras.route,
        pin = extras.pin
    )
    MapFrameLoop(map)
    Canvas(
        modifier = modifier
            .onSizeChanged { map.setViewport(it.width / density.toDouble(), it.height / density.toDouble()) }
            .pointerInput(map) { mapGestures(map, vm::onTap) }
            .pointerInput(map) { mapWheel(map) }
            .focusable()
            .onKeyEvent { handleMapKey(vm, it) }
            .semantics {
                contentDescription = MAP_DESCRIPTION
                customActions = listOf(
                    CustomAccessibilityAction("Zoom in") {
                        map.zoomCentre(Cfg.ZOOM_STEP)
                        true
                    },
                    CustomAccessibilityAction("Zoom out") {
                        map.zoomCentre(-Cfg.ZOOM_STEP)
                        true
                    },
                    CustomAccessibilityAction("Put north at the top") {
                        vm.onCompass()
                        true
                    }
                )
            }
    ) {
        drawMap(vm, palette, dark)
    }
}
