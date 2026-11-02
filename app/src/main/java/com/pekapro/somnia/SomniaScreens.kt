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
// 5. ACTIVIDAD PRINCIPAL Y COMPONENTES JETPACK COMPOSE
// =========================================================================================


private data class TabItem(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val appTabs = listOf(
    TabItem(0, "Modos", Icons.Filled.Bedtime, Icons.Outlined.Bedtime),
    TabItem(1, "Naturaleza", Icons.Filled.Forest, Icons.Outlined.Forest),
    TabItem(2, "Laboratorio Hz", Icons.Filled.Tune, Icons.Outlined.Tune),
    TabItem(3, "Ciencia", Icons.Filled.Psychology, Icons.Outlined.Psychology)
)

@Composable
fun SomniaBinauralScreen(viewModel: BinauralViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.selectedTab != 0) {
        BackHandler { viewModel.selectTab(0) }
    }

    if (uiState.showSleepTimerSheet) {
        SleepTimerSheet(
            uiState = uiState,
            onDismiss = { viewModel.setShowSleepTimerSheet(false) },
            onSelectMinutes = { viewModel.setTimerMinutes(it) },
            onToggleTimerActive = { viewModel.toggleTimerActive(it) },
            onToggleFadeOut = { viewModel.toggleProgressiveFadeOut(it) }
        )
    }

    if (uiState.showSavePresetDialog) {
        SavePresetDialog(
            defaultModeName = uiState.selectedMode.nameEs,
            onDismiss = { viewModel.setShowSavePresetDialog(false) },
            onConfirmSave = { viewModel.saveCurrentMixPreset(it) }
        )
    }

    Scaffold(
        containerColor = MidnightIndigo,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = DeepObsidian, contentColor = StarlightWhite) {
                appTabs.forEach { tab ->
                    val selected = uiState.selectedTab == tab.index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab.index) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightIndigo,
                            selectedTextColor = LuminousGold,
                            indicatorColor = LuminousGold,
                            unselectedIconColor = MoonbeamSilver,
                            unselectedTextColor = MoonbeamSilver
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MidnightIndigo, DeepObsidian, MidnightIndigo)))
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Somnia Binaural", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite, fontWeight = FontWeight.Bold)
                            Text("Sintonización Cerebral & Naturaleza", style = MaterialTheme.typography.bodySmall, color = LuminousGold)
                            Text("Hecho con Grok", style = MaterialTheme.typography.labelSmall, color = MoonbeamSilver.copy(alpha = 0.7f))
                        }
                        Surface(
                            color = ElevatedNightCard,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Headphones, contentDescription = null, tint = BioluminescentTeal, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Estéreo 44.1 kHz", style = MaterialTheme.typography.labelSmall, color = StarlightWhite)
                            }
                        }
                    }
                }

                item {
                    HeroVisualizerStage(
                        uiState = uiState,
                        onTogglePlay = { viewModel.togglePlayback() },
                        onOpenTimerSheet = { viewModel.setShowSleepTimerSheet(true) },
                        onOpenSavePreset = { viewModel.setShowSavePresetDialog(true) }
                    )
                }

                item {
                    when (uiState.selectedTab) {
                        0 -> ModesSection(
                            uiState = uiState,
                            onSelectMode = { mode, play -> viewModel.selectMode(mode, play) },
                            onBinauralVolumeChange = { viewModel.setBinauralVolume(it) },
                            onApplySavedPreset = { viewModel.applySavedPreset(it) },
                            onDeleteSavedPreset = { viewModel.deleteSavedPreset(it) }
                        )
                        1 -> NatureMixerSection(
                            uiState = uiState,
                            onNatureVolumeChange = { type, vol -> viewModel.setNatureVolume(type, vol) },
                            onClearAllNature = { viewModel.clearAllNatureSounds() },
                            onApplyQuickMix = { viewModel.applyNatureQuickPreset(it) }
                        )
                        2 -> FrequencyLabSection(
                            uiState = uiState,
                            onCarrierChange = { viewModel.setCarrierFrequency(it) },
                            onBeatChange = { viewModel.setBeatFrequency(it) },
                            onToggleIsochronic = { viewModel.toggleIsochronicPulse(it) },
                            onToggleSwapStereo = { viewModel.toggleSwapStereoChannels(it) }
                        )
                        else -> ScienceSection()
                    }
                }
            }
        }
    }
}

