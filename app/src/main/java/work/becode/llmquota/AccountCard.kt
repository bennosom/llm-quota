package work.becode.llmquota

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

// Matrix palette: black field, poison-green text and accents.
val Poison = Color(0xFF39FF14)
val PoisonDim = Color(0xFF2E9E22)
private val TrackGreen = Color(0xFF0D2410)
private val Highlight = Color(0xFF0D2410)

private val LABEL_WIDTH = 64.dp
private val FIGURE_WIDTH = 52.dp

fun accountKey(account: StoredAccount) = "${account.provider}:${account.id}"

fun providerName(provider: String) = when (provider) {
    CODEX -> "Codex"
    CLAUDE -> "Claude"
    else -> "DeepSeek"
}

/**
 * One provider card. A tap opens the provider's usage page. A long press opens a menu with refresh
 * and delete; moving the finger after the long press drags the card to reorder the list.
 */
@Composable
fun AccountCard(
    account: StoredAccount,
    result: SourceResult?,
    loading: Boolean,
    dragging: Boolean,
    now: Instant,
    dragOffset: Float,
    shift: () -> Float,
    onHeight: (Int) -> Unit,
    onOpenUsage: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onOpenUsage)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragBy by rememberUpdatedState(onDragBy)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val highlighted = menuOpen || dragging
    val lift by animateFloatAsState(if (dragging) 1.03f else 1f, label = "lift")

    Box(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = (if (dragging) dragOffset else 0f) + shift()
                scaleX = lift
                scaleY = lift
            }
            .onSizeChanged { onHeight(it.height) },
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (highlighted) Highlight else colorScheme.surface),
            border = if (highlighted) BorderStroke(2.dp, Poison) else null,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .pointerInput(Unit) {
                    cardGestures(
                        onTap = { tap() },
                        onLongPress = { menuOpen = true },
                        onDragStart = { menuOpen = false; dragStart() },
                        onDrag = { dragBy(it) },
                        onDragEnd = { dragEnd() },
                    )
                },
        ) {
            Column(Modifier.padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            providerName(account.provider),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(displayLabel(account), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(
                            painterResource(R.drawable.ic_refresh),
                            contentDescription = "Refresh",
                            tint = if (loading) PoisonDim else Poison,
                        )
                    }
                }
                Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val error = result?.error ?: if (result == null) "loading…" else null
                    if (error != null) Text(error, color = colorScheme.error, fontSize = 13.sp)
                    result?.figure?.let { (label, value) -> FigureRow(label, value) }
                    result?.windows?.forEach { WindowRow(it, now) }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                enabled = !loading,
                onClick = { menuOpen = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = colorScheme.error) },
                onClick = { menuOpen = false; onDeleteRequest() },
            )
        }
    }
}

/**
 * Tap, long press and the drag that follows a long press, in one pass so a tap never fires after
 * a long press. A parent scroll that takes the gesture cancels it.
 */
private suspend fun PointerInputScope.cardGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        var cancelled = false
        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            if (waitForUpOrCancellation() == null) cancelled = true
            true
        } == null
        if (cancelled) return@awaitEachGesture
        if (!timedOut) {
            onTap()
            return@awaitEachGesture
        }

        onLongPress()
        var dragging = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) break
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                onDragStart()
            }
            if (dragging) {
                onDrag(change.positionChange().y)
                change.consume()
            }
        }
        if (dragging) onDragEnd()
    }
}

@Composable
private fun WindowRow(window: QuotaWindow, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(window.label, modifier = Modifier.width(LABEL_WIDTH), fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
            RemainingBar(window.remainingPercent, modifier = Modifier.weight(1f))
            Text(
                window.remainingPercent?.let { "$it %" } ?: "–",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = barColor(window.remainingPercent),
                modifier = Modifier.width(FIGURE_WIDTH),
            )
        }
        Text(
            formatReset(window.resetEpochSec, now, ZoneId.systemDefault()),
            fontSize = 11.sp,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = LABEL_WIDTH + 10.dp),
        )
    }
}

/** A plain amount in the style of a window's percentage figure. */
@Composable
private fun FigureRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, modifier = Modifier.width(LABEL_WIDTH), fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Poison)
    }
}

/** Remaining share of a window: empty = 0 % left, full = 100 % left. The fill grows from the left and never drops below 2 dp. */
@Composable
private fun RemainingBar(remaining: Int?, modifier: Modifier = Modifier) {
    val fraction = (remaining ?: 0).coerceIn(0, 100) / 100f
    val color = barColor(remaining)
    val minWidth = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(modifier = modifier.height(10.dp)) {
        drawRect(TrackGreen)
        drawRect(color, size = Size(maxOf(size.width * fraction, minWidth), size.height))
    }
}

private fun barColor(remaining: Int?): Color = when {
    remaining == null -> PoisonDim
    remaining < 5 -> Color(0xFFFF4D4D)
    remaining < 15 -> Color(0xFFFFD000)
    else -> Poison
}
