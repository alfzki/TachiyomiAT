package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Contextual popup menu for translation bubbles
 * Shows when user taps on a translation bubble
 *
 * @param expanded Whether the menu is currently visible
 * @param onDismiss Callback when user dismisses menu (taps outside)
 * @param onDelete Callback when user clicks "Delete bubble" option
 * @param offset Position offset for the menu relative to bubble
 */
@Composable
fun TranslationBubbleMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    offset: DpOffset = DpOffset(8.dp, 8.dp),
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(ATMR.strings.action_delete_bubble))
                }
            },
            onClick = {
                onDelete()
                onDismiss()
            },
        )
    }
}
