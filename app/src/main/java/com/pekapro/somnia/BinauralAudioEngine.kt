package com.pekapro.somnia

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =========================================================================================
// 3. MOTOR DSP DE SÍNTESIS BINAURAL Y SONIDOS DE LA NATURALEZA EN TIEMPO REAL
// =========================================================================================

class BinauralAudioEngine {
    private val sampleRate = 44100
    private val framesPerBuffer = 1024
    private val twoPi = 2.0 * PI

    @Volatile private var isRunning = false
    private var audioThread: Thread? = null

    @Volatile var targetCarrierHz: Float = 174f
    @Volatile var targetBeatHz: Float = 10f
    @Volatile var targetBinauralVolume: Float = 0.65f
    @Volatile var masterFadeMultiplier: Float = 1.0f
    /** Atenuación temporal por foco de audio ("ducking"); 1 = sin atenuar. */
    @Volatile var duckMultiplier: Float = 1.0f
    @Volatile var swapStereoChannels: Boolean = false
    @Volatile var isochronicPulseEnabled: Boolean = false

    private val natureVolumes = ConcurrentHashMap<NatureSoundType, Float>().apply {
        NatureSoundType.entries.forEach { put(it, 0f) }
    }

    fun setNatureVolume(type: NatureSoundType, volume: Float) {
        natureVolumes[type] = volume.coerceIn(0f, 1f)
    }

