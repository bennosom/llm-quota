package work.becode.llmquota

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs an [OAuthFlow] from the browser step to the stored account. Shared by every provider. */
@Composable
fun OAuthLoginDialog(
    title: String,
    start: () -> OAuthFlow,
    onDismiss: () -> Unit,
    onAdded: (StoredAccount) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flow by remember { mutableStateOf<OAuthFlow?>(null) }
    var status by remember { mutableStateOf("Sign in with the browser.") }
    var busy by remember { mutableStateOf(false) }

    fun cancelAndDismiss() {
        flow?.cancel()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { cancelAndDismiss() },
        containerColor = colorScheme.surface,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(status, fontSize = 13.sp, color = colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                status = "Opening browser…"
                scope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) { start() }
                        flow = started
                        openInBrowser(context, started.authUrl)
                        status = "Waiting for sign-in in the browser…"
                        onAdded(started.await())
                    } catch (e: Exception) {
                        status = errorText(e)
                        busy = false
                    }
                }
            }) { Text("Sign in with browser") }
        },
        dismissButton = { TextButton(onClick = { cancelAndDismiss() }) { Text("Cancel") } },
    )
}

/** Takes a DeepSeek key, checks it with one balance call, and stores it only when valid. */
@Composable
fun AddDeepSeekDialog(onDismiss: () -> Unit, onAdded: (StoredAccount) -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter the API key. It is checked before it is saved.") }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = { Text("Add DeepSeek", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API-Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(status, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && key.isNotBlank(), onClick = {
                busy = true
                status = "Checking key…"
                scope.launch {
                    try {
                        val account = withContext(Dispatchers.IO) { deepSeekAccount(key.trim()) }
                        onAdded(account)
                    } catch (e: Exception) {
                        status = errorText(e)
                        busy = false
                    }
                }
            }) { Text("Check & save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
