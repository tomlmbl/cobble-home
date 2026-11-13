package io.github.tomlmbl.launcher.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.context.Detection
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.context.Ssid
import io.github.tomlmbl.launcher.home.profileLabel
import io.github.tomlmbl.launcher.signals.WifiStatus
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.Tokens

/** Network removed from a profile, which "Undo" puts back. */
data class Unlinked(val ssid: Ssid, val profile: Profile)

/**
 * "Automatic switching": toggle, Wi-Fi state in "Now" (one at a time), networks of each selected profile with
 * "Use current network" or "Move … here". [current]: connected network read in the foreground, null if unknown or
 * redacted. [denied]: denied at the last dialog (toggle left off).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSwitchScreen(
    detection: Detection,
    selected: List<Profile>,
    status: WifiStatus,
    current: Ssid?,
    denied: Boolean,
    unlinked: Unlinked?,
    onEnabled: (Boolean) -> Unit,
    onUseCurrent: (Profile) -> Unit,
    onUnlink: (Ssid, Profile) -> Unit,
    onUndo: () -> Unit,
    onLossSeen: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAllowPrecise: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = OnSurface {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val on = detection.enabled
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.auto_switching)) },
            navigationIcon = { BackButton(onBack) },
            scrollBehavior = scroll,
        )
        Body {
            Group(null) {
                SettingRow(
                    R.drawable.ic_wifi, stringResource(R.string.switch_by_wifi),
                    stringResource(if (on) R.string.only_dock_changes else R.string.auto_off_hint),
                    modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onEnabled),
                ) { Switch(checked = on, onCheckedChange = null) }
            }
            if (detection.networksLost) {
                Group(null) {
                    SettingRow(R.drawable.ic_error, stringResource(R.string.networks_lost), stringResource(R.string.networks_lost_hint))
                    ActionRow(R.drawable.ic_check, stringResource(R.string.done), onLossSeen)
                }
            }
            // Out on leaving a registered network of another profile: what the card says with no known network.
            val departure = Profile.OUT.takeIf { it in selected && detection.acknowledged.let { left -> left != null && left != it } }
            if (on || denied) NowCard(status, current, current?.let { detection.networks[it] }, departure, onOpenLocationSettings, onOpenAppSettings, onAllowPrecise)
            selected.forEach { profile ->
                val name = profileLabel(profile)
                Group(name) {
                    val networks = detection.networksOf(profile).sortedBy { it.display().lowercase() }
                    networks.forEach { ssid ->
                        val label = ssid.display()
                        val unlink = stringResource(R.string.unlink_network, label)
                        SettingRow(R.drawable.ic_wifi, label, stringResource(R.string.connected).takeIf { ssid == current }) {
                            IconButton(onClick = { onUnlink(ssid, profile) }, modifier = Modifier.semantics { contentDescription = unlink }) {
                                Glyph(R.drawable.ic_close, Tokens.glyphSize, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (unlinked?.profile == profile) {
                        ActionRow(R.drawable.ic_wifi, stringResource(R.string.unlinked_undo, unlinked.ssid.display()), onUndo)
                    }
                    if (profile == Profile.OUT) {
                        SettingRow(R.drawable.ic_signal_wifi_0_bar, stringResource(R.string.leaving_networks), stringResource(R.string.switches_after_5s, name))
                    } else if (networks.isEmpty() && unlinked?.profile != profile) {
                        SettingRow(R.drawable.ic_touch_app, stringResource(R.string.no_network), stringResource(R.string.switch_to_by_hand, name))
                    }
                    val owner = current?.let { detection.networks[it] }
                    if (current != null && owner != profile) {
                        if (owner == null) ActionRow(R.drawable.ic_add, stringResource(R.string.use_current_network)) { onUseCurrent(profile) }
                        else ActionRow(R.drawable.ic_move_down, stringResource(R.string.move_here, current.display())) { onUseCurrent(profile) }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = Tokens.Space.l))
        }
    }
}

/**
 * "Now" card: the first cause preventing switching, otherwise the connected network and its profile.
 * [departure]: profile applied with no known network and Wi-Fi on, null if the current profile stays.
 */
