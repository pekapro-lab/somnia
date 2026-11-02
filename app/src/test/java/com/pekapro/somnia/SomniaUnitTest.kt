package com.pekapro.somnia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SomniaUnitTest {
    @Test fun softLimitIsContinuousMonotonicAndBounded() {
        var prev = softLimit(-5.0)
        var x = -5.0 + 1e-4
        while (x <= 5.0) {
            val y = softLimit(x)
            assertTrue("salto en x=$x", abs(y - prev) < 1e-3)
            assertTrue("no monótono en x=$x", y >= prev)
            assertTrue(abs(y) < SOFT_LIMIT_CEILING)
            prev = y; x += 1e-4
        }
        assertEquals(0.1, softLimit(0.1), 2e-3) // casi lineal en señales pequeñas
        assertTrue(abs(softLimit(1.2) - softLimit(1.2001)) < 1e-4)
    }

    @Test fun presetsRoundTripThroughJson() {
        val presets = BinauralUiState().savedPresets
        val decoded = PresetCodec.decodePresets(PresetCodec.encodePresets(presets))
        assertEquals(presets.map { it.id to it.name }, decoded.map { it.id to it.name })
        assertEquals(0.58f, decoded[0].natureVolumes[NatureSoundType.RAIN]!!, 1e-6f)
        assertEquals(0f, decoded[0].natureVolumes[NatureSoundType.OCEAN]!!, 1e-6f)
    }

    @Test fun noMedicalClaimsInTexts() {
        val texts = BrainwaveBand.entries.map { it.descriptionEs } +
            BinauralCatalog.modes.flatMap { listOf(it.descriptionEs, it.taglineEs) + it.benefitsEs }
        val banned = listOf("regeneración celular", "hormona del crecimiento", "cortisol")
        texts.forEach { t -> banned.forEach { b -> assertTrue("$b en: $t", !t.lowercase().contains(b)) } }
    }
}
