package com.github.damontecres.wholphin.ui.jellyeye

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.LocalDateTime

/**
 * The "Recommended" tab of the Movies and TV Shows libraries, in the Jellyeye look: each of Wholphin's
 * recommendation rows becomes a panel like on the home page (wide pictures for things you're part way
 * through, posters for the rest), two rows each, with "View all" for the full list. Scrolls downwards only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JellyeyeRecommended(
    rows: List<HomeRowLoadingState>,
    onOpen: (BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onViewAll: (rowIndex: Int, row: HomeRowLoadingState.Success) -> Unit,
    modifier: Modifier = Modifier,
    memoryKey: String = "jellyeye-rec",
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    // focus: the title you came back to, else the first title (see JellyeyeFocus.kt)
    val memory = rememberJeReturn(memoryKey)
    val listState = rememberLazyListState()
    val firstTile = remember { FocusRequester() }
    val restoreTile = remember { FocusRequester() }
    val firstId =
        rows
            .firstNotNullOfOrNull { (it as? HomeRowLoadingState.Success)?.items?.firstOrNull { item -> item != null } }
            ?.id
    val restore = if (memory.id != null && memory.id == firstId) firstTile else restoreTile
    val focusMap = rememberReturnFocusMap(memory, restore, extra = firstId?.let { mapOf(it to firstTile) } ?: emptyMap())
    PageFocus(memory, listState, ready = firstId != null, restore = restore, main = firstTile)
    val open = { item: BaseItem -> memory.leaving(item.id, listState); onOpen(item) }
    val play = { item: BaseItem -> memory.leaving(item.id, listState); onPlay(item) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides JellyeyeScrollSpec) {
        LazyColumn(
            state = listState,
            // the library page already has the side margins; just a little room under the tabs
            contentPadding = PaddingValues(top = 4.dp, bottom = JeLayout.pageBottom),
            verticalArrangement = Arrangement.spacedBy(JeLayout.panelGap),
            modifier = modifier.fillMaxSize(),
        ) {
            itemsIndexed(rows, key = { i, _ -> "rec-$i" }) { index, row ->
                when (row) {
                    is HomeRowLoadingState.Success -> {
                        val items = remember(row.items) { row.items.filterNotNull() }
                        if (items.isNotEmpty()) RecommendedPanel(index, row, items, now, open, play, onViewAll, focusMap)
                    }

                    is HomeRowLoadingState.Loading,
                    is HomeRowLoadingState.Pending,
                    -> {
                        SectionTile(row.title.getString()) {
                            Text("Loading…", color = JE.dim, fontSize = 13.sp)
                        }
                    }

                    // a row that failed to load is simply left out
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun RecommendedPanel(
    index: Int,
    row: HomeRowLoadingState.Success,
    items: List<BaseItem>,
    now: LocalDateTime,
    onOpen: (BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onViewAll: (Int, HomeRowLoadingState.Success) -> Unit,
    focusMap: Map<java.util.UUID, FocusRequester>,
) {
    // things you're part way through (and next episodes) get the wide pictures with "finishes at", like the home page
    val inProgress =
        items.count { it.type == BaseItemKind.EPISODE || (it.data.userData?.playedPercentage ?: 0.0) > 0.0 } * 2 > items.size
    val shown = if (inProgress) 10 else 16
    SectionTile(row.title.getString()) {
        if (inProgress) {
            WideGrid(items, now, onClick = onPlay, focusRequesters = focusMap)
        } else {
            PosterGrid(items, rows = 2, onClick = onOpen, fullRowsOnly = false, focusRequesters = focusMap)
        }
        if (row.showViewMore || items.size > shown) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.padding(top = 2.dp)) {
                    PillButton("View all  →", onClick = { onViewAll(index, row) })
                }
            }
        }
    }
}