    fun setAllNatureVolumes(volumes: Map<NatureSoundType, Float>) {
        NatureSoundType.entries.forEach { type ->
            natureVolumes[type] = (volumes[type] ?: 0f).coerceIn(0f, 1f)
        }
    }

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true
        audioThread = Thread({ runSynthesisLoop() }, "SomniaBinauralDSP").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        val thread = audioThread
        audioThread = null
        try {
            thread?.join(350)
        } catch (_: InterruptedException) {
        }
    }

    private fun runSynthesisLoop() {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(framesPerBuffer * 4)

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return
        }

        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            return
        }

        val pcmBuffer = ShortArray(framesPerBuffer * 2)
        val rng = Random(42L)

        var phaseLeft = 0.0
        var phaseRight = 0.0
        var isoPhase = 0.0
        var currentCarrier = targetCarrierHz.toDouble()
        var currentBeat = targetBeatHz.toDouble()
        var currentBinVol = targetBinauralVolume.toDouble()

        var b0L = 0.0; var b1L = 0.0; var b2L = 0.0
        var b0R = 0.0; var b1R = 0.0; var b2R = 0.0

        var oceanWavePhase = 0.0
        var oceanSecondaryPhase = 1.7
        var oceanLpL = 0.0; var oceanLpR = 0.0
        var rainLpL = 0.0; var rainLpR = 0.0; var rainDropEnv = 0.0
        var windLfoPhase = 0.4
        var windLp1L = 0.0; var windLp2L = 0.0; var windLp1R = 0.0; var windLp2R = 0.0
        var cricketCarrierPhase1 = 0.0; var cricketCarrierPhase2 = 0.0
        var cricketEnvPhase1 = 0.0; var cricketEnvPhase2 = 2.1
        var streamLpL = 0.0; var streamLpR = 0.0
        var bubblePhase = 0.0; var bubbleFreq = 520.0; var bubbleEnv = 0.0
        var hearthBrownL = 0.0; var hearthBrownR = 0.0; var crackleEnv = 0.0

        try {
            audioTrack.play()
        } catch (_: Exception) {
            audioTrack.release()
            return
        }

        val invSampleRate = 1.0 / sampleRate

        while (isRunning) {
            val fade = masterFadeMultiplier.toDouble().coerceIn(0.0, 1.0) * duckMultiplier.toDouble().coerceIn(0.0, 1.0)
            val rainVol = (natureVolumes[NatureSoundType.RAIN] ?: 0f).toDouble()
            val oceanVol = (natureVolumes[NatureSoundType.OCEAN] ?: 0f).toDouble()
            val windVol = (natureVolumes[NatureSoundType.FOREST_WIND] ?: 0f).toDouble()
            val cricketVol = (natureVolumes[NatureSoundType.NIGHT_CRICKETS] ?: 0f).toDouble()
            val streamVol = (natureVolumes[NatureSoundType.RIVER_STREAM] ?: 0f).toDouble()
            val hearthVol = (natureVolumes[NatureSoundType.WARM_HEARTH] ?: 0f).toDouble()

            for (i in 0 until framesPerBuffer) {
                currentCarrier += (targetCarrierHz.toDouble() - currentCarrier) * 0.0008
                currentBeat += (targetBeatHz.toDouble() - currentBeat) * 0.0008
                currentBinVol += (targetBinauralVolume.toDouble() - currentBinVol) * 0.001

                val freqLeft = (currentCarrier - currentBeat * 0.5).coerceAtLeast(40.0)
                val freqRight = (currentCarrier + currentBeat * 0.5).coerceAtLeast(40.5)

                phaseLeft += twoPi * freqLeft * invSampleRate
                if (phaseLeft >= twoPi) phaseLeft -= twoPi

                phaseRight += twoPi * freqRight * invSampleRate
                if (phaseRight >= twoPi) phaseRight -= twoPi

                var binL = (sin(phaseLeft) * 0.90 + sin(phaseLeft * 2.0) * 0.07) * currentBinVol * 0.42
                var binR = (sin(phaseRight) * 0.90 + sin(phaseRight * 2.0) * 0.07) * currentBinVol * 0.42

                if (isochronicPulseEnabled) {
                    isoPhase += twoPi * currentBeat * invSampleRate
                    if (isoPhase >= twoPi) isoPhase -= twoPi
                    val isoMod = 0.65 + 0.35 * cos(isoPhase)
                    binL *= isoMod
                    binR *= isoMod
                }

                if (swapStereoChannels) {
                    val tmp = binL; binL = binR; binR = tmp
                }

                var natL = 0.0
                var natR = 0.0

                val whiteL = rng.nextDouble(-1.0, 1.0)
                val whiteR = rng.nextDouble(-1.0, 1.0)

                b0L = 0.99765 * b0L + whiteL * 0.0990460
                b1L = 0.96300 * b1L + whiteL * 0.2965164
                b2L = 0.57000 * b2L + whiteL * 1.0526913
                val pinkL = (b0L + b1L + b2L + whiteL * 0.1848) * 0.08

                b0R = 0.99765 * b0R + whiteR * 0.0990460
                b1R = 0.96300 * b1R + whiteR * 0.2965164
                b2R = 0.57000 * b2R + whiteR * 1.0526913
                val pinkR = (b0R + b1R + b2R + whiteR * 0.1848) * 0.08

                if (rainVol > 0.001) {
                    rainLpL += 0.22 * (pinkL - rainLpL)
                    rainLpR += 0.22 * (pinkR - rainLpR)
                    if (rng.nextDouble() < 0.0014) rainDropEnv = rng.nextDouble(0.12, 0.32)
                    rainDropEnv *= 0.992
                    val drop = whiteL * rainDropEnv * 0.35
                    natL += (rainLpL * 0.85 + drop) * rainVol * 0.55
                    natR += (rainLpR * 0.85 + drop * 0.8) * rainVol * 0.55
                }

                if (oceanVol > 0.001) {
                    oceanWavePhase += twoPi * 0.13 * invSampleRate
                    if (oceanWavePhase >= twoPi) oceanWavePhase -= twoPi
                    oceanSecondaryPhase += twoPi * 0.05 * invSampleRate
                    if (oceanSecondaryPhase >= twoPi) oceanSecondaryPhase -= twoPi

                    val swell = ((sin(oceanWavePhase) + 1.0) * 0.5) * 0.75 + ((sin(oceanSecondaryPhase) + 1.0) * 0.5) * 0.25
                    val cutoff = 0.015 + swell * 0.12
                    oceanLpL += cutoff * (pinkL - oceanLpL)
                    oceanLpR += cutoff * (pinkR - oceanLpR)

                    val env = 0.18 + swell * 0.82
                    natL += oceanLpL * env * oceanVol * 0.78
                    natR += oceanLpR * env * oceanVol * 0.78
                }

                if (windVol > 0.001) {
                    windLfoPhase += twoPi * 0.09 * invSampleRate
                    if (windLfoPhase >= twoPi) windLfoPhase -= twoPi
                    val breezeCut = 0.018 + ((sin(windLfoPhase) + 1.0) * 0.5) * 0.045
                    windLp1L += breezeCut * (pinkL - windLp1L)
                    windLp2L += breezeCut * (windLp1L - windLp2L)
                    windLp1R += breezeCut * (pinkR - windLp1R)
                    windLp2R += breezeCut * (windLp1R - windLp2R)

                    natL += windLp2L * windVol * 0.85
                    natR += windLp2R * windVol * 0.85
                }

                if (cricketVol > 0.001) {
                    cricketCarrierPhase1 += twoPi * 3850.0 * invSampleRate
                    if (cricketCarrierPhase1 >= twoPi) cricketCarrierPhase1 -= twoPi
                    cricketCarrierPhase2 += twoPi * 4120.0 * invSampleRate
                    if (cricketCarrierPhase2 >= twoPi) cricketCarrierPhase2 -= twoPi

                    cricketEnvPhase1 += twoPi * 3.2 * invSampleRate
                    if (cricketEnvPhase1 >= twoPi) cricketEnvPhase1 -= twoPi
                    cricketEnvPhase2 += twoPi * 2.7 * invSampleRate
                    if (cricketEnvPhase2 >= twoPi) cricketEnvPhase2 -= twoPi

                    val pulse1 = sin(cricketEnvPhase1).coerceAtLeast(0.0)
                    val pulse2 = sin(cricketEnvPhase2).coerceAtLeast(0.0)
                    val trill = sin(cricketEnvPhase1 * 11.0).coerceAtLeast(0.0)

                    natL += sin(cricketCarrierPhase1) * pulse1 * trill * 0.045 * cricketVol
                    natR += sin(cricketCarrierPhase2) * pulse2 * trill * 0.042 * cricketVol
                }

                if (streamVol > 0.001) {
                    streamLpL += 0.14 * (pinkL - streamLpL)
                    streamLpR += 0.14 * (pinkR - streamLpR)
                    val highFlowL = pinkL - streamLpL
                    val highFlowR = pinkR - streamLpR

                    if (rng.nextDouble() < 0.0022) {
                        bubbleFreq = rng.nextDouble(420.0, 980.0)
                        bubbleEnv = 0.08
                    }
                    bubbleEnv *= 0.994
                    bubbleFreq += 0.35
                    bubblePhase += twoPi * bubbleFreq * invSampleRate
                    if (bubblePhase >= twoPi) bubblePhase -= twoPi
                    val bubble = sin(bubblePhase) * bubbleEnv

                    natL += (highFlowL * 0.42 + bubble * 0.6) * streamVol * 0.55
                    natR += (highFlowR * 0.42 + bubble * 0.5) * streamVol * 0.55
                }

                if (hearthVol > 0.001) {
                    hearthBrownL = (hearthBrownL + whiteL * 0.025).coerceIn(-1.0, 1.0) * 0.996
                    hearthBrownR = (hearthBrownR + whiteR * 0.025).coerceIn(-1.0, 1.0) * 0.996

                    if (rng.nextDouble() < 0.0018) crackleEnv = rng.nextDouble(0.18, 0.55)
                    crackleEnv *= 0.965
                    val spark = whiteL * crackleEnv * 0.25

                    natL += (hearthBrownL * 0.32 + spark) * hearthVol * 0.65
                    natR += (hearthBrownR * 0.32 + spark * 0.85) * hearthVol * 0.65
                }

                val outL = softLimit((binL + natL) * fade)
                val outR = softLimit((binR + natR) * fade)

                pcmBuffer[i * 2] = (outL * 32760.0).toInt().coerceIn(-32767, 32767).toShort()
                pcmBuffer[i * 2 + 1] = (outR * 32760.0).toInt().coerceIn(-32767, 32767).toShort()
            }

            try {
                audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
            } catch (_: Exception) {
                break
            }
        }

        try {
            audioTrack.stop()
        } catch (_: Exception) {
        }
        audioTrack.release()
    }

    val isActive: Boolean get() = isRunning
}

/** Limitador suave continuo y derivable: pendiente 1 en el origen y techo asintótico ±0.92. */
internal fun softLimit(x: Double): Double = SOFT_LIMIT_CEILING * kotlin.math.tanh(x / SOFT_LIMIT_CEILING)
internal const val SOFT_LIMIT_CEILING = 0.92
