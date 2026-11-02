package com.pekapro.somnia

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.somniaDataStore by preferencesDataStore(name = "somnia_preferences")

/**
 * Fuente única de verdad del estado de reproducción. Vive a nivel de proceso (no del ViewModel),
 * de modo que el motor de audio y el temporizador siguen funcionando con la pantalla apagada.
 * El [SomniaPlaybackService] en primer plano mantiene vivo el proceso mientras suena.
 */
object SomniaController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val audioEngine = BinauralAudioEngine()
    private val _uiState = MutableStateFlow(BinauralUiState())
    val uiState: StateFlow<BinauralUiState> = _uiState.asStateFlow()

    private lateinit var appContext: Context
    private var timerJob: Job? = null
    private var persistJob: Job? = null
    private var initialized = false

    private val keyLastModeId = stringPreferencesKey("last_mode_id")
    private val keyBinauralVol = floatPreferencesKey("binaural_vol")
    private val keyCarrier = floatPreferencesKey("carrier_hz")
    private val keyBeat = floatPreferencesKey("beat_hz")
    private val keyNature = stringPreferencesKey("nature_volumes_json")
    private val keyPresets = stringPreferencesKey("presets_json")
    private val keyTimerMinutes = intPreferencesKey("timer_minutes")
    private val keyTimerActive = booleanPreferencesKey("timer_active")
    private val keyFade = booleanPreferencesKey("fade_out")
    private val keyIso = booleanPreferencesKey("isochronic")
    private val keySwap = booleanPreferencesKey("swap_stereo")

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        syncEngineWithState(_uiState.value)
        scope.launch {
            try {
                val prefs = appContext.somniaDataStore.data.first()
                _uiState.update { restore(it, prefs) }
                syncEngineWithState(_uiState.value)
            } catch (_: Exception) {
            }
        }
    }

    private fun restore(s: BinauralUiState, p: Preferences): BinauralUiState {
        val mode = BinauralCatalog.modes.find { it.id == p[keyLastModeId] } ?: s.selectedMode
        val nature = p[keyNature]?.let { runCatching { PresetCodec.decodeVolumes(JSONObject(it)) }.getOrNull() }
            ?: NatureSoundType.entries.associateWith { mode.defaultNatureMix[it] ?: 0f }
        val presets = p[keyPresets]?.let { runCatching { PresetCodec.decodePresets(it) }.getOrNull() } ?: s.savedPresets
        val minutes = (p[keyTimerMinutes] ?: mode.recommendedMinutes).coerceIn(5, 480)
        return s.copy(
            selectedMode = mode,
            carrierHz = (p[keyCarrier] ?: mode.carrierHz).coerceIn(70f, 432f),
            beatHz = (p[keyBeat] ?: mode.beatHz).coerceIn(0.5f, 15f),
            binauralVolume = (p[keyBinauralVol] ?: s.binauralVolume).coerceIn(0f, 1f),
            natureVolumes = nature,
            savedPresets = presets,
            timerTotalMinutes = minutes,
            timerRemainingSeconds = minutes * 60,
            isTimerActive = p[keyTimerActive] ?: s.isTimerActive,
            progressiveFadeOut = p[keyFade] ?: s.progressiveFadeOut,
            isochronicPulseEnabled = p[keyIso] ?: s.isochronicPulseEnabled,
            swapStereoChannels = p[keySwap] ?: s.swapStereoChannels
        )
    }

    /** Guardado con un pequeño retardo para no escribir en disco en cada paso de un slider. */
    private fun persist() {
        if (!initialized) return
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(350)
            val s = _uiState.value
            try {
                appContext.somniaDataStore.edit { p ->
                    p[keyLastModeId] = s.selectedMode.id
                    p[keyBinauralVol] = s.binauralVolume
                    p[keyCarrier] = s.carrierHz
                    p[keyBeat] = s.beatHz
                    p[keyNature] = PresetCodec.encodeVolumes(s.natureVolumes).toString()
                    p[keyPresets] = PresetCodec.encodePresets(s.savedPresets)
                    p[keyTimerMinutes] = s.timerTotalMinutes
                    p[keyTimerActive] = s.isTimerActive
                    p[keyFade] = s.progressiveFadeOut
                    p[keyIso] = s.isochronicPulseEnabled
                    p[keySwap] = s.swapStereoChannels
                }
            } catch (_: Exception) {
            }
        }
    }

    fun update(transform: (BinauralUiState) -> BinauralUiState) = _uiState.update(transform)

    /** ¿Ha empezado ya la cuenta atrás? (en marcha o pausada a medias) */
    private fun BinauralUiState.timerStarted(): Boolean =
        isPlaying || timerRemainingSeconds != timerTotalMinutes * 60

    fun selectMode(mode: BinauralMode, autoPlay: Boolean = false) {
        val fullNatureMap = NatureSoundType.entries.associateWith { mode.defaultNatureMix[it] ?: 0f }
        val wasPlaying = _uiState.value.isPlaying
        _uiState.update { current ->
            val keepTimer = current.isTimerActive && current.timerStarted()
            current.copy(
                selectedMode = mode,
                carrierHz = mode.carrierHz,
                beatHz = mode.beatHz,
                natureVolumes = fullNatureMap,
                timerTotalMinutes = if (keepTimer) current.timerTotalMinutes else mode.recommendedMinutes,
                timerRemainingSeconds = if (keepTimer) current.timerRemainingSeconds else mode.recommendedMinutes * 60
            )
        }
        syncEngineWithState(_uiState.value)
        if (autoPlay && !wasPlaying) play()
        persist()
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) pause() else play()
    }

    fun play() {
        _uiState.update { current ->
            val resetRemaining = if (current.timerRemainingSeconds <= 0) current.timerTotalMinutes * 60 else current.timerRemainingSeconds
            current.copy(isPlaying = true, timerRemainingSeconds = resetRemaining)
        }
        syncEngineWithState(_uiState.value)
        audioEngine.masterFadeMultiplier = 1f
        audioEngine.start()
        startTimerLoop()
        startService()
    }

    /** Pausa conservando el tiempo restante del temporizador. */
    fun pause() {
        _uiState.update { it.copy(isPlaying = false) }
        timerJob?.cancel()
        audioEngine.stop()
    }

    /** Detiene del todo: para el audio, reinicia el temporizador y cierra el servicio. */
    fun stop() {
        _uiState.update { it.copy(isPlaying = false, timerRemainingSeconds = it.timerTotalMinutes * 60) }
        timerJob?.cancel()
        audioEngine.stop()
        audioEngine.masterFadeMultiplier = 1f
        if (initialized) appContext.stopService(Intent(appContext, SomniaPlaybackService::class.java))
    }

    private fun startService() {
        if (!initialized) return
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, SomniaPlaybackService::class.java))
        } catch (_: Exception) {
            // Si el sistema no deja arrancar el servicio (p. ej. en segundo plano), el audio sigue mientras viva la app.
        }
    }

    fun setCarrierFrequency(hz: Float) {
        val clamped = hz.coerceIn(70f, 432f)
        _uiState.update { it.copy(carrierHz = clamped) }
        audioEngine.targetCarrierHz = clamped
        persist()
    }

    fun setBeatFrequency(hz: Float) {
        val clamped = hz.coerceIn(0.5f, 15.0f)
        _uiState.update { it.copy(beatHz = clamped) }
        audioEngine.targetBeatHz = clamped
        persist()
    }

    fun setBinauralVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(binauralVolume = clamped) }
        audioEngine.targetBinauralVolume = clamped
        persist()
    }

    fun setNatureVolume(type: NatureSoundType, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { current ->
            current.copy(natureVolumes = current.natureVolumes.toMutableMap().apply { put(type, clamped) })
        }
        audioEngine.setNatureVolume(type, clamped)
        persist()
    }

    fun setAllNatureVolumes(map: Map<NatureSoundType, Float>) {
        val full = NatureSoundType.entries.associateWith { map[it] ?: 0f }
        _uiState.update { it.copy(natureVolumes = full) }
        audioEngine.setAllNatureVolumes(full)
        persist()
    }

    fun setTimerMinutes(minutes: Int) {
        val valid = minutes.coerceIn(5, 480)
        _uiState.update { it.copy(timerTotalMinutes = valid, timerRemainingSeconds = valid * 60, isTimerActive = true) }
        audioEngine.masterFadeMultiplier = 1.0f
        persist()
    }

    fun toggleTimerActive(active: Boolean) {
        _uiState.update { it.copy(isTimerActive = active) }
        if (!active) audioEngine.masterFadeMultiplier = 1.0f
        persist()
    }

    fun toggleProgressiveFadeOut(enabled: Boolean) {
        _uiState.update { it.copy(progressiveFadeOut = enabled) }
        if (!enabled) audioEngine.masterFadeMultiplier = 1.0f
        persist()
    }

    fun toggleIsochronicPulse(enabled: Boolean) {
        _uiState.update { it.copy(isochronicPulseEnabled = enabled) }
        audioEngine.isochronicPulseEnabled = enabled
        persist()
    }

    fun toggleSwapStereoChannels(swap: Boolean) {
        _uiState.update { it.copy(swapStereoChannels = swap) }
        audioEngine.swapStereoChannels = swap
        persist()
    }

    fun saveCurrentMixPreset(customName: String) {
        val s = _uiState.value
        val trimmed = customName.trim().ifEmpty { "Mezcla Nocturna ${s.savedPresets.size + 1}" }
        val newPreset = SavedMixPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = trimmed,
            modeId = s.selectedMode.id,
            carrierHz = s.carrierHz,
            beatHz = s.beatHz,
            binauralVolume = s.binauralVolume,
            natureVolumes = s.natureVolumes
        )
        _uiState.update { it.copy(savedPresets = listOf(newPreset) + it.savedPresets, showSavePresetDialog = false) }
        persist()
    }

    fun applySavedPreset(preset: SavedMixPreset) {
        val matchingMode = BinauralCatalog.modes.find { it.id == preset.modeId } ?: _uiState.value.selectedMode
        val fullNature = NatureSoundType.entries.associateWith { preset.natureVolumes[it] ?: 0f }
        _uiState.update {
            it.copy(
                selectedMode = matchingMode,
                carrierHz = preset.carrierHz,
                beatHz = preset.beatHz,
                binauralVolume = preset.binauralVolume,
                natureVolumes = fullNature
            )
        }
        syncEngineWithState(_uiState.value)
        persist()
    }

    fun deleteSavedPreset(presetId: String) {
        _uiState.update { current -> current.copy(savedPresets = current.savedPresets.filterNot { it.id == presetId }) }
        persist()
    }

    private fun syncEngineWithState(state: BinauralUiState) {
        audioEngine.targetCarrierHz = state.carrierHz
        audioEngine.targetBeatHz = state.beatHz
        audioEngine.targetBinauralVolume = state.binauralVolume
        audioEngine.isochronicPulseEnabled = state.isochronicPulseEnabled
        audioEngine.swapStereoChannels = state.swapStereoChannels
        audioEngine.setAllNatureVolumes(state.natureVolumes)
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_uiState.value.isPlaying) {
                delay(1000L)
                val current = _uiState.value
                if (!current.isPlaying) break
                if (!current.isTimerActive) {
                    audioEngine.masterFadeMultiplier = 1.0f
                    continue
                }
                val nextRemaining = (current.timerRemainingSeconds - 1).coerceAtLeast(0)
                _uiState.update { it.copy(timerRemainingSeconds = nextRemaining) }

                if (current.progressiveFadeOut && nextRemaining in 1..60) {
                    audioEngine.masterFadeMultiplier = nextRemaining / 60f
                } else if (nextRemaining > 60) {
                    audioEngine.masterFadeMultiplier = 1.0f
                }

                if (nextRemaining <= 0) {
                    stop()
                    break
                }
            }
        }
    }
}

