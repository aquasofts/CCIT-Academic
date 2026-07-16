package edu.ccit.webvpn.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.android.material.color.utilities.Variant
import edu.ccit.webvpn.settings.preference.SettingsSegmentedPrefsScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsPreferencesTest {
    @Test
    fun newInstallDefaultsToDynamicBrownTheme() {
        assertEquals(
            ThemeSettings(
                theme = Theme.DYNAMIC,
                customColor = 0xFF705D61.toInt(),
                customVariant = Variant.CONTENT,
            ),
            readThemeSettings(emptyPreferences()),
        )
    }

    @Test
    fun invalidStoredEnumsFallBackSafely() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("theme") to "REMOVED_THEME",
            stringPreferencesKey("custom_variant") to "REMOVED_VARIANT",
            stringPreferencesKey("dark_preference") to "REMOVED_MODE",
        )

        assertEquals(Theme.DYNAMIC, readThemeSettings(preferences).theme)
        assertEquals(Variant.CONTENT, readThemeSettings(preferences).customVariant)
        assertEquals(DarkPreference.FOLLOW_SYSTEM, readUiSettings(preferences).darkPreference)
    }

    @Test
    fun migratedAcademicPreferencesDropFavoritesAndKeepOrder() {
        val migrated = mutablePreferencesOf(
            stringSetPreferencesKey("favorite_ids") to setOf("grades", "timetable"),
            stringPreferencesKey("feature_order") to "timetable,grades,evaluation",
        )

        assertEquals(
            AcademicFeatureSettings(
                orderIds = listOf("timetable", "grades", "evaluation"),
            ),
            readAcademicFeatureSettings(migrated),
        )
    }

    @Test
    fun preferenceModelsRoundTripWithoutReflection() {
        val preferences = mutablePreferencesOf()
        val theme = ThemeSettings(Theme.PINK, 0xFF123456.toInt(), Variant.EXPRESSIVE)
        val ui = UISettings(bottomNavFloating = true, reduceEffect = true)
        val features = AcademicFeatureSettings(listOf("grades", "timetable"))

        writeThemeSettings(preferences, theme)
        writeUiSettings(preferences, ui)
        writeAcademicFeatureSettings(preferences, features)

        assertEquals(theme, readThemeSettings(preferences))
        assertEquals(ui, readUiSettings(preferences))
        assertEquals(features, readAcademicFeatureSettings(preferences))
        assertFalse(
            SettingsSegmentedPrefsScope::class.java.methods.any { method ->
                method.parameterTypes.any { it.name == "kotlin.reflect.KProperty1" }
            },
        )
    }
}
