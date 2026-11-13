package io.github.tomlmbl.launcher.settings

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.Tokens

/** Licenses shipped in the APK: static texts in `res/raw`, no dependency. */
internal enum class License(@param:RawRes val text: Int, @param:StringRes val label: Int) {
    GPL3(R.raw.license_gpl3, R.string.license_gpl3),
    APACHE2(R.raw.license_apache2, R.string.license_apache2),
    MIT(R.raw.license_mit, R.string.license_mit),
    CC_BY4(R.raw.license_cc_by4, R.string.license_cc_by4),
}

/**
 * Third-party components in the APK and their authors. Proper names are not translated; [notice]: origin and
 * modifications, required by Apache-2.0 (4 b) and CC BY 4.0 (3 a 1 B).
 */
internal enum class Component(val title: String, val holder: String, val license: License, @param:StringRes val notice: Int? = null) {
    // Title: the app name (`app_name`).
    LAUNCHER("", "Tom Le Masson Banning Lover", License.GPL3, R.string.notice_launcher),
    COLOR("Material Color Utilities", "Google LLC", License.APACHE2, R.string.notice_color),
    SYMBOLS("Material Symbols", "Google LLC", License.APACHE2),
    ANDROIDX("AndroidX, Jetpack Compose", "The Android Open Source Project", License.APACHE2),
    KOTLIN("Kotlin, kotlinx.coroutines", "JetBrains s.r.o.", License.APACHE2),
    OKIO("Okio", "Square, Inc.", License.APACHE2),
    METEOCONS("Meteocons", "Bas Milius", License.MIT, R.string.notice_meteocons),
    GEONAMES("GeoNames", "geonames.org", License.CC_BY4, R.string.notice_geonames),
    OPEN_METEO("Open-Meteo", "open-meteo.com", License.CC_BY4, R.string.notice_open_meteo),
}

@Composable
private fun Component.displayTitle() = if (this == Component.LAUNCHER) stringResource(R.string.app_name) else title

/** "Open source licenses": the app, then reused code and icons, then data; a row opens the text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onOpen: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) = OnSurface {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(title = { Text(stringResource(R.string.open_source_licenses)) }, navigationIcon = { BackButton(onBack) }, scrollBehavior = scroll)
        Body {
            Group(stringResource(R.string.licenses_this_app)) { Row(Component.LAUNCHER, onOpen) }
            Group(stringResource(R.string.licenses_code_and_icons)) {
                listOf(Component.COLOR, Component.SYMBOLS, Component.ANDROIDX, Component.KOTLIN, Component.OKIO, Component.METEOCONS)
                    .forEach { Row(it, onOpen) }
            }
            Group(stringResource(R.string.licenses_data)) { listOf(Component.GEONAMES, Component.OPEN_METEO).forEach { Row(it, onOpen) } }
        }
    }
}

@Composable
private fun Row(component: Component, onOpen: (String) -> Unit) {
    SettingRow(
        R.drawable.ic_info, component.displayTitle(), component.holder + " · " + stringResource(component.license.label),
        modifier = Modifier.clickable(role = Role.Button) { onOpen(component.name) },
    ) { Chevron() }
}

/**
 * License text, preceded by the component's origin and modifications where relevant; the file's lines are joined
 * back into paragraphs ([LicenseText.reflow]) so they wrap like any text on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseTextScreen(name: String, onBack: () -> Unit, modifier: Modifier = Modifier) = OnSurface {
    val component = Component.entries.firstOrNull { it.name == name } ?: Component.LAUNCHER
    val resources = LocalResources.current
    val text = remember(component) { LicenseText.reflow(resources.openRawResource(component.license.text).bufferedReader().use { it.readText() }) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(title = { Text(component.displayTitle()) }, navigationIcon = { BackButton(onBack) }, scrollBehavior = scroll)
        Body {
            component.notice?.let {
                BasicText(stringResource(it), style = Tokens.sheetBody, modifier = Modifier.padding(horizontal = Tokens.Space.l, vertical = Tokens.Space.m))
            }
            BasicText(text, style = Tokens.supporting, modifier = Modifier.padding(start = Tokens.Space.l, end = Tokens.Space.l, top = Tokens.Space.m, bottom = Tokens.Space.xl))
        }
    }
}

/** Formatting of license texts, in pure Kotlin for tests. */
internal object LicenseText {
    /** Length from which a line counts as prose wrapped by the file, to be joined with the next one. */
    private const val WRAPPED = 50

    /**
     * Hand-wrapped lines joined into paragraphs; short lines (titles, centered headers) kept alone; blank lines
     * reduced to one, which separates paragraphs.
     */
    fun reflow(source: String): String = buildString {
        var previous: String? = null
        for (raw in source.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> { if (previous != null && previous.isNotEmpty()) append("\n\n"); previous = if (previous == null) null else "" }
                previous == null || previous.isEmpty() -> append(line)
                previous.length >= WRAPPED -> append(' ').append(line)
                else -> append('\n').append(line)
            }
            if (line.isNotEmpty()) previous = line
        }
    }.trimEnd()
}
