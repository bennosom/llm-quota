package work.becode.llmquota

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val CARD_GAP = 12.dp

/** Top bar: app title and the add-account button. */
@Composable
fun DashboardHeader(onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            "LLM Quota",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = "Add account", tint = Poison)
        }
    }
}

/** First list entry: when the data was last refreshed. */
@Composable
fun StampLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = CARD_GAP),
    )
}
