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
// 2. MODELOS DE DOMINIO Y CATÁLOGO DE MODOS BINAURALES
// =========================================================================================

enum class BrainwaveBand(
    val titleEs: String,
    val rangeHz: String,
    val descriptionEs: String,
    val accentColor: Color
) {
    EPSILON(
        titleEs = "Épsilon & Infra-Delta",
        rangeHz = "0.5 – 1.5 Hz",
        descriptionEs = "Asociadas a las fases más profundas del sueño lento; un fondo muy suave que puede ayudar a relajarse.",
        accentColor = RoseQuartz
    ),
    DELTA(
        titleEs = "Ondas Delta",
        rangeHz = "1.5 – 4.0 Hz",
        descriptionEs = "Asociadas al sueño profundo (NREM fase 3) y a la sensación de descanso físico.",
        accentColor = EtherealViolet
    ),
    THETA(
        titleEs = "Ondas Theta",
        rangeHz = "4.0 – 8.0 Hz",
        descriptionEs = "Asociadas a la somnolencia, la meditación profunda y la siesta; pueden ayudar a soltar tensión.",
        accentColor = BioluminescentTeal
    ),
    ALPHA(
        titleEs = "Ondas Alfa",
        rangeHz = "8.0 – 13.0 Hz",
        descriptionEs = "Asociadas al descanso consciente y a la calma despierta; pueden ayudar a relajarse.",
        accentColor = LuminousGold
    ),
    SMR_BETA(
        titleEs = "Ritmo Sensoriomotor (SMR)",
        rangeHz = "13.0 – 15.0 Hz",
        descriptionEs = "Asociado a un estado de alerta tranquila, útil para espabilarse con suavidad tras la siesta.",
        accentColor = CalmCyan
    );

    companion object {
        fun fromBeatFrequency(beatHz: Float): BrainwaveBand = when {
            beatHz < 1.5f -> EPSILON
            beatHz < 4.0f -> DELTA
            beatHz < 8.0f -> THETA
            beatHz <= 13.0f -> ALPHA
            else -> SMR_BETA
        }
    }
}

enum class NatureSoundType(
    val id: String,
    val titleEs: String,
    val subtitleEs: String,
    val defaultAccent: Color
) {
    RAIN("rain", "Lluvia Suave", "Cortina acústica de gotas finas y follaje húmedo", CalmCyan),
    OCEAN("ocean", "Olas del Océano", "Resaca rítmica de marea nocturna (ciclo 0.13 Hz)", BioluminescentTeal),
    FOREST_WIND("forest_wind", "Brisa del Bosque", "Viento cálido resonando entre copas de pinos", LuminousGold),
    NIGHT_CRICKETS("night_crickets", "Noche Estrellada", "Grillos cristalinos lejanos en pradera serena", EtherealViolet),
    RIVER_STREAM("river_stream", "Arroyo de Montaña", "Agua cristalina fluyendo sobre piedras pulidas", SoftAmber),
    WARM_HEARTH("warm_hearth", "Hoguera Nocturna", "Brasas cálidas y suaves crepitaciones de madera", RoseQuartz)
}

data class BinauralMode(
    val id: String,
    val nameEs: String,
    val taglineEs: String,
    val descriptionEs: String,
    val categoryBadge: String,
    val carrierHz: Float,
    val beatHz: Float,
    val band: BrainwaveBand,
    val recommendedMinutes: Int,
    val benefitsEs: List<String>,
    val defaultNatureMix: Map<NatureSoundType, Float>
)

data class SavedMixPreset(
    val id: String,
    val name: String,
    val modeId: String,
    val carrierHz: Float,
    val beatHz: Float,
    val binauralVolume: Float,
    val natureVolumes: Map<NatureSoundType, Float>
)

