package io.github.nuadyr.lufoss.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.positionChange
import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.geo.GeoMath
import io.github.nuadyr.lufoss.map.MapController
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NEVER_A_TAP = 1_000_000f
private const val WHEEL_CTRL_BOOST = 4.0

/**
 * Drag to move, pinch to zoom, twist two fingers to turn the map, double-tap to zoom in, tap to drop a pin.
 * While the map follows a walker, two fingers only zoom: the dot stays where it is on the screen.
 */
suspend fun PointerInputScope.mapGestures(map: MapController, onTap: (Double, Double) -> Unit) {
    val d = density
    val slop = Cfg.TAP_MOVE_DP * d
    coroutineScope {
        var tapJob: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            map.beginTouch()
            var moved = 0f
            var multi = false
            var twist = 0.0
            var rotating = false
            var lastPos = down.position
            var lastTime = down.uptimeMillis
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    multi = true
                    moved = NEVER_A_TAP
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val rotation = event.calculateRotation()
                    val centre = event.calculateCentroid(useCurrent = true)
                    val cx = (centre.x / d).toDouble()
                    val cy = (centre.y / d).toDouble()
                    if (map.follow) {
                        if (zoom != 1f) map.zoomAt(cx, cy, map.z + ln(zoom.toDouble()) / LN2)
                    } else {
                        map.panBy((pan.x / d).toDouble(), (pan.y / d).toDouble())
                        if (zoom != 1f) map.zoomAt(cx, cy, map.z + ln(zoom.toDouble()) / LN2)
                        twist += rotation
                        if (rotating || abs(twist) > Cfg.ROTATE_START_DEG) {
                            rotating = true
                            map.rotateAt(cx, cy, map.bearing - rotation)
                        }
                    }
                } else if (pressed.size == 1) {
                    val change = pressed[0]
                    val delta = change.positionChange()
                    lastPos = change.position
                    if (!multi) moved += abs(delta.x) + abs(delta.y)
                    if (moved > slop) {
                        val dx = (delta.x / d).toDouble()
                        val dy = (delta.y / d).toDouble()
                        map.follow = false
                        if (event.keyboardModifiers.isShiftPressed) {
                            map.rotateAt(map.width / 2, map.height / 2, map.bearing - dx * Cfg.ROTATE_DRAG_DEG_PER_PX)
                        } else {
                            map.panBy(dx, dy)
                        }
                    }
                }
                event.changes.firstOrNull()?.let { lastTime = it.uptimeMillis }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })

            if (rotating && abs(GeoMath.angDiff(0.0, map.bearing)) < Cfg.NORTH_SNAP_DEG) map.bearing = 0.0
            map.endTouch()
            val tapped = !multi && moved <= slop && lastTime - down.uptimeMillis < Cfg.TAP_MS
            if (tapped) {
                val x = (lastPos.x / d).toDouble()
                val y = (lastPos.y / d).toDouble()
                val pending = tapJob
                if (pending != null && pending.isActive) {
                    // A second tap soon after the first zooms in instead of dropping a pin.
                    pending.cancel()
                    tapJob = null
                    map.zoomAt(x, y, map.z + Cfg.ZOOM_STEP)
                } else {
                    tapJob = launch {
                        delay(Cfg.DOUBLE_TAP_MS)
                        onTap(x, y)
                    }
                }
            }
        }
    }
}

private val LN2 = ln(2.0)

/** Mouse wheel and trackpad scrolling zoom around the pointer. */
suspend fun PointerInputScope.mapWheel(map: MapController) {
    val d = density
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            val boost = if (event.keyboardModifiers.isCtrlPressed) WHEEL_CTRL_BOOST else 1.0
            val steps = change.scrollDelta.y * Cfg.WHEEL_ZOOM_PER_NOTCH * boost
            map.zoomAt((change.position.x / d).toDouble(), (change.position.y / d).toDouble(), map.z - steps)
            change.consume()
        }
    }
}

/** Keyboard control for the map: arrows move it, plus and minus zoom, Shift with left or right turns it, N is north. */
fun handleMapKey(vm: LufossViewModel, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val map = vm.map
    val pan = Cfg.KEY_PAN_DP
    when {
        event.key == Key.Escape && vm.picking -> vm.endPick(true)
        event.isShiftPressed && event.key == Key.DirectionLeft -> map.rotateBy(Cfg.ROTATE_KEY_DEG)
        event.isShiftPressed && event.key == Key.DirectionRight -> map.rotateBy(-Cfg.ROTATE_KEY_DEG)
        event.key == Key.DirectionLeft -> panKey(map, pan, 0.0)
        event.key == Key.DirectionRight -> panKey(map, -pan, 0.0)
        event.key == Key.DirectionUp -> panKey(map, 0.0, pan)
        event.key == Key.DirectionDown -> panKey(map, 0.0, -pan)
        event.key == Key.Plus || event.key == Key.Equals -> map.zoomCentre(Cfg.ZOOM_STEP)
        event.key == Key.Minus -> map.zoomCentre(-Cfg.ZOOM_STEP)
        event.key == Key.N -> vm.onCompass()
        else -> return false
    }
    return true
}

private fun panKey(map: MapController, dx: Double, dy: Double) {
    map.follow = false
    map.panBy(dx, dy)
    map.armRecentre()
}