@Composable
fun HeroVisualizerStage(
    uiState: BinauralUiState,
    onTogglePlay: () -> Unit,
    onOpenTimerSheet: () -> Unit,
    onOpenSavePreset: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (uiState.isPlaying) 2600 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val band = uiState.activeBrainwaveBand

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(ElevatedNightCard, DeepObsidian, MidnightIndigo)))
            .border(1.dp, band.accentColor.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .align(Alignment.Center)
        ) {
            val w = size.width
            val h = size.height
            val midY = h * 0.52f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(band.accentColor.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(w * 0.5f, midY),
                    radius = w * 0.45f
                ),
                center = Offset(w * 0.5f, midY),
                radius = w * 0.45f
            )

            val leftPath = Path()
            val rightPath = Path()
            val interferencePath = Path()

            val steps = 110
            val stepX = w / steps
            val amplitude = if (uiState.isPlaying) h * 0.17f * (0.4f + uiState.binauralVolume * 0.6f) else h * 0.06f
            val freqVisualLeft = 2.4f + (uiState.carrierHz / 120f)
            val freqVisualBeat = (uiState.beatHz / 3.5f).coerceIn(0.4f, 4.2f)

            for (i in 0..steps) {
                val x = i * stepX
                val normX = i.toFloat() / steps
                val window = sin(normX * PI).toFloat()

                val yLeft = midY + sin(normX * freqVisualLeft * 2f * PI + wavePhase).toFloat() * amplitude * window
                val yRight = midY + sin(normX * (freqVisualLeft + freqVisualBeat * 0.35f) * 2f * PI - wavePhase * 0.8f).toFloat() * amplitude * window
                val yBeat = midY + ((yLeft - midY) + (yRight - midY)) * 0.58f

                if (i == 0) {
                    leftPath.moveTo(x, yLeft)
                    rightPath.moveTo(x, yRight)
                    interferencePath.moveTo(x, yBeat)
                } else {
                    leftPath.lineTo(x, yLeft)
                    rightPath.lineTo(x, yRight)
                    interferencePath.lineTo(x, yBeat)
                }
            }

            drawPath(leftPath, color = EtherealViolet.copy(alpha = 0.5f), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(rightPath, color = BioluminescentTeal.copy(alpha = 0.5f), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path = interferencePath,
                brush = Brush.horizontalGradient(listOf(EtherealViolet, band.accentColor, LuminousGold, BioluminescentTeal)),
                style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = band.accentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, band.accentColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(band.accentColor))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${band.titleEs} • ${String.format(Locale.US, "%.1f", uiState.beatHz)} Hz",
                            style = MaterialTheme.typography.labelMedium,
                            color = StarlightWhite
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onOpenTimerSheet,
                        color = MidnightIndigo.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Temporizador",
                                tint = if (uiState.isTimerActive) LuminousGold else MoonbeamSilver,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isTimerActive) formatSeconds(uiState.timerRemainingSeconds) else "Continuo",
                                style = MaterialTheme.typography.labelMedium,
                                color = StarlightWhite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onOpenSavePreset) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Guardar mezcla", tint = LuminousGold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(uiState.selectedMode.categoryBadge, style = MaterialTheme.typography.labelSmall, color = LuminousGold, fontWeight = FontWeight.Bold)
            Text(uiState.selectedMode.nameEs, style = MaterialTheme.typography.headlineMedium, color = StarlightWhite, fontWeight = FontWeight.Bold)
            Text(uiState.selectedMode.taglineEs, style = MaterialTheme.typography.bodyMedium, color = MoonbeamSilver)
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MidnightIndigo.copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("IZQ (L)", style = MaterialTheme.typography.labelSmall, color = MoonbeamSilver)
                        Text("${String.format(Locale.US, "%.1f", uiState.leftEarHz)} Hz", style = MaterialTheme.typography.titleSmall, color = EtherealViolet, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = LuminousGold, modifier = Modifier.size(18.dp))
                        Text("Δ ${String.format(Locale.US, "%.1f", uiState.beatHz)} Hz", style = MaterialTheme.typography.labelSmall, color = LuminousGold, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DER (R)", style = MaterialTheme.typography.labelSmall, color = MoonbeamSilver)
                        Text("${String.format(Locale.US, "%.1f", uiState.rightEarHz)} Hz", style = MaterialTheme.typography.titleSmall, color = BioluminescentTeal, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                FilledIconButton(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isPlaying) LuminousGold else band.accentColor,
                        contentColor = MidnightIndigo
                    ),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModesSection(
    uiState: BinauralUiState,
    onSelectMode: (BinauralMode, Boolean) -> Unit,
    onBinauralVolumeChange: (Float) -> Unit,
    onApplySavedPreset: (SavedMixPreset) -> Unit,
    onDeleteSavedPreset: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            color = ElevatedNightCard.copy(alpha = 0.85f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Intensidad de Onda Binaural", style = MaterialTheme.typography.titleSmall, color = StarlightWhite)
                    Text("${(uiState.binauralVolume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = LuminousGold)
                }
                Slider(
                    value = uiState.binauralVolume,
                    onValueChange = onBinauralVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = LuminousGold, activeTrackColor = LuminousGold, inactiveTrackColor = MidnightIndigo)
                )
            }
        }

        BinauralCatalog.modes.forEach { mode ->
            val isSelected = uiState.selectedMode.id == mode.id
            val accent = mode.band.accentColor
            val icon = when (mode.id) {
                "descanso_alfa" -> Icons.Default.Spa
                "siesta_theta" -> Icons.Default.AccessTime
                "sueno_profundo" -> Icons.Default.Bedtime
                "sueno_reparador" -> Icons.Default.Favorite
                else -> Icons.Default.NightsStay
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ElevatedNightCard)
                    .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .clickable { onSelectMode(mode, false) }
                    .padding(18.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = mode.nameEs, tint = accent)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("${mode.categoryBadge} • ${mode.recommendedMinutes} min", style = MaterialTheme.typography.labelSmall, color = accent)
                                Text(mode.nameEs, style = MaterialTheme.typography.titleLarge, color = StarlightWhite)
                                Text(mode.taglineEs, style = MaterialTheme.typography.bodySmall, color = MoonbeamSilver)
                            }
                        }
                        Button(
                            onClick = { onSelectMode(mode, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MidnightIndigo),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(if (uiState.isPlaying && isSelected) "Activo" else "Iniciar", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(mode.descriptionEs, style = MaterialTheme.typography.bodyMedium, color = MoonbeamSilver)
                }
            }
        }

        if (uiState.savedPresets.isNotEmpty()) {
            Text("Mis Mezclas Guardadas", style = MaterialTheme.typography.titleLarge, color = StarlightWhite)
            uiState.savedPresets.forEach { preset ->
                Surface(
                    onClick = { onApplySavedPreset(preset) },
                    color = ElevatedNightCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, EtherealViolet.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LuminousGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(preset.name, style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                                Text("Pulso ${preset.beatHz} Hz • Portadora ${preset.carrierHz.toInt()} Hz", style = MaterialTheme.typography.bodySmall, color = MoonbeamSilver)
                            }
                        }
                        IconButton(onClick = { onDeleteSavedPreset(preset.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = MoonbeamSilver)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NatureMixerSection(
    uiState: BinauralUiState,
    onNatureVolumeChange: (NatureSoundType, Float) -> Unit,
    onClearAllNature: () -> Unit,
    onApplyQuickMix: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Sonidos de la Naturaleza", style = MaterialTheme.typography.titleLarge, color = StarlightWhite)
            OutlinedButton(onClick = onClearAllNature, shape = RoundedCornerShape(50)) {
                Icon(Icons.Default.VolumeOff, contentDescription = null, tint = MoonbeamSilver, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Silenciar", color = StarlightWhite)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = false, onClick = { onApplyQuickMix("lluvia_hoguera") }, label = { Text("Lluvia & Hoguera") })
            FilterChip(selected = false, onClick = { onApplyQuickMix("oceano_estrellas") }, label = { Text("Océano & Estrellas") })
            FilterChip(selected = false, onClick = { onApplyQuickMix("bosque_arroyo") }, label = { Text("Bosque & Arroyo") })
        }

        NatureSoundType.entries.forEach { sound ->
            val vol = uiState.natureVolumes[sound] ?: 0f
            val isActive = vol > 0.02f
            val icon = when (sound) {
                NatureSoundType.RAIN -> Icons.Default.WaterDrop
                NatureSoundType.OCEAN -> Icons.Default.Waves
                NatureSoundType.FOREST_WIND -> Icons.Default.Air
                NatureSoundType.NIGHT_CRICKETS -> Icons.Default.NightsStay
                NatureSoundType.RIVER_STREAM -> Icons.Default.Water
                NatureSoundType.WARM_HEARTH -> Icons.Default.LocalFireDepartment
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ElevatedNightCard)
                    .border(1.dp, if (isActive) sound.defaultAccent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = sound.titleEs, tint = if (isActive) sound.defaultAccent else MoonbeamSilver)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(sound.titleEs, style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                                Text(sound.subtitleEs, style = MaterialTheme.typography.bodySmall, color = MoonbeamSilver)
                            }
                        }
                        Text(if (isActive) "${(vol * 100).toInt()}%" else "Apagado", color = sound.defaultAccent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = vol,
                        onValueChange = { onNatureVolumeChange(sound, it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = sound.defaultAccent, activeTrackColor = sound.defaultAccent, inactiveTrackColor = MidnightIndigo)
                    )
                }
            }
        }
    }
}

@Composable
fun FrequencyLabSection(
    uiState: BinauralUiState,
    onCarrierChange: (Float) -> Unit,
    onBeatChange: (Float) -> Unit,
    onToggleIsochronic: (Boolean) -> Unit,
    onToggleSwapStereo: (Boolean) -> Unit
) {
    val activeBand = uiState.activeBrainwaveBand
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Laboratorio de Frecuencias (Hz)", style = MaterialTheme.typography.titleLarge, color = StarlightWhite)

        Surface(color = ElevatedNightCard, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Pulso Binaural: ${String.format(Locale.US, "%.2f", uiState.beatHz)} Hz (${activeBand.titleEs})", style = MaterialTheme.typography.titleMedium, color = activeBand.accentColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onBeatChange((uiState.beatHz - 0.25f).coerceAtLeast(0.5f)) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }
                    Slider(
                        value = uiState.beatHz,
                        onValueChange = onBeatChange,
                        valueRange = 0.5f..15.0f,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = { onBeatChange((uiState.beatHz + 0.25f).coerceAtMost(15.0f)) }) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Frecuencia Portadora: ${String.format(Locale.US, "%.1f", uiState.carrierHz)} Hz", style = MaterialTheme.typography.titleMedium, color = LuminousGold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onCarrierChange((uiState.carrierHz - 2f).coerceAtLeast(70f)) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }
                    Slider(
                        value = uiState.carrierHz,
                        onValueChange = onCarrierChange,
                        valueRange = 70f..432f,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = { onCarrierChange((uiState.carrierHz + 2f).coerceAtMost(432f)) }) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }
            }
        }

        Surface(color = ElevatedNightCard, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Modulación Isocrónica Suave", color = StarlightWhite)
                    Switch(checked = uiState.isochronicPulseEnabled, onCheckedChange = onToggleIsochronic)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Invertir Canales Estéreo (L ↔ R)", color = StarlightWhite)
                    Switch(checked = uiState.swapStereoChannels, onCheckedChange = onToggleSwapStereo)
                }
            }
        }
    }
}

