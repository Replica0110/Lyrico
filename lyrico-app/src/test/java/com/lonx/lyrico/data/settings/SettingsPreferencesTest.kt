package com.lonx.lyrico.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreferencesTest {
    @Test
    fun materialSelectionDoesNotEnableMissingBlurSwitch() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.FLOATING_BAR_EFFECT to FloatingBarEffect.LIQUID_GLASS.name,
        )

        assertEquals(FloatingBarEffect.LIQUID_GLASS, prefs.resolveFloatingBarEffect())
        assertFalse(prefs.resolveBarBlurEnabled())
    }

    @Test
    fun currentSwitchAndMaterialAreReadIndependently() {
        val prefs = mutablePreferencesOf(SettingsPreferenceKeys.BAR_BLUR_ENABLED to true)

        assertTrue(prefs.resolveBarBlurEnabled())
        assertEquals(SettingsDefaults.FLOATING_BAR_EFFECT, prefs.resolveFloatingBarEffect())

        prefs[SettingsPreferenceKeys.BAR_BLUR_ENABLED] = false
        prefs[SettingsPreferenceKeys.FLOATING_BAR_EFFECT] = FloatingBarEffect.LIQUID_GLASS.name

        assertFalse(prefs.resolveBarBlurEnabled())
        assertEquals(FloatingBarEffect.LIQUID_GLASS, prefs.resolveFloatingBarEffect())
    }

    @Test
    fun obsoleteSwitchesDoNotOverrideCurrentDefaults() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("floating_bar_blur_enabled") to true,
            booleanPreferencesKey("liquid_glass_enabled") to true,
        )

        assertFalse(prefs.resolveBarBlurEnabled())
        assertEquals(FloatingBarEffect.NONE, prefs.resolveFloatingBarEffect())
        assertFalse(prefs.contains(SettingsPreferenceKeys.BAR_BLUR_ENABLED))
        assertFalse(prefs.contains(SettingsPreferenceKeys.FLOATING_BAR_EFFECT))
    }
}
