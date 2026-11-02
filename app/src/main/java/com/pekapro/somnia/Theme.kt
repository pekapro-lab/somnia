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
// 1. PALETA DE COLORES Y TEMA (SANTUARIO NOCTURNO)
// =========================================================================================

val MidnightIndigo = Color(0xFF080B1A)
val DeepObsidian = Color(0xFF0D1226)
val ElevatedNightCard = Color(0xFF151C38)
val LuminousGold = Color(0xFFF4C464)
val SoftAmber = Color(0xFFE5A93C)
val EtherealViolet = Color(0xFFA78BFA)
val BioluminescentTeal = Color(0xFF2DD4BF)
val CalmCyan = Color(0xFF38BDF8)
val RoseQuartz = Color(0xFFF472B6)
val StarlightWhite = Color(0xFFF8FAFC)
val MoonbeamSilver = Color(0xFFCBD5E1)

private val SomniaColorScheme = darkColorScheme(
    primary = LuminousGold,
    onPrimary = Color(0xFF1C1300),
    secondary = EtherealViolet,
    tertiary = BioluminescentTeal,
    background = MidnightIndigo,
    onBackground = StarlightWhite,
    surface = DeepObsidian,
    onSurface = StarlightWhite,
    surfaceVariant = ElevatedNightCard,
    onSurfaceVariant = MoonbeamSilver
)

@Composable
fun SomniaBinauralTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SomniaColorScheme,
        content = content
    )
}
