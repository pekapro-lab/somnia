package com.pekapro.somnia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

/** Capa fina sobre [SomniaController]: el estado y el audio viven fuera del ViewModel. */
class BinauralViewModel(application: Application) : AndroidViewModel(application) {
    private val c = SomniaController.also { it.init(application) }
    val uiState: StateFlow<BinauralUiState> = c.uiState

    fun selectTab(index: Int) = c.update { it.copy(selectedTab = index) }
    fun selectMode(mode: BinauralMode, autoPlay: Boolean = false) = c.selectMode(mode, autoPlay)
    fun togglePlayback() = c.togglePlayback()
    fun setCarrierFrequency(hz: Float) = c.setCarrierFrequency(hz)
    fun setBeatFrequency(hz: Float) = c.setBeatFrequency(hz)
    fun setBinauralVolume(volume: Float) = c.setBinauralVolume(volume)
    fun setNatureVolume(type: NatureSoundType, volume: Float) = c.setNatureVolume(type, volume)
    fun clearAllNatureSounds() = c.setAllNatureVolumes(emptyMap())

    fun applyNatureQuickPreset(presetName: String) {
        val mix = when (presetName) {
            "lluvia_hoguera" -> mapOf(NatureSoundType.RAIN to 0.55f, NatureSoundType.WARM_HEARTH to 0.45f)
            "oceano_estrellas" -> mapOf(NatureSoundType.OCEAN to 0.60f, NatureSoundType.NIGHT_CRICKETS to 0.35f)
            "bosque_arroyo" -> mapOf(NatureSoundType.FOREST_WIND to 0.50f, NatureSoundType.RIVER_STREAM to 0.45f)
            else -> emptyMap()
        }
        c.setAllNatureVolumes(mix)
    }

    fun setTimerMinutes(minutes: Int) = c.setTimerMinutes(minutes)
    fun toggleTimerActive(active: Boolean) = c.toggleTimerActive(active)
    fun toggleProgressiveFadeOut(enabled: Boolean) = c.toggleProgressiveFadeOut(enabled)
    fun toggleIsochronicPulse(enabled: Boolean) = c.toggleIsochronicPulse(enabled)
    fun toggleSwapStereoChannels(swap: Boolean) = c.toggleSwapStereoChannels(swap)
    fun setShowSavePresetDialog(show: Boolean) = c.update { it.copy(showSavePresetDialog = show) }
    fun setShowSleepTimerSheet(show: Boolean) = c.update { it.copy(showSleepTimerSheet = show) }
    fun saveCurrentMixPreset(customName: String) = c.saveCurrentMixPreset(customName)
    fun applySavedPreset(preset: SavedMixPreset) = c.applySavedPreset(preset)
    fun deleteSavedPreset(presetId: String) = c.deleteSavedPreset(presetId)
    // Sin onCleared(): el audio sigue sonando aunque se cierre la pantalla; lo gestiona el servicio.
}
