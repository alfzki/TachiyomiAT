package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt
import tachiyomi.presentation.core.util.collectAsState as collectAsStatePref

/**
 * Translation settings page for the reader settings dialog.
 *
 * Displays BOTH CJK and Latin profile settings that can be adjusted in real-time
 * while viewing a translated chapter. Settings are global and apply to all chapters
 * with the same profile type.
 */
@Composable
internal fun ColumnScope.TranslationPage(viewModel: ReaderSettingsViewModel) {
    val hasTranslations by viewModel.hasTranslationsFlow.collectAsState()
    val profileType by viewModel.currentProfileTypeFlow.collectAsState()

    if (!hasTranslations) {
        Text(
            text = stringResource(ATMR.strings.pref_translation_no_translations),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    // Show currently active profile indicator
    Text(
        text = stringResource(ATMR.strings.pref_translation_profile_active) + ": " +
            when (profileType) {
                ProfileType.CJK -> stringResource(ATMR.strings.pref_translation_profile_cjk)
                ProfileType.LATIN -> stringResource(ATMR.strings.pref_translation_profile_latin)
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    // Show BOTH profiles (CJK and Latin)
    CJKProfileSettings(viewModel)

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    LatinProfileSettings(viewModel)
}

@Composable
private fun ColumnScope.CJKProfileSettings(viewModel: ReaderSettingsViewModel) {
    val prefs = viewModel.translationPreferences

    // Section header
    Text(
        text = stringResource(ATMR.strings.pref_translation_profile_cjk),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val textMarginPref = prefs.cjkTextMargin
    val textMargin by textMarginPref.collectAsStatePref()

    val paddingMultPref = prefs.cjkPaddingMultiplier
    val paddingMult by paddingMultPref.collectAsStatePref()
    val paddingMultInt = (paddingMult * 100f).roundToInt()

    val ovalHeightPref = prefs.cjkOvalHeightMargin
    val ovalHeight by ovalHeightPref.collectAsStatePref()
    val ovalHeightInt = (ovalHeight * 100f).roundToInt()

    SliderItem(
        value = textMargin,
        valueRange = 0..8,
        label = stringResource(ATMR.strings.pref_translation_text_margin),
        valueString = "${textMargin}px",
        onChange = { textMarginPref.set(it) },
    )

    SliderItem(
        value = paddingMultInt,
        valueRange = 100..150,
        label = stringResource(ATMR.strings.pref_translation_padding),
        valueString = "$paddingMultInt%",
        onChange = { paddingMultPref.set(it / 100f) },
    )

    SliderItem(
        value = ovalHeightInt,
        valueRange = 50..95,
        label = stringResource(ATMR.strings.pref_translation_oval_height),
        valueString = "$ovalHeightInt%",
        onChange = { ovalHeightPref.set(it / 100f) },
    )

    // Reset button
    TextButton(
        onClick = { prefs.resetCJKDefaults() },
        modifier = Modifier
            .align(Alignment.End)
            .padding(horizontal = 8.dp),
    ) {
        Text(stringResource(ATMR.strings.pref_reset_defaults))
    }
}

@Composable
private fun ColumnScope.LatinProfileSettings(viewModel: ReaderSettingsViewModel) {
    val prefs = viewModel.translationPreferences

    // Section header
    Text(
        text = stringResource(ATMR.strings.pref_translation_profile_latin),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val textMarginPref = prefs.latinTextMargin
    val textMargin by textMarginPref.collectAsStatePref()

    val paddingMultPref = prefs.latinPaddingMultiplier
    val paddingMult by paddingMultPref.collectAsStatePref()
    val paddingMultInt = (paddingMult * 100f).roundToInt()

    val ovalHeightPref = prefs.latinOvalHeightMargin
    val ovalHeight by ovalHeightPref.collectAsStatePref()
    val ovalHeightInt = (ovalHeight * 100f).roundToInt()

    val hPaddingPref = prefs.latinHorizontalPadding
    val hPadding by hPaddingPref.collectAsStatePref()

    val vPaddingPref = prefs.latinVerticalPadding
    val vPadding by vPaddingPref.collectAsStatePref()

    SliderItem(
        value = textMargin,
        valueRange = 0..8,
        label = stringResource(ATMR.strings.pref_translation_text_margin),
        valueString = "${textMargin}px",
        onChange = { textMarginPref.set(it) },
    )

    SliderItem(
        value = paddingMultInt,
        valueRange = 100..150,
        label = stringResource(ATMR.strings.pref_translation_padding),
        valueString = "$paddingMultInt%",
        onChange = { paddingMultPref.set(it / 100f) },
    )

    SliderItem(
        value = ovalHeightInt,
        valueRange = 70..99,
        label = stringResource(ATMR.strings.pref_translation_oval_height),
        valueString = "$ovalHeightInt%",
        onChange = { ovalHeightPref.set(it / 100f) },
    )

    SliderItem(
        value = hPadding,
        valueRange = 2..12,
        label = stringResource(ATMR.strings.pref_translation_h_padding),
        valueString = "${hPadding}dp",
        onChange = { hPaddingPref.set(it) },
    )

    SliderItem(
        value = vPadding,
        valueRange = 2..10,
        label = stringResource(ATMR.strings.pref_translation_v_padding),
        valueString = "${vPadding}dp",
        onChange = { vPaddingPref.set(it) },
    )

    // Reset button
    TextButton(
        onClick = { prefs.resetLatinDefaults() },
        modifier = Modifier
            .align(Alignment.End)
            .padding(horizontal = 8.dp),
    ) {
        Text(stringResource(ATMR.strings.pref_reset_defaults))
    }
}