object BinauralCatalog {
    val modes: List<BinauralMode> = listOf(
        BinauralMode(
            id = "descanso_alfa",
            nameEs = "Descanso Sereno",
            taglineEs = "Desconexión mental & calma Alfa",
            descriptionEs = "Ideal para desconectar tras una jornada intensa. Un pulso de 10 Hz sobre una portadora cálida de 174 Hz.",
            categoryBadge = "DESCANSO",
            carrierHz = 174f,
            beatHz = 10.0f,
            band = BrainwaveBand.ALPHA,
            recommendedMinutes = 20,
            benefitsEs = listOf("Puede ayudar a bajar el ritmo y descansar la vista", "Ayuda a acallar el ruido mental"),
            defaultNatureMix = mapOf(NatureSoundType.FOREST_WIND to 0.42f, NatureSoundType.RIVER_STREAM to 0.35f)
        ),
        BinauralMode(
            id = "siesta_theta",
            nameEs = "Siesta Revitalizante",
            taglineEs = "Siesta corta de 25 min",
            descriptionEs = "Un pulso Theta suave (6.0 Hz) pensado para una siesta corta de 25 minutos, sin alargarla en exceso.",
            categoryBadge = "SIESTA",
            carrierHz = 200f,
            beatHz = 6.0f,
            band = BrainwaveBand.THETA,
            recommendedMinutes = 25,
            benefitsEs = listOf("Pensada para despejarse en poco tiempo", "Duración corta para despertar con menos pesadez"),
            defaultNatureMix = mapOf(NatureSoundType.RAIN to 0.45f, NatureSoundType.FOREST_WIND to 0.25f)
        ),
        BinauralMode(
            id = "sueno_profundo",
            nameEs = "Sueño Profundo",
            taglineEs = "Inmersión Delta para conciliar el sueño",
            descriptionEs = "Un pulso binaural de 2.5 Hz, en el rango Delta, sobre una portadora grave de 136.1 Hz (Om). Un fondo envolvente que puede ayudar a conciliar el sueño.",
            categoryBadge = "SUEÑO",
            carrierHz = 136.1f,
            beatHz = 2.5f,
            band = BrainwaveBand.DELTA,
            recommendedMinutes = 60,
            benefitsEs = listOf("Puede ayudar a conciliar el sueño", "Olas oceánicas sincronizadas con la respiración"),
            defaultNatureMix = mapOf(NatureSoundType.OCEAN to 0.50f, NatureSoundType.NIGHT_CRICKETS to 0.22f)
        ),
        BinauralMode(
            id = "sueno_reparador",
            nameEs = "Sueño Reparador",
            taglineEs = "Descanso nocturno largo Infra-Delta (1.5 Hz)",
            descriptionEs = "Combina una portadora ultra-suave de 110 Hz con un pulso de 1.5 Hz asociado a la fase más reparadora del sueño lento.",
            categoryBadge = "SUEÑO REPARADOR",
            carrierHz = 110f,
            beatHz = 1.5f,
            band = BrainwaveBand.EPSILON,
            recommendedMinutes = 90,
            benefitsEs = listOf("Pensado para acompañar un descanso largo y tranquilo", "Manto envolvente de lluvia nocturna y fuego cálido"),
            defaultNatureMix = mapOf(NatureSoundType.RAIN to 0.40f, NatureSoundType.WARM_HEARTH to 0.35f, NatureSoundType.NIGHT_CRICKETS to 0.18f)
        ),
        BinauralMode(
            id = "meditacion_crepuscular",
            nameEs = "Paz Crepuscular",
            taglineEs = "Transición vigilia-sueño (7.83 Hz Schumann)",
            descriptionEs = "Sintonizado en la Resonancia Schumann (7.83 Hz), en la frontera entre Alfa y Theta. Pensado para ir preparando la mente antes de dormir.",
            categoryBadge = "DESCANSO PROFUNDO",
            carrierHz = 160f,
            beatHz = 7.83f,
            band = BrainwaveBand.THETA,
            recommendedMinutes = 35,
            benefitsEs = listOf("Armonización en 7.83 Hz (Resonancia Schumann)", "Arroyo de montaña y noche estrellada en equilibrio"),
            defaultNatureMix = mapOf(NatureSoundType.RIVER_STREAM to 0.38f, NatureSoundType.NIGHT_CRICKETS to 0.35f, NatureSoundType.WARM_HEARTH to 0.20f)
        )
    )
}

data class BinauralUiState(
    val selectedTab: Int = 0,
    val selectedMode: BinauralMode = BinauralCatalog.modes.first(),
    val isPlaying: Boolean = false,
    val carrierHz: Float = BinauralCatalog.modes.first().carrierHz,
    val beatHz: Float = BinauralCatalog.modes.first().beatHz,
    val binauralVolume: Float = 0.68f,
    val natureVolumes: Map<NatureSoundType, Float> = BinauralCatalog.modes.first().defaultNatureMix,
    val timerTotalMinutes: Int = 25,
    val timerRemainingSeconds: Int = 25 * 60,
    val isTimerActive: Boolean = true,
    val progressiveFadeOut: Boolean = true,
    val isochronicPulseEnabled: Boolean = false,
    val swapStereoChannels: Boolean = false,
    val savedPresets: List<SavedMixPreset> = listOf(
        SavedMixPreset(
            id = "preset_lluvia_delta",
            name = "Tormenta Lejana & Delta",
            modeId = "sueno_profundo",
            carrierHz = 136.1f,
            beatHz = 2.2f,
            binauralVolume = 0.62f,
            natureVolumes = mapOf(NatureSoundType.RAIN to 0.58f, NatureSoundType.WARM_HEARTH to 0.30f)
        ),
        SavedMixPreset(
            id = "preset_siesta_bosque",
            name = "Siesta en el Pinar",
            modeId = "siesta_theta",
            carrierHz = 192f,
            beatHz = 5.5f,
            binauralVolume = 0.65f,
            natureVolumes = mapOf(NatureSoundType.FOREST_WIND to 0.48f, NatureSoundType.RIVER_STREAM to 0.36f)
        )
    ),
    val showSavePresetDialog: Boolean = false,
    val showSleepTimerSheet: Boolean = false
) {
    val activeBrainwaveBand: BrainwaveBand
        get() = BrainwaveBand.fromBeatFrequency(beatHz)

    val leftEarHz: Float
        get() = if (!swapStereoChannels) carrierHz - beatHz * 0.5f else carrierHz + beatHz * 0.5f

    val rightEarHz: Float
        get() = if (!swapStereoChannels) carrierHz + beatHz * 0.5f else carrierHz - beatHz * 0.5f

    val activeNatureCount: Int
        get() = natureVolumes.count { it.value > 0.02f }
}
