package io.github.nuadyr.lufoss.device

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import androidx.core.hardware.display.DisplayManagerCompat
import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.geo.GeoMath
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The direction the phone faces, in degrees clockwise from true north, from the rotation-vector sensor. */
class CompassSource(context: Context) {
    private val appContext = context.applicationContext
    private val sensors = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val available: Boolean get() = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    /** [declination] gives the angle between magnetic and true north for the current position, in degrees. */
    fun headings(declination: () -> Float): Flow<Double> = callbackFlow {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(MATRIX_SIZE)
        val screen = FloatArray(MATRIX_SIZE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                remapForScreen(rotation, screen)
                trySend(GeoMath.norm(headingOf(screen) + declination()))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors.unregisterListener(listener) }
    }

    /** Re-expresses the rotation matrix for the way the screen is turned. */
    private fun remapForScreen(rotation: FloatArray, out: FloatArray) {
        val display = DisplayManagerCompat.getInstance(appContext).getDisplay(Display.DEFAULT_DISPLAY)
        val axes = when (display?.rotation) {
            Surface.ROTATION_90 -> intArrayOf(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X)
            Surface.ROTATION_180 -> intArrayOf(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y)
            Surface.ROTATION_270 -> intArrayOf(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
            else -> null
        }
        if (axes == null) {
            rotation.copyInto(out)
        } else {
            SensorManager.remapCoordinateSystem(rotation, axes[0], axes[1], out)
        }
    }

    /**
     * Held flat, the way the top of the screen points; held upright, the way the back of the phone points.
     * [m] maps screen axes to world axes (east, north, up), row by row.
     */
    private fun headingOf(m: FloatArray): Double {
        val screenFacesUp = abs(m[8]) >= Cfg.FLAT_TILT_COS
        val east = if (screenFacesUp) m[1] else -m[2]
        val north = if (screenFacesUp) m[4] else -m[5]
        return Math.toDegrees(atan2(east.toDouble(), north.toDouble()))
    }

    companion object {
        private const val MATRIX_SIZE = 9

        /** Degrees to add to a magnetic heading to get a true-north heading at this position. */
        fun declinationAt(lat: Double, lng: Double): Float =
            GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
    }
}
