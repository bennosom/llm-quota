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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val vm: DashboardViewModel = viewModel { DashboardViewModel(store) }
    // Cards that already exist when this composition starts are shown without an enter animation,
    // so a configuration change does not replay it for every card.
    val initialKeys = remember { vm.accounts.map { accountKey(it) }.toSet() }
    var now by remember { mutableStateOf(Instant.now()) }

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
            val list = vm.accounts
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
                    vm.moveTo(list.toMutableList().also { it.add(i + 1, it.removeAt(i)) })
                    dragOffset -= nextHeight + gapPx
                    slideInto(accountKey(next!!), h + gapPx)
                }
                prevHeight != null && center < top(i - 1) + prevHeight / 2f -> {
                    vm.moveTo(list.toMutableList().also { it.add(i - 1, it.removeAt(i)) })
                    dragOffset += prevHeight + gapPx
                    slideInto(accountKey(prev!!), -(h + gapPx))
                }
                else -> break
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
        DashboardHeader(onAdd = { vm.choosing = true })

        PullToRefreshBox(
            isRefreshing = vm.pulling,
            onRefresh = { vm.refreshAll(showPull = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (vm.accounts.isEmpty()) {
                    Text(
                        "No accounts configured.\nAdd accounts by '+' button",
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                } else {
                    StampLine(vm.updated?.let { "Last updated ${formatStamp(it, now, ZoneId.systemDefault())}" } ?: "loading…")
                }
                vm.accounts.forEach { account ->
                    val k = accountKey(account)
                    androidx.compose.runtime.key(k) {
                        val visible = remember { MutableTransitionState(k in initialKeys) }
                        visible.targetState = k !in vm.leaving
                        LaunchedEffect(k in vm.leaving, visible.currentState) {
                            if (k in vm.leaving && !visible.currentState) {
                                vm.finishRemoval(account)
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
                                    result = vm.results[k],
                                    loading = vm.isLoading(k),
                                    dragging = draggingKey == k,
                                    now = now,
                                    dragOffset = dragOffset,
                                    shift = { shiftOf(k).value },
                                    onHeight = { heights[k] = it },
                                    onOpenUsage = { openInBrowser(context, usageUrl(account.provider)) },
                                    onRefresh = { vm.refreshOne(account) },
                                    onDeleteRequest = { vm.pendingRemoval = account },
                                    onDragStart = { if (draggingKey == null) { draggingKey = k; dragOffset = 0f } },
                                    onDragBy = { dragBy(it) },
                                    onDragEnd = {
                                        if (draggingKey == k) {
                                            vm.saveOrder()
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

    if (vm.choosing) {
        AlertDialog(
            onDismissRequest = { vm.choosing = false },
            containerColor = colorScheme.surface,
            title = { Text("Add account", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.choosing = false; vm.loginProvider = CODEX }) { Text("Codex · browser sign-in") }
                    TextButton(onClick = { vm.choosing = false; vm.loginProvider = CLAUDE }) { Text("Claude · browser sign-in") }
                    TextButton(onClick = { vm.choosing = false; vm.addingDeepSeek = true }) { Text("DeepSeek · API key") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.choosing = false }) { Text("Cancel") } },
        )
    }

    vm.loginProvider?.let { provider ->
        OAuthLoginDialog(
            title = if (provider == CODEX) "Add Codex" else "Add Claude",
            start = { if (provider == CODEX) CodexLogin.start() else ClaudeLogin.start() },
            onDismiss = { vm.loginProvider = null },
            onAdded = { account ->
                vm.addAccount(account)
                vm.loginProvider = null
            },
        )
    }

    if (vm.addingDeepSeek) {
        AddDeepSeekDialog(
            onDismiss = { vm.addingDeepSeek = false },
            onAdded = { account ->
                vm.addAccount(account)
                vm.addingDeepSeek = false
            },
        )
    }

    vm.pendingRemoval?.let { account ->
        AlertDialog(
            onDismissRequest = { vm.pendingRemoval = null },
            containerColor = colorScheme.surface,
            title = { Text("Remove account?", fontWeight = FontWeight.SemiBold) },
            text = { Text("${providerName(account.provider)} · ${displayLabel(account)}", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmRemoval(account)
                }) { Text("Remove", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { vm.pendingRemoval = null }) { Text("Cancel") } },
        )
    }
}
