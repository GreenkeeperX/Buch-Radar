package de.buchradar.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** A database result requests scrolling; only the visible UI performs the animation. */
@Composable
internal fun BookListScrollEffect(
    state: LazyListState,
    requestedId: String?,
    visibleIds: List<String>,
    enabled: Boolean,
    onConsumed: () -> Unit
) {
    val consume by rememberUpdatedState(onConsumed)
    val index = requestedId?.let(visibleIds::indexOf) ?: -1
    LaunchedEffect(state, requestedId, index, enabled) {
        if (enabled && index >= 0) {
            state.animateScrollToItem(index)
            consume()
        }
    }
}
