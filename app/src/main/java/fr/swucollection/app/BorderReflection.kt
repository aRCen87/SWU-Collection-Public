package fr.swucollection.app

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal const val REFLECTION_PREFERENCES = "visual_effects"
internal const val REFLECTION_ENABLED = "gold_border_reflection"

private fun SensorManager.reflectionSensor(): Sensor? =
    getDefaultSensor(Sensor.TYPE_GRAVITY) ?: getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

/** One listener shared by the home buttons. State is consumed during drawing, not composition. */
@Composable
internal fun rememberBorderTilt(active: Boolean): State<BorderTilt?> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf<BorderTilt?>(null) }
    DisposableEffect(context, owner, active) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (active) manager?.reflectionSensor() else null
        val filter = BorderTiltFilter()
        var registered = false
        @Suppress("DEPRECATION")
        fun rotation(): Int = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay?.rotation ?: 0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!registered || event.values.size < 3) return
                val next = filter.update(event.values[0], event.values[1], event.values[2], rotation(), event.timestamp)
                val previous = tilt.value
                if (next != null && (previous == null || kotlin.math.abs(next.x - previous.x) > 0.002f ||
                        kotlin.math.abs(next.y - previous.y) > 0.002f)) tilt.value = next
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun stop() {
            registered = false
            manager?.unregisterListener(listener)
            filter.reset()
            tilt.value = null
        }
        fun start() {
            if (!registered && active && sensor != null && ValueAnimator.areAnimatorsEnabled()) {
                filter.reset()
                registered = manager?.registerListener(listener, sensor, 33_333) == true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        tilt.value = null
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    return tilt
}

/** A narrow moving light band, drawn only over the rounded outline. No pointer handling. */
internal fun Modifier.goldBorderReflection(tilt: State<BorderTilt?>): Modifier = drawWithContent {
    drawContent()
    val motion = tilt.value ?: return@drawWithContent
    val inset = size.width * 0.01f
    val outlineSize = Size((size.width - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f))
    val radius = (size.height * 0.15f).coerceAtMost(18.dp.toPx())
    val position = 0.5f + motion.x * 0.45f + motion.y * 0.2f
    val centerX = size.width * position
    val bandWidth = size.width * 0.3f
    val light = Brush.linearGradient(
        0f to Color.Transparent,
        0.35f to Color(0xFFFFD47D).copy(alpha = 0.12f),
        0.5f to Color(0xFFFFF3CC).copy(alpha = 0.65f),
        0.65f to Color(0xFFFFD47D).copy(alpha = 0.12f),
        1f to Color.Transparent,
        start = Offset(centerX - bandWidth, -size.height * 0.4f),
        end = Offset(centerX + bandWidth, size.height * 1.4f)
    )
    drawRoundRect(light, topLeft = Offset(inset, inset), size = outlineSize,
        cornerRadius = CornerRadius(radius, radius), style = Stroke(1.5.dp.toPx()))
}

@Composable
internal fun BorderReflectionSettings(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val supported = remember(context) {
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.reflectionSensor() != null
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reflets dorés", color = MaterialTheme.colorScheme.onSurface)
                Text(if (supported) "Un reflet suit l’inclinaison du téléphone sur les boutons de l’accueil. Respecte la désactivation des animations Android."
                    else "Le capteur de mouvement n’est pas disponible sur cet appareil.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Switch(checked = enabled && supported, enabled = supported, onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = "Activer les reflets dorés" })
        }
    }
}
