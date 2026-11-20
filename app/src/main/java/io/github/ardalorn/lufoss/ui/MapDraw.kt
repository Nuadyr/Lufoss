package io.github.ardalorn.lufoss.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.geo.GeoMath
import io.github.ardalorn.lufoss.map.MapController
import io.github.ardalorn.lufoss.map.TileStore
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The colours the map canvas draws with. */
class MapPalette(val background: Color, val route: Color, val pin: Color, val ring: Color = Color.White)

/**
 * Dark mode turns the light map tiles into dark ones: invert, hue-rotate 180 degrees, then soften.
 * The same recipe the web page used as a CSS filter.
 */
private val darkTileFilter: ColorFilter by lazy {
    val invert = android.graphics.ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val hue = android.graphics.ColorMatrix(
        floatArrayOf(
            -0.574f, 1.43f, 0.144f, 0f, 0f,
            0.426f, 0.43f, 0.144f, 0f, 0f,
            0.426f, 1.43f, -0.856f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val saturate = android.graphics.ColorMatrix().apply { setSaturation(0.45f) }
    val brightness = android.graphics.ColorMatrix().apply { setScale(0.9f, 0.9f, 0.9f, 1f) }
    val contrast = android.graphics.ColorMatrix(
        floatArrayOf(
            0.95f, 0f, 0f, 0f, 6.375f,
            0f, 0.95f, 0f, 0f, 6.375f,
            0f, 0f, 0.95f, 0f, 6.375f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    invert.postConcat(hue)
    invert.postConcat(saturate)
    invert.postConcat(brightness)
    invert.postConcat(contrast)
    ColorFilter.colorMatrix(ColorMatrix(invert.array))
}

/** Draws the whole map: tiles first, then the route, markers and the person's dot. */
fun DrawScope.drawMap(vm: LufossViewModel, palette: MapPalette, dark: Boolean) {
    // Reading the version makes this draw run again whenever a tile picture arrives.
    check(vm.tiles.version >= 0)
    val w = size.width / density
    val h = size.height / density
    drawRect(palette.background)
    drawTiles(vm.map, vm.tiles, w.toDouble(), h.toDouble(), dark)
    scale(density, Offset.Zero) { drawOverlays(vm, palette, w.toDouble(), h.toDouble()) }
}

private fun DrawScope.drawTiles(map: MapController, tiles: TileStore, w: Double, h: Double, dark: Boolean) {
    val zi = Math.round(map.z).toInt().coerceIn(Cfg.MIN_Z.toInt(), Cfg.MAX_TILE_Z)
    val s = 2.0.pow(map.z - zi)
    val c = GeoMath.project(map.lat, map.lng, zi.toDouble())
    val n = 1 shl zi
    // A turned map needs tiles out to the corners.
    val turned = map.bearing != 0.0
    val rx = if (turned) hypot(w, h) / 2 else w / 2
    val ry = if (turned) hypot(w, h) / 2 else h / 2
    val tx0 = floor((c.x - rx / s) / Cfg.TILE).toInt()
    val tx1 = floor((c.x + rx / s) / Cfg.TILE).toInt()
    val ty0 = max(0, floor((c.y - ry / s) / Cfg.TILE).toInt())
    val ty1 = min(n - 1, floor((c.y + ry / s) / Cfg.TILE).toInt())
    // One extra pixel of overlap hides the seams between tiles.
    val sizePx = ceil(Cfg.TILE * s * density + 1).toInt()
    val filter = if (dark) darkTileFilter else null
    rotate(degrees = (-map.bearing).toFloat(), pivot = Offset(size.width / 2, size.height / 2)) {
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                val dx = ((tx * Cfg.TILE - c.x) * s + w / 2) * density
                val dy = ((ty * Cfg.TILE - c.y) * s + h / 2) * density
                translate(dx.toFloat(), dy.toFloat()) {
                    drawTile(tiles, zi, ((tx % n) + n) % n, ty, sizePx, filter)
                }
            }
        }
    }
}

private fun DrawScope.drawTile(tiles: TileStore, z: Int, x: Int, y: Int, sizePx: Int, filter: ColorFilter?) {
    val target = IntSize(sizePx, sizePx)
    val image = tiles.get(z, x, y)
    if (image != null) {
        drawImage(image, dstSize = target, colorFilter = filter, filterQuality = FilterQuality.Medium)
        return
    }
    // Show a fresh blurry parent while the sharp tile loads.
    val parent = if (z > Cfg.MIN_Z.toInt()) tiles.cached(z - 1, x shr 1, y shr 1) else null
    if (parent != null) {
        val halfW = parent.width / 2
        val halfH = parent.height / 2
        drawImage(
            parent,
            srcOffset = IntOffset((x and 1) * halfW, (y and 1) * halfH),
            srcSize = IntSize(halfW, halfH),
            dstSize = target,
            colorFilter = filter,
            filterQuality = FilterQuality.Medium
        )
    }
}

/** Everything drawn above the tiles, in dp. */
private fun DrawScope.drawOverlays(vm: LufossViewModel, palette: MapPalette, w: Double, h: Double) {
    val map = vm.map
    val cc = GeoMath.project(map.lat, map.lng, map.z)
    val turn = map.bearing * GeoMath.DEG
    val cs = cos(turn)
    val sn = sin(turn)

    // Map position to screen position, turned by the bearing.
    fun screen(lat: Double, lng: Double): Offset {
        val p = GeoMath.project(lat, lng, map.z)
        val dx = p.x - cc.x
        val dy = p.y - cc.y
        return Offset((dx * cs + dy * sn + w / 2).toFloat(), (-dx * sn + dy * cs + h / 2).toFloat())
    }

    val route = vm.route
    if (route != null && route.size > 1) {
        val path = Path()
        for (i in 0 until route.size) {
            val o = screen(route.lats[i], route.lngs[i])
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        val round = { width: Float -> Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        drawPath(path, palette.ring, style = round(Cfg.ROUTE_W + Cfg.ROUTE_CASING))
        drawPath(path, palette.route, style = round(Cfg.ROUTE_W))
        val from = vm.orig
        if (from != null && !vm.navActive) drawMarker(screen(from.lat, from.lng), palette, inner = false)
    }
    val start = vm.start
    if (start != null && !vm.navActive) drawMarker(screen(start.lat, start.lng), palette, inner = true)

    val fix = vm.meFix
    val dot = if (vm.navActive) map.meV ?: fix?.latLng else null
    if (dot != null) {
        val at = screen(dot.lat, dot.lng)
        val acc = fix?.acc?.takeIf { it.isFinite() } ?: 0.0
        val metresPerPixel = Cfg.EARTH_CIRCUMFERENCE_M * cos(dot.lat * GeoMath.DEG) / GeoMath.worldSize(map.z)
        val radius = (acc / metresPerPixel).toFloat()
        if (radius > Cfg.ACCURACY_MIN_R) drawCircle(palette.route, radius, at, alpha = ACCURACY_ALPHA)
        val heading = map.heading
        if (vm.navActive && heading != null) {
            // The view cone: the way the person faces, relative to the way the map is turned.
            val centre = (heading - map.bearing).toFloat() - QUARTER_TURN
            drawArc(
                color = palette.route,
                startAngle = centre - Cfg.CONE_HALF_DEG,
                sweepAngle = Cfg.CONE_HALF_DEG * 2,
                useCenter = true,
                topLeft = Offset(at.x - Cfg.CONE_R, at.y - Cfg.CONE_R),
                size = Size(Cfg.CONE_R * 2, Cfg.CONE_R * 2),
                alpha = Cfg.CONE_ALPHA
            )
        }
        drawCircle(palette.ring, Cfg.ME_DOT_R + Cfg.DOT_RING, at)
        drawCircle(palette.route, Cfg.ME_DOT_R, at)
    }

    vm.dest?.let { drawPin(screen(it.lat, it.lng), palette) }
}

private fun DrawScope.drawMarker(at: Offset, palette: MapPalette, inner: Boolean) {
    drawCircle(palette.ring, Cfg.START_DOT_R, at)
    drawCircle(palette.route, Cfg.START_DOT_R, at, style = Stroke(width = MARKER_STROKE))
    if (inner) drawCircle(palette.route, Cfg.START_DOT_R * INNER_DOT, at)
}

private fun DrawScope.drawPin(at: Offset, palette: MapPalette) {
    val head = Offset(at.x, at.y - Cfg.PIN_HEAD_OFFSET)
    val path = Path().apply {
        moveTo(at.x, at.y)
        arcTo(Rect(center = head, radius = Cfg.PIN_HEAD_R), PIN_ARC_START, PIN_ARC_SWEEP, false)
        close()
    }
    drawPath(path, palette.pin)
    drawPath(path, palette.ring, style = Stroke(width = PIN_OUTLINE))
    drawCircle(palette.ring, Cfg.PIN_HOLE_R, head)
}

private const val ACCURACY_ALPHA = 0.15f
private const val QUARTER_TURN = 90f
private const val MARKER_STROKE = 4f
private const val INNER_DOT = 0.4f
private const val PIN_OUTLINE = 2f
private const val PIN_ARC_START = 117f
private const val PIN_ARC_SWEEP = 306f
