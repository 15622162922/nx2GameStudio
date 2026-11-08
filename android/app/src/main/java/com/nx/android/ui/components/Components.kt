package com.nx.android.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.ui.theme.GlassPanel
import com.nx.android.ui.theme.NxPalette

/** A titled section: the screen is a few glass cards, each with its heading */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassPanel(modifier = modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** The main action: a cream capsule, like the launcher's */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = NxPalette.Surface,
            disabledContentColor = NxPalette.Unbuilt,
        ),
        modifier = modifier.height(46.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** The secondary action: a quiet capsule */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = modifier.height(44.dp),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

/** A status dot: one color per state, the same everywhere (library cards, builds rows, settings) */
@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** A small line of muted text */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = size,
        modifier = modifier,
    )
}

/** The pages' scroll states, saveable across the tab switches (a hidden page's composition goes away; the scroll position must not) */
@Composable
fun rememberPageScroll(): ScrollState =
    rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

@Composable
fun rememberLibraryGrid(): LazyGridState =
    rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