@Composable
fun ScienceSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Guía Neuroacústica de Ondas Cerebrales", style = MaterialTheme.typography.titleLarge, color = StarlightWhite)
        Surface(
            color = LuminousGold.copy(alpha = 0.10f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, LuminousGold.copy(alpha = 0.45f))
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = LuminousGold)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "No es un tratamiento médico. Usa auriculares para el efecto binaural. No lo uses conduciendo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightWhite
                )
            }
        }
        BrainwaveBand.entries.forEach { band ->
            Surface(
                color = ElevatedNightCard,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, band.accentColor.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(band.titleEs, style = MaterialTheme.typography.titleMedium, color = StarlightWhite, fontWeight = FontWeight.Bold)
                        Text(band.rangeHz, style = MaterialTheme.typography.labelLarge, color = band.accentColor)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(band.descriptionEs, style = MaterialTheme.typography.bodyMedium, color = MoonbeamSilver)
                }
            }
        }
        Text(
            "Hecho con Grok",
            style = MaterialTheme.typography.labelSmall,
            color = MoonbeamSilver.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    uiState: BinauralUiState,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onToggleTimerActive: (Boolean) -> Unit,
    onToggleFadeOut: (Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ElevatedNightCard, contentColor = StarlightWhite) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Temporizador de Sueño", style = MaterialTheme.typography.headlineSmall, color = StarlightWhite)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activar Temporizador", color = StarlightWhite)
                Switch(checked = uiState.isTimerActive, onCheckedChange = onToggleTimerActive)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60, 90, 180, 480).forEach { mins ->
                    FilterChip(
                        selected = uiState.isTimerActive && uiState.timerTotalMinutes == mins,
                        onClick = { onSelectMinutes(mins) },
                        label = { Text("$mins min") }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Atenuación Progresiva (Fade-Out)", color = StarlightWhite)
                Switch(checked = uiState.progressiveFadeOut, onCheckedChange = onToggleFadeOut)
            }
        }
    }
}

@Composable
fun SavePresetDialog(
    defaultModeName: String,
    onDismiss: () -> Unit,
    onConfirmSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("$defaultModeName Personalizado") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedNightCard,
        title = { Text("Guardar Mezcla", color = StarlightWhite) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la mezcla") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirmSave(name) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun formatSeconds(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
