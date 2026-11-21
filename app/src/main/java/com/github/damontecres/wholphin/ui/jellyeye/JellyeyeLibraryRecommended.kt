package com.github.damontecres.wholphin.ui.jellyeye

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/** One panel on a library's Recommended tab. Wide panels (episodes) play when selected; poster panels open the title. */
data class RecSection(
    val key: String,
    val title: String,
    val items: List<BaseItem>,
    val wide: Boolean = false,
)

data class RecState(
    val loaded: Boolean = false,
    val sections: List<RecSection> = emptyList(),
)

/**
 * Jellyeye's own Recommended tab for a Movies or TV Shows library, worked out from your viewing history on the
 * server: "Because you watched", top picks in your favourite genres, new episodes of your shows, and so on.
 * Only titles from this library; a title appears in one panel only; empty panels are left out.
 */
@HiltViewModel
class JellyeyeRecommendedViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RecState())
        val state: StateFlow<RecState> = _state
        private var loadedFor: UUID? = null

        private val fields =
            listOf(
                ItemFields.OVERVIEW,
                ItemFields.GENRES,
                ItemFields.CHILD_COUNT,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.DATE_CREATED,
            )

        fun load(
            libraryId: UUID,
            kind: BaseItemKind,
        ) {
            if (loadedFor == libraryId && _state.value.loaded) return
            loadedFor = libraryId
            viewModelScope.launchIO {
                val userId = serverRepository.currentUser?.id ?: return@launchIO
                try {
                    val sections = if (kind == BaseItemKind.SERIES) shows(userId, libraryId) else films(userId, libraryId)
                    _state.update { RecState(loaded = true, sections = dedupe(sections)) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Jellyeye: recommendations failed")
                    _state.update { it.copy(loaded = true) }
                }
            }
        }

        fun open(item: BaseItem) = navigationManager.navigateTo(item.destination())

        fun play(item: BaseItem) = navigationManager.navigateTo(Destination.Playback(item))

        /* ---------------- helpers ---------------- */

        private fun base(
            userId: UUID,
            libraryId: UUID,
            type: BaseItemKind,
        ) = GetItemsRequest(
            userId = userId,
            parentId = libraryId,
            recursive = true,
            includeItemTypes = listOf(type),
            fields = fields,
            enableUserData = true,
        )

        private suspend fun items(request: GetItemsRequest): List<BaseItem> =
            try {
                api.libraryApi
                    .getItems(request)
                    .content.items
                    .map { BaseItem(it, false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Jellyeye: recommendation request failed")
                emptyList()
            }

        private suspend fun similar(
            userId: UUID,
            itemId: UUID,
        ): List<BaseItem> =
            try {
                api.libraryApi
                    .getSimilarItems(GetSimilarItemsRequest(itemId = itemId, userId = userId, fields = fields, limit = 40))
                    .content.items
                    .map { BaseItem(it, false) }
            } catch (ex: Exception) {
                emptyList()
            }

        private fun BaseItem.watched() = data.userData?.played == true

        /** Most-watched genres first. */
        private fun topGenres(
            watched: List<BaseItem>,
            count: Int,
        ): List<String> =
            watched
                .flatMap { it.data.genres.orEmpty() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(count)
                .map { it.key }

        /** Each title only once on the page, keeping the panel that comes first; panels with too few left are dropped. */
        private fun dedupe(sections: List<RecSection>): List<RecSection> {
            val seen = mutableSetOf<UUID>()
            return sections.mapNotNull { s ->
                val fresh = s.items.filter { seen.add(it.id) }.take(16)
                if (fresh.size >= 4) s.copy(items = fresh) else null
            }
        }

        private val decades = listOf(1970, 1980, 1990, 2000, 2010)

        /** A panel that changes each visit: a genre you don't usually watch, or a decade. */
        private suspend fun rotating(
            userId: UUID,
            libraryId: UUID,
            type: BaseItemKind,
            watched: List<BaseItem>,
        ): RecSection? {
            val favourites = topGenres(watched, 3).toSet()
            val genres =
                items(base(userId, libraryId, type).copy(sortBy = listOf(ItemSortBy.RANDOM), limit = 80))
                    .flatMap { it.data.genres.orEmpty() }
                    .distinct()
                    .filter { it !in favourites }
            return if (genres.isNotEmpty() && (decades.isEmpty() || Math.random() < 0.6)) {
                val g = genres.random()
                RecSection(
                    "rotating",
                    "$g tonight",
                    items(base(userId, libraryId, type).copy(genres = listOf(g), sortBy = listOf(ItemSortBy.RANDOM), limit = 30)),
                )
            } else {
                val d = decades.random()
                RecSection(
                    "rotating",
                    "Back to the ${d.toString().takeLast(2)}s",
                    items(base(userId, libraryId, type).copy(years = (d until d + 10).toList(), sortBy = listOf(ItemSortBy.RANDOM), limit = 30)),
                )
            }
        }

        /* ---------------- Movies ---------------- */

        private suspend fun films(
            userId: UUID,
            libraryId: UUID,
        ): List<RecSection> =
            coroutineScope {
                val type = BaseItemKind.MOVIE
                val watched =
                    items(
                        base(userId, libraryId, type).copy(
                            filters = listOf(ItemFilter.IS_PLAYED),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 200,
                        ),
                    )
                val becauseJobs =
                    watched.take(3).map { film ->
                        async {
                            RecSection(
                                "because-${film.id}",
                                "Because you watched ${film.name ?: ""}",
                                similar(userId, film.id).filter { it.type == type && !it.watched() },
                            )
                        }
                    }
                val genres = topGenres(watched, 3)
                val picks =
                    async {
                        if (genres.isEmpty()) {
                            null
                        } else {
                            RecSection(
                                "picks",
                                "Top picks for you",
                                items(
                                    base(userId, libraryId, type).copy(
                                        genres = genres,
                                        filters = listOf(ItemFilter.IS_UNPLAYED),
                                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        limit = 60,
                                    ),
                                ),
                            )
                        }
                    }
                val added =
                    async {
                        RecSection(
                            "added",
                            "Recently added",
                            items(base(userId, libraryId, type).copy(sortBy = listOf(ItemSortBy.DATE_CREATED), sortOrder = listOf(SortOrder.DESCENDING), limit = 30)),
                        )
                    }
                val acclaimed =
                    async {
                        RecSection(
                            "acclaimed",
                            "Critically acclaimed",
                            items(
                                base(userId, libraryId, type).copy(
                                    minCriticRating = 80.0,
                                    filters = listOf(ItemFilter.IS_UNPLAYED),
                                    sortBy = listOf(ItemSortBy.CRITIC_RATING),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = 40,
                                ),
                            ),
                        )
                    }
                val gems =
                    async {
                        RecSection(
                            "gems",
                            "Hidden gems",
                            items(
                                base(userId, libraryId, type).copy(
                                    minCommunityRating = 7.0,
                                    filters = listOf(ItemFilter.IS_UNPLAYED),
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = 40,
                                ),
                            ),
                        )
                    }
                // favourites, or films you rated highly, that you watched longest ago
                val again =
                    RecSection(
                        "again",
                        "Watch again",
                        watched
                            .filter { it.data.userData?.isFavorite == true || (it.data.communityRating ?: 0f) >= 7f }
                            .reversed(),
                    )
                val rotating = async { rotating(userId, libraryId, type, watched) }
                listOfNotNull(
                    *becauseJobs.map { it.await() }.toTypedArray(),
                    picks.await(),
                    added.await(),
                    acclaimed.await(),
                    gems.await(),
                    again,
                    rotating.await(),
                )
            }

        /* ---------------- TV Shows ---------------- */

        private suspend fun shows(
            userId: UUID,
            libraryId: UUID,
        ): List<RecSection> =
            coroutineScope {
                val type = BaseItemKind.SERIES
                // the shows you've been watching, most recent first
                val watchedEpisodes =
                    items(
                        base(userId, libraryId, BaseItemKind.EPISODE).copy(
                            filters = listOf(ItemFilter.IS_PLAYED),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 400,
                        ),
                    )
                val startedIds = watchedEpisodes.mapNotNull { it.data.seriesId }.distinct()
                val started = startedIds.toSet()
                val startedShows =
                    if (startedIds.isEmpty()) {
                        emptyList()
                    } else {
                        items(base(userId, libraryId, type).copy(ids = startedIds.take(100)))
                            .sortedBy { startedIds.indexOf(it.id) }
                    }
                val becauseJobs =
                    startedShows.take(3).map { show ->
                        async {
                            RecSection(
                                "because-${show.id}",
                                "Because you watched ${show.name ?: ""}",
                                similar(userId, show.id).filter { it.type == type && it.id !in started },
                            )
                        }
                    }
                val newEpisodes =
                    async {
                        RecSection(
                            "new-episodes",
                            "New episodes of your shows",
                            items(
                                base(userId, libraryId, BaseItemKind.EPISODE).copy(
                                    filters = listOf(ItemFilter.IS_UNPLAYED),
                                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = 150,
                                ),
                            ).filter { it.data.seriesId in started }
                                // one per show: the newest
                                .distinctBy { it.data.seriesId },
                            wide = true,
                        )
                    }
                val genres = topGenres(startedShows, 3)
                val picks =
                    async {
                        if (genres.isEmpty()) {
                            null
                        } else {
                            RecSection(
                                "picks",
                                "Top picks for you",
                                items(
                                    base(userId, libraryId, type).copy(
                                        genres = genres,
                                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        limit = 80,
                                    ),
                                ).filter { it.id !in started },
                            )
                        }
                    }
                val binge =
                    async {
                        RecSection(
                            "binge",
                            "Ready to binge",
                            items(
                                base(userId, libraryId, type).copy(
                                    seriesStatus = listOf(SeriesStatus.ENDED),
                                    minCommunityRating = 7.5,
                                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = 80,
                                ),
                            ).filter { it.id !in started },
                        )
                    }
                val added =
                    async {
                        RecSection(
                            "added",
                            "Recently added shows",
                            items(base(userId, libraryId, type).copy(sortBy = listOf(ItemSortBy.DATE_CREATED), sortOrder = listOf(SortOrder.DESCENDING), limit = 30)),
                        )
                    }
                val gems =
                    async {
                        RecSection(
                            "gems",
                            "Hidden gems",
                            items(
                                base(userId, libraryId, type).copy(
                                    minCommunityRating = 7.0,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = 60,
                                ),
                            ).filter { it.id !in started },
                        )
                    }
                val rotating = async { rotating(userId, libraryId, type, startedShows) }
                listOfNotNull(
                    *becauseJobs.map { it.await() }.toTypedArray(),
                    newEpisodes.await(),
                    picks.await(),
                    binge.await(),
                    added.await(),
                    gems.await(),
                    rotating.await(),
                )
            }
    }

/** The Recommended tab of a Movies or TV Shows library. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JellyeyeLibraryRecommended(
    libraryId: UUID,
    kind: BaseItemKind,
    modifier: Modifier = Modifier,
    viewModel: JellyeyeRecommendedViewModel = hiltViewModel(key = "jellyeye-rec-$libraryId"),
) {
    LaunchedEffect(libraryId) { viewModel.load(libraryId, kind) }
    val state by viewModel.state.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides JellyeyeScrollSpec) {
        LazyColumn(
            // the library page already has the side margins; just a little room under the tabs
            contentPadding = PaddingValues(top = 4.dp, bottom = JeLayout.pageBottom),
            verticalArrangement = Arrangement.spacedBy(JeLayout.panelGap),
            modifier = modifier.fillMaxSize(),
        ) {
            if (!state.loaded) {
                item(key = "loading") {
                    SectionTile("Finding things for you…") {
                        Text("Looking through what you've watched", color = JE.dim, fontSize = 13.sp)
                    }
                }
            } else if (state.sections.isEmpty()) {
                item(key = "empty") {
                    SectionTile("Nothing to recommend yet") {
                        Text("Watch a few things and recommendations will appear here.", color = JE.dim, fontSize = 13.sp)
                    }
                }
            }
            items(state.sections, key = { it.key }) { s ->
                SectionTile(s.title) {
                    if (s.wide) {
                        WideGrid(s.items, now, onClick = viewModel::play)
                    } else {
                        PosterGrid(s.items, rows = 2, onClick = viewModel::open, fullRowsOnly = false)
                    }
                }
            }
        }
    }
}
