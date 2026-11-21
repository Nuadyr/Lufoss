package io.github.ardalorn.lufoss.data

import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.geo.GeoMath
import io.github.ardalorn.lufoss.geo.LatLng
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A loop found by the planner, with how far its time is from the target, how much it doubles back and how many
 * U-turns it holds.
 */
class LoopCandidate(val route: Route, val err: Double, val overlap: Double, val uTurns: Int = 0)

/**
 * Round-trip walks. The loop is a triangle from the start point through two corners and back. The router times it,
 * then the corners are moved in or out until the walk takes the time chosen. Several directions are tried and the
 * one closest to the chosen time that does not double back over itself wins. A loop with a U-turn in it is never
 * offered, whatever its time.
 */
class LoopPlanner(private val router: Router) {
    /**
     * Resolves to the best loop found, or null when none could be planned. Cancelling the calling coroutine
     * drops a walk that is still being planned. [onAttempt] is told the attempt number before each request.
     */
    suspend fun plan(start: Place, minutes: Int, onAttempt: (Int) -> Unit): LoopCandidate? {
        val target = minutes * 60.0
        val began = System.nanoTime()
        val firstBearing = Random.nextDouble() * 360.0
        var best: LoopCandidate? = null
        var tries = 0
        val origin = start.latLng
        outer@ for (t in 0 until Cfg.LOOP_TRIES) {
            val bearing = firstBearing + t * Cfg.LOOP_BEARING_STEP_DEG
            // Straight-line length of each side, as a first guess.
            var r = target * Cfg.LOOP_SPEED_MS / (Cfg.LOOP_SIDES * Cfg.LOOP_DETOUR)
            var resizes = 0
            for (k in 0 until Cfg.LOOP_REFINES) {
                currentCoroutineContext().ensureActive()
                if ((System.nanoTime() - began) / NS_PER_MS > Cfg.LOOP_BUDGET_MS) break@outer
                tries++
                onAttempt(tries)
                val stops = listOf(
                    origin,
                    GeoMath.offsetPoint(origin, r, bearing),
                    GeoMath.offsetPoint(origin, r, bearing + Cfg.LOOP_CORNER_DEG),
                    origin
                )
                val rt = try {
                    router.fetchRoute(
                        stops,
                        "Your start point",
                        tries = 1,
                        timeoutMs = Cfg.LOOP_FETCH_TIMEOUT_MS,
                        noUTurns = true
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (rt == null) {
                    r *= Cfg.LOOP_FAIL_SHRINK
                    continue
                }
                val candidate = LoopCandidate(rt, abs(rt.dur - target) / target, overlapShare(rt), uTurnCount(rt))
                // A U-turn rules the loop out and is never offered. A different size can move a corner off the
                // street that caused it, so try the near and far edge of the time buffer before dropping the
                // direction.
                if (!allowed(candidate)) {
                    if (resizes >= Cfg.LOOP_UTURN_RESIZES) break
                    val edge = if (resizes % 2 == 0) -Cfg.LOOP_BUFFER_S else Cfg.LOOP_BUFFER_S
                    resizes++
                    val aim = max(target + edge, MIN_AIM_S)
                    r *= min(Cfg.LOOP_SCALE_MAX, max(Cfg.LOOP_SCALE_MIN, aim / rt.dur))
                    continue
                }
                if (better(candidate, best)) best = candidate
                // Doubling back cannot be fixed by resizing, so try another direction.
                if (!isClean(candidate)) break
                if (candidate.err <= Cfg.LOOP_TOL) break@outer
                r *= min(Cfg.LOOP_SCALE_MAX, max(Cfg.LOOP_SCALE_MIN, target / rt.dur))
            }
        }
        return best
    }

    companion object {
        private const val NS_PER_MS = 1_000_000L
        private const val MIN_AIM_S = 60.0

        /** Share of the route that passes over ground it already covered a good way back. */
        fun overlapShare(rt: Route): Double {
            val seen = HashMap<Pair<Long, Long>, Double>()
            var shared = 0.0
            val startLat = rt.lats[0]
            val startLng = rt.lngs[0]
            for (i in 1 until rt.size) {
                val key = Pair(
                    (rt.lats[i] / Cfg.LOOP_CELL_DEG).roundToLong(),
                    (rt.lngs[i] / Cfg.LOOP_CELL_DEG).roundToLong()
                )
                val first = seen[key]
                if (first == null) {
                    seen[key] = rt.cum[i]
                    continue
                }
                val farBack = rt.cum[i] - first > Cfg.LOOP_OVERLAP_MIN_M
                val fromStart = GeoMath.segmentMetres(rt.lats[i], rt.lngs[i], startLat, startLng)
                val awayFromStart = fromStart > Cfg.LOOP_START_ZONE_M
                if (farBack && awayFromStart) shared += rt.cum[i] - rt.cum[i - 1]
            }
            return shared / max(1.0, rt.total)
        }

        /**
         * How many times the walk turns right round to go back the way it came. Counts the router's own U-turn
         * manoeuvres (including one at a corner of the loop), and any place where the line itself folds back so
         * that the stretch just before it and the stretch just after it lie along the same bearing.
         */
        fun uTurnCount(rt: Route): Int = max(rt.steps.count { it.mod == "uturn" }, foldCount(rt))

        private fun foldCount(rt: Route): Int {
            val arm = Cfg.LOOP_UTURN_ARM_M
            var folds = 0
            var resumeAt = 0.0
            for (i in 1 until rt.size - 1) {
                val at = rt.cum[i]
                if (at < arm || at > rt.total - arm || at < resumeAt) continue
                val apex = LatLng(rt.lats[i], rt.lngs[i])
                val back = GeoMath.bearingTo(apex, pointAtDist(rt, at - arm))
                val ahead = GeoMath.bearingTo(apex, pointAtDist(rt, at + arm))
                if (abs(GeoMath.angDiff(back, ahead)) <= Cfg.LOOP_UTURN_FOLD_DEG) {
                    folds++
                    resumeAt = at + arm // One fold shows up at several neighbouring points; count it once.
                }
            }
            return folds
        }

        private fun pointAtDist(rt: Route, dist: Double): LatLng {
            val hit = Arrays.binarySearch(rt.cum, dist)
            val i = if (hit >= 0) hit else -hit - 1
            if (i <= 0) return LatLng(rt.lats[0], rt.lngs[0])
            if (i >= rt.size) return LatLng(rt.lats[rt.size - 1], rt.lngs[rt.size - 1])
            val span = rt.cum[i] - rt.cum[i - 1]
            val t = if (span == 0.0) 0.0 else (dist - rt.cum[i - 1]) / span
            return LatLng(
                rt.lats[i - 1] + (rt.lats[i] - rt.lats[i - 1]) * t,
                rt.lngs[i - 1] + (rt.lngs[i] - rt.lngs[i - 1]) * t
            )
        }

        /** A loop may be offered only when it holds no U-turn. */
        fun allowed(c: LoopCandidate): Boolean = c.uTurns == 0

        /** A loop with next to no doubling back counts as clean. */
        fun isClean(c: LoopCandidate): Boolean = c.overlap <= Cfg.LOOP_CLEAN_OVERLAP

        /**
         * A loop with no overlap always beats one with overlap, whatever its time. Among clean loops the closest
         * to the chosen time wins; overlapping loops are only used when no clean one was found, and then the
         * least overlapping wins.
         */
        fun better(a: LoopCandidate, b: LoopCandidate?): Boolean = when {
            b == null -> true
            isClean(a) != isClean(b) -> isClean(a)
            isClean(a) -> a.err < b.err
            else -> a.overlap < b.overlap || (a.overlap == b.overlap && a.err < b.err)
        }
    }
}