/** Serialización JSON (org.json) de las mezclas guardadas y volúmenes. */
object PresetCodec {
    fun encodeVolumes(v: Map<NatureSoundType, Float>): JSONObject =
        JSONObject().apply { v.forEach { (k, x) -> put(k.id, x.toDouble()) } }

    fun decodeVolumes(o: JSONObject): Map<NatureSoundType, Float> =
        NatureSoundType.entries.associateWith { o.optDouble(it.id, 0.0).toFloat().coerceIn(0f, 1f) }

    fun encodePresets(list: List<SavedMixPreset>): String = JSONArray().apply {
        list.forEach { p ->
            put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("modeId", p.modeId)
                put("carrierHz", p.carrierHz.toDouble()); put("beatHz", p.beatHz.toDouble())
                put("binauralVolume", p.binauralVolume.toDouble())
                put("nature", encodeVolumes(p.natureVolumes))
            })
        }
    }.toString()

    fun decodePresets(json: String): List<SavedMixPreset> {
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SavedMixPreset(
                id = o.getString("id"),
                name = o.getString("name"),
                modeId = o.getString("modeId"),
                carrierHz = o.getDouble("carrierHz").toFloat(),
                beatHz = o.getDouble("beatHz").toFloat(),
                binauralVolume = o.getDouble("binauralVolume").toFloat(),
                natureVolumes = decodeVolumes(o.optJSONObject("nature") ?: JSONObject())
            )
        }
    }
}
