package work.becode.llmquota

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val store = AccountStore(Secrets(applicationContext))
        setContent {
            LlmQuotaTheme { Dashboard(store) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(store: AccountStore) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fetcher = remember { QuotaFetcher(store) }
    var accounts by remember { mutableStateOf(store.list()) }
    val results = remember { mutableStateMapOf<String, SourceResult>() }
    val loadingKeys = remember { mutableStateMapOf<String, Boolean>() }
    var pulling by remember { mutableStateOf(false) }
    var updated by remember { mutableStateOf<Instant?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    // Cards being removed play their exit animation first; the account leaves the store afterwards.
    val leaving = remember { mutableStateListOf<String>() }
    var choosing by remember { mutableStateOf(false) }
    var loginProvider by remember { mutableStateOf<String?>(null) }
    var addingDeepSeek by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<StoredAccount?>(null) }

    val heights = remember { mutableStateMapOf<String, Int>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { CARD_GAP.toPx() }
    // Visual offset per card that eases a card into its new slot after a neighbour was dragged past it.
    val shifts = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }

    fun shiftOf(key: String) = shifts.getOrPut(key) { Animatable(0f) }

    fun slideInto(key: String, from: Float) {
        val shift = shiftOf(key)
        scope.launch {
            shift.snapTo(shift.value + from)
            shift.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    /** Fetches one account on its own coroutine off the main thread; other rows are untouched. */
    fun refreshOne(account: StoredAccount): Job? {
        val k = accountKey(account)
        if (loadingKeys[k] == true) return null
        loadingKeys[k] = true
        return scope.launch {
            val result = withContext(Dispatchers.IO) { fetcher.fetch(account.provider, account.id) }
            results[k] = result
            loadingKeys.remove(k)
            updated = Instant.now()
        }
    }

    fun refreshAll(showPull: Boolean) {
        val jobs = accounts.mapNotNull { refreshOne(it) }
        if (showPull && jobs.isNotEmpty()) {
            pulling = true
            scope.launch { jobs.joinAll(); pulling = false }
        }
    }

    LaunchedEffect(Unit) { refreshAll(showPull = false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(30_000)
        }
    }

    /** Moves the dragged card past a neighbour once its centre crosses the neighbour's centre. */
    fun dragBy(dy: Float) {
        val k = draggingKey ?: return
        dragOffset += dy
        while (true) {
            val list = accounts
            val i = list.indexOfFirst { accountKey(it) == k }
            val h = heights[k] ?: return
            fun top(index: Int) = (0 until index).sumOf { (heights[accountKey(list[it])] ?: 0) + gapPx.toDouble() }.toFloat()
            val center = top(i) + dragOffset + h / 2f
            val next = list.getOrNull(i + 1)
            val prev = list.getOrNull(i - 1)
            val nextHeight = next?.let { heights[accountKey(it)] }
            val prevHeight = prev?.let { heights[accountKey(it)] }
            when {
                nextHeight != null && center > top(i + 1) + nextHeight / 2f -> {
                    accounts = list.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                    dragOffset -= nextHeight + gapPx
                    slideInto(accountKey(next!!), h + gapPx)
                }
                prevHeight != null && center < top(i - 1) + prevHeight / 2f -> {
                    accounts = list.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                    dragOffset += prevHeight + gapPx
                    slideInto(accountKey(prev!!), -(h + gapPx))
                }
                else -> break
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
        DashboardHeader(onAdd = { choosing = true })

        PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = { refreshAll(showPull = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (accounts.isEmpty()) {
                    Text(
                        "No accounts configured.\nAdd accounts by '+' button",
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                } else {
                    StampLine(updated?.let { "Last updated ${formatStamp(it, now, ZoneId.systemDefault())}" } ?: "loading…")
                }
                accounts.forEach { account ->
                    val k = accountKey(account)
                    androidx.compose.runtime.key(k) {
                        val visible = remember { MutableTransitionState(false) }
                        visible.targetState = k !in leaving
                        LaunchedEffect(k in leaving, visible.currentState) {
                            if (k in leaving && !visible.currentState) {
                                store.remove(account.provider, account.id)
                                accounts = store.list()
                                results.remove(k)
                                leaving.remove(k)
                            }
                        }
                        AnimatedVisibility(
                            visibleState = visible,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Box(Modifier.padding(bottom = CARD_GAP)) {
                                AccountCard(
                                    account = account,
                                    result = results[k],
                                    loading = loadingKeys[k] == true,
                                    dragging = draggingKey == k,
                                    now = now,
                                    dragOffset = dragOffset,
                                    shift = { shiftOf(k).value },
                                    onHeight = { heights[k] = it },
                                    onOpenUsage = { openInBrowser(context, usageUrl(account.provider)) },
                                    onRefresh = { refreshOne(account) },
                                    onDeleteRequest = { pendingRemoval = account },
                                    onDragStart = { if (draggingKey == null) { draggingKey = k; dragOffset = 0f } },
                                    onDragBy = { dragBy(it) },
                                    onDragEnd = {
                                        if (draggingKey == k) {
                                            store.reorder(accounts.map { accountKey(it) })
                                            scope.launch {
                                                Animatable(dragOffset).animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) { dragOffset = value }
                                                draggingKey = null
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            containerColor = colorScheme.surface,
            title = { Text("Add account", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { choosing = false; loginProvider = CODEX }) { Text("Codex · browser sign-in") }
                    TextButton(onClick = { choosing = false; loginProvider = CLAUDE }) { Text("Claude · browser sign-in") }
                    TextButton(onClick = { choosing = false; addingDeepSeek = true }) { Text("DeepSeek · API key") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }

    loginProvider?.let { provider ->
        OAuthLoginDialog(
            title = if (provider == CODEX) "Add Codex" else "Add Claude",
            start = { if (provider == CODEX) CodexLogin.start() else ClaudeLogin.start() },
            onDismiss = { loginProvider = null },
            onAdded = { account ->
                store.upsert(account)
                accounts = store.list()
                loginProvider = null
                refreshOne(account)
            },
        )
    }

    if (addingDeepSeek) {
        AddDeepSeekDialog(
            onDismiss = { addingDeepSeek = false },
            onAdded = { account ->
                store.upsert(account)
                accounts = store.list()
                addingDeepSeek = false
                refreshOne(account)
            },
        )
    }

    pendingRemoval?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = colorScheme.surface,
            title = { Text("Remove account?", fontWeight = FontWeight.SemiBold) },
            text = { Text("${providerName(account.provider)} · ${displayLabel(account)}", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    leaving.add(accountKey(account))
                    pendingRemoval = null
                }) { Text("Remove", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }
}
