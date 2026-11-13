package io.github.tomlmbl.launcher.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.Tokens

/** Project links. */
internal object ProjectLinks {
    const val SOURCE = "https://github.com/tomlmbl/cobble-home"
    const val ISSUES = "$SOURCE/issues"
    const val PRIVACY = "$SOURCE/blob/main/PRIVACY.md"
}

/**
 * "About": small bar with Back, header with the app icon, name and version; "Report an issue", "Source code" and
 * "Privacy" open the browser ([onOpenLink]); "Open source licenses" opens the licenses page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(version: String, onOpenLink: (String) -> Unit, onLicenses: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) = OnSurface {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(title = {}, navigationIcon = { BackButton(onBack) })
        Body {
            Header(version)
            Group(stringResource(R.string.feedback)) {
                LinkRow(R.drawable.ic_bug_report, stringResource(R.string.report_an_issue), stringResource(R.string.report_an_issue_hint)) { onOpenLink(ProjectLinks.ISSUES) }
            }
            Group(stringResource(R.string.project)) {
                LinkRow(R.drawable.ic_code, stringResource(R.string.source_code), ProjectLinks.SOURCE.removePrefix("https://")) { onOpenLink(ProjectLinks.SOURCE) }
                LinkRow(R.drawable.ic_privacy_tip, stringResource(R.string.privacy), stringResource(R.string.privacy_hint)) { onOpenLink(ProjectLinks.PRIVACY) }
                SettingRow(
                    R.drawable.ic_license, stringResource(R.string.open_source_licenses), stringResource(R.string.open_source_licenses_hint),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onLicenses),
                ) { Chevron() }
            }
            Spacer(Modifier.height(Tokens.Space.xxl))
        }
    }
}

/**
 * "About" header: app icon in a 72 dp disc, name and version aligned left, on the page's neutral card background
 * (`surfaceContainer`): the icon alone carries the brand, without clashing with dynamic colors. Radius 28, padding
 * 24 and 32. The adaptive foreground is 108 dp for 72 visible: centered and clipped.
 */
@Composable
private fun Header(version: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Tokens.Space.m)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = Tokens.Space.xxl, vertical = 32.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.ml),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(108.dp))
        }
        // Name and version grouped: the version closely follows the name, without a license mention.
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** Row that opens the browser: trailing `open_in_new` icon instead of the chevron. */
@Composable
private fun LinkRow(@DrawableRes icon: Int, title: String, supporting: String, onClick: () -> Unit) {
    SettingRow(icon, title, supporting, modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Glyph(R.drawable.ic_open_in_new, Tokens.glyphSize, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
