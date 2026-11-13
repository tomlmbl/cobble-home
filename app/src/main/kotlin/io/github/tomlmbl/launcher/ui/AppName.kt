package io.github.tomlmbl.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import io.github.tomlmbl.launcher.R

/**
 * An app's name, followed by the briefcase glyph if it comes from another profile. Decorative glyph: the row carries
 * the system's description (`App.description`) for accessibility.
 */
@Composable
fun AppName(label: String, otherProfile: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style, modifier = Modifier.weight(1f, fill = false))
        if (otherProfile) {
            Spacer(Modifier.width(Tokens.profileGlyphGap))
            Glyph(R.drawable.ic_work_filled, Tokens.profileGlyphSize, Tokens.onWallpaperMuted)
        }
    }
}