@Composable
private fun NowCard(
    status: WifiStatus,
    current: Ssid?,
    linked: Profile?,
    departure: Profile?,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAllowPrecise: () -> Unit,
) {
    Group(stringResource(R.string.now)) {
        when (status) {
            WifiStatus.NO_PERMISSION -> {
                SettingRow(R.drawable.ic_location_disabled, stringResource(R.string.no_location_access), stringResource(R.string.switch_by_hand))
                ActionRow(R.drawable.ic_open_in_new, stringResource(R.string.allow_in_settings), onOpenAppSettings)
            }
            WifiStatus.APPROXIMATE -> {
                SettingRow(R.drawable.ic_my_location, stringResource(R.string.precise_needed), stringResource(R.string.precise_needed_hint))
                ActionRow(R.drawable.ic_arrow_forward, stringResource(R.string.allow_precise), onAllowPrecise)
            }
            WifiStatus.LOCATION_OFF -> {
                SettingRow(R.drawable.ic_location_off, stringResource(R.string.location_off), stringResource(R.string.location_off_hint))
                ActionRow(R.drawable.ic_open_in_new, stringResource(R.string.open_location_settings), onOpenLocationSettings)
            }
            WifiStatus.WIFI_OFF -> SettingRow(R.drawable.ic_wifi_off, stringResource(R.string.wifi_off), stringResource(R.string.profile_stays))
            WifiStatus.NOT_CONNECTED -> SettingRow(R.drawable.ic_signal_wifi_0_bar, stringResource(R.string.not_connected), stays(departure))
            WifiStatus.CONNECTED -> when {
                // Connected network with an unreadable (redacted) SSID: no departure, the profile stays.
                current == null -> SettingRow(R.drawable.ic_signal_wifi_0_bar, stringResource(R.string.not_connected), stays(null))
                linked != null -> SettingRow(R.drawable.ic_wifi, current.display(), stringResource(R.string.linked_to, profileLabel(linked)))
                departure != null -> SettingRow(R.drawable.ic_wifi_find, current.display(), stringResource(R.string.not_linked_switches, profileLabel(departure)))
                else -> SettingRow(R.drawable.ic_wifi_find, current.display(), stringResource(R.string.not_linked))
            }
        }
    }
}

@Composable
private fun stays(departure: Profile?) =
    departure?.let { stringResource(R.string.switches_to, profileLabel(it)) } ?: stringResource(R.string.profile_stays)

/** Single-line action row (56 dp): icon and label, the whole row tappable. */
@Composable
private fun ActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    SettingRow(icon, label, null, modifier = Modifier.clickable(role = Role.Button, onClick = onClick), minHeight = Tokens.settingActionRowHeight)
}

/**
 * Before the permission dialog: three points, then "Not now" or "Continue", which opens the system dialog
 * (precise and approximate location requested together).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainScreen(onContinue: () -> Unit, onNotNow: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) = OnSurface {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.switch_by_wifi)) },
            navigationIcon = { BackButton(onBack) },
            scrollBehavior = scroll,
        )
        Column(Modifier.weight(1f)) {
            Body {
                listOf(
                    Triple(R.drawable.ic_dock_to_bottom, R.string.explain_dock_title, R.string.explain_dock),
                    Triple(R.drawable.ic_location_on, R.string.explain_location_title, R.string.explain_location),
                    Triple(R.drawable.ic_lock, R.string.explain_local_title, R.string.explain_local),
                ).forEach { (icon, title, text) ->
                    Group(null) { SettingRow(icon, stringResource(title), stringResource(text)) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Tokens.Space.l),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.m, androidx.compose.ui.Alignment.End),
        ) {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now), style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = onContinue) { Text(stringResource(R.string.continue_action), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
