package com.github.damontecres.wholphin.ui.jellyeye

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** A spotlight title and the reason it was picked. */
data class SpotlightItem(
    val item: BaseItem,
    val why: String,
)

data class JellyeyeHomeState(
    val loaded: Boolean = false,
    val spotlight: List<SpotlightItem> = emptyList(),
    val pickUp: List<BaseItem> = emptyList(),
    val recommendedShows: List<BaseItem> = emptyList(),
    val recommendedFilms: List<BaseItem> = emptyList(),
    val season: JellyeyeSeason? = null,
    val seasonal: List<BaseItem> = emptyList(),
    // when there's no holiday: highly rated titles you haven't watched yet
    val gems: List<BaseItem> = emptyList(),
)

@HiltViewModel
class JellyeyeHomeViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val latestNextUpService: LatestNextUpService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(JellyeyeHomeState())
        val state: StateFlow<JellyeyeHomeState> = _state

        private var lastLoad = 0L

        // kept so the spotlight can be refreshed on its own without reloading the whole page
        private var lastUserId: UUID? = null
        private var lastPickUp: List<BaseItem> = emptyList()
        private var lastSeason: JellyeyeSeason? = null
        private var lastSeasonal: List<BaseItem> = emptyList()

        private val fields =
            listOf(
                ItemFields.OVERVIEW,
                ItemFields.GENRES,
                ItemFields.CHILD_COUNT,
                ItemFields.SORT_NAME,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            )

        /** Called whenever the home screen is shown; reloads at most every 5 minutes. */
        fun refresh(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastLoad < 5 * 60_000 && _state.value.loaded) return
            lastLoad = now
            viewModelScope.launchIO {
                val userId = serverRepository.currentUser?.id ?: return@launchIO
                try {
                    load(userId)
                } catch (ex: Exception) {
                    Timber.e(ex, "Jellyeye: home failed to load")
                    _state.update { it.copy(loaded = true) }
                }
            }
        }

        private suspend fun items(request: GetItemsRequest): List<BaseItem> =
            try {
                api.libraryApi
                    .getItems(request)
                    .content
                    .items
                    .map { BaseItem(it, false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Jellyeye: items request failed")
                emptyList()
            }

        private suspend fun similar(
            userId: UUID,
            itemId: UUID,
        ): List<BaseItem> =
            try {
                api.libraryApi
                    .getSimilarItems(
                        GetSimilarItemsRequest(
                            userId = userId,
                            itemId = itemId,
                            fields = fields,
                            limit = 30,
                        ),
                    ).content
                    .items
                    .map { BaseItem(it, false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Jellyeye: similar request failed")
                emptyList()
            }

        private suspend fun load(userId: UUID) =
            coroutineScope {
                val season = JellyeyeSeason.detect()

                // Pick up where you left off: resume first, then next episodes of other shows
                val pickUpJob =
                    async {
                        val resume =
                            try {
                                latestNextUpService.getResume(userId, 10, includeEpisodes = true, useSeriesForPrimary = false)
                            } catch (ex: Exception) {
                                emptyList()
                            }
                        val nextUp =
                            try {
                                latestNextUpService.getNextUp(
                                    userId,
                                    10,
                                    enableRewatching = false,
                                    enableResumable = false,
                                    maxDays = 0,
                                    useSeriesForPrimary = false,
                                )
                            } catch (ex: Exception) {
                                emptyList()
                            }
                        val seen = mutableSetOf<UUID>()
                        val list = mutableListOf<BaseItem>()
                        resume.forEach { seen.add(it.data.seriesId ?: it.id); list.add(it) }
                        nextUp.forEach {
                            val key = it.data.seriesId ?: it.id
                            if (seen.add(key)) list.add(it)
                        }
                        list.take(10)
                    }

                val seasonalJob =
                    async {
                        if (season == null) return@async emptyList<BaseItem>()
                        val base =
                            GetItemsRequest(
                                userId = userId,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                fields = fields,
                                limit = 40,
                                enableUserData = true,
                            )
                        val found = mutableListOf<BaseItem>()
                        if (season.tags.isNotEmpty()) found += items(base.copy(tags = season.tags, sortBy = listOf(ItemSortBy.RANDOM)))
                        season.searchTerms.forEach { found += items(base.copy(searchTerm = it)) }
                        if (season.genres.isNotEmpty()) found += items(base.copy(genres = season.genres, sortBy = listOf(ItemSortBy.RANDOM)))
                        found.distinctBy { it.id }
                    }

                val pickUp = pickUpJob.await()
                val continuing = pickUp.flatMap { listOfNotNull(it.id, it.data.seriesId) }.toSet()

                val showsJob = async { recommended(userId, BaseItemKind.SERIES, continuing) }
                val filmsJob = async { recommended(userId, BaseItemKind.MOVIE, continuing) }

                val gemsJob =
                    async {
                        if (season != null) emptyList() else gems(userId, continuing)
                    }

                val seasonal = seasonalJob.await()
                lastUserId = userId
                lastPickUp = pickUp
                lastSeason = season
                lastSeasonal = seasonal
                val spotlight = buildSpotlight(userId, pickUp, season, seasonal, exclude = emptySet())
                // a holiday with nothing for it in the library falls back to the hidden gems too
                val gems = if (season != null && seasonal.isEmpty()) gems(userId, continuing) else gemsJob.await()

                _state.update {
                    JellyeyeHomeState(
                        loaded = true,
                        spotlight = spotlight,
                        pickUp = pickUp,
                        recommendedShows = showsJob.await(),
                        recommendedFilms = filmsJob.await(),
                        season = season,
                        seasonal = seasonal,
                        gems = gems,
                    )
                }
            }

        private suspend fun buildSpotlight(
            userId: UUID,
            pickUp: List<BaseItem>,
            season: JellyeyeSeason?,
            seasonal: List<BaseItem>,
            exclude: Set<UUID>,
        ): List<SpotlightItem> {
            val nextUpSeries = pickUp.mapNotNull { it.data.seriesId }.toSet()
            var spotlight =
                items(
                    GetItemsRequest(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        imageTypes = listOf(ImageType.BACKDROP),
                        fields = fields,
                        limit = 30,
                        enableUserData = true,
                    ),
                ).filter { !it.data.overview.isNullOrBlank() && it.id !in exclude }
                    .take(5)
                    .map { SpotlightItem(it, whyPicked(it, nextUpSeries)) }
            if (season != null) {
                // a different seasonal pick each time, where there's more than one
                val picks = seasonal.filter { !it.data.backdropImageTags.isNullOrEmpty() && !it.data.overview.isNullOrBlank() }
                val pick = picks.firstOrNull { it.id !in exclude } ?: picks.firstOrNull()
                if (pick != null) {
                    val days = season.daysLeft()
                    val why = if (days == 0L) "Seasonal pick · today!" else "Seasonal pick · $days days to ${season.targetName}"
                    spotlight = listOf(SpotlightItem(pick, why)) + spotlight.filter { it.item.id != pick.id }
                }
            }
            return spotlight
        }

        /** A fresh set of spotlight titles, different from the ones just shown. Called after the banner has gone round a few times. */
        fun refreshSpotlight() {
            val userId = lastUserId ?: return
            viewModelScope.launchIO {
                try {
                    val shown = _state.value.spotlight.map { it.item.id }.toSet()
                    val fresh = buildSpotlight(userId, lastPickUp, lastSeason, lastSeasonal.shuffled(), exclude = shown)
                    if (fresh.isNotEmpty()) _state.update { it.copy(spotlight = fresh) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Jellyeye: spotlight refresh failed")
                }
            }
        }

        /** Highly rated films and shows you haven't watched yet. */
        private suspend fun gems(
            userId: UUID,
            exclude: Set<UUID>,
        ): List<BaseItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    recursive = true,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    filters = listOf(ItemFilter.IS_UNPLAYED),
                    minCommunityRating = 7.0,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    fields = fields,
                    limit = 40,
                    enableUserData = true,
                ),
            ).filter { it.id !in exclude }

        /** Open the normal details page for a title. */
        fun open(item: BaseItem) {
            navigationManager.navigateTo(item.destination())
        }

        /**
         * Play a title. Films and episodes play straight away (resuming where you left off);
         * a TV show plays its next episode, or the first one if you haven't started it.
         */
        fun play(item: BaseItem) {
            if (item.type.playable) {
                navigationManager.navigateTo(Destination.Playback(item))
                return
            }
            if (item.type != BaseItemKind.SERIES) {
                open(item)
                return
            }
            viewModelScope.launchIO {
                val userId = serverRepository.currentUser?.id
                val episode =
                    try {
                        api.showApi
                            .getNextUp(GetNextUpRequest(userId = userId, seriesId = item.id, limit = 1, enableUserData = true))
                            .content
                            .items
                            .firstOrNull()
                            ?: api.showApi
                                .getEpisodes(GetEpisodesRequest(seriesId = item.id, userId = userId, limit = 1, enableUserData = true))
                                .content
                                .items
                                .firstOrNull()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Jellyeye: couldn't find an episode to play")
                        null
                    }
                withContext(Dispatchers.Main) {
                    if (episode != null) {
                        navigationManager.navigateTo(Destination.Playback(BaseItem(episode, false)))
                    } else {
                        navigationManager.navigateTo(item.destination())
                    }
                }
            }
        }

        private fun whyPicked(
            item: BaseItem,
            nextUpSeries: Set<UUID>,
        ): String {
            val ud = item.data.userData
            val rating = item.data.communityRating ?: 0f
            return when {
                item.id in nextUpSeries -> "Continue the series"
                ud?.isFavorite == true -> "One of your favourites"
                ud?.played != true && rating >= 7.5f -> "Highly rated · not watched yet"
                ud?.played != true -> "Not watched yet"
                rating >= 7.5f -> "Highly rated"
                else -> "From your library"
            }
        }

        /**
         * Like Jellyfin's Suggestions: titles similar to the last few you watched, mixed together,
         * leaving out anything watched or already in "Pick up where you left off".
         * Topped up with the highest-rated titles you haven't seen.
         */
        private suspend fun recommended(
            userId: UUID,
            kind: BaseItemKind,
            exclude: Set<UUID>,
        ): List<BaseItem> {
            val played =
                items(
                    GetItemsRequest(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(if (kind == BaseItemKind.SERIES) BaseItemKind.EPISODE else BaseItemKind.MOVIE),
                        filters = listOf(ItemFilter.IS_PLAYED),
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = 60,
                        enableImages = false,
                    ),
                )
            val bases =
                played
                    .mapNotNull { if (kind == BaseItemKind.SERIES) it.data.seriesId else it.id }
                    .distinct()
                    .take(3)
            val lists = bases.map { similar(userId, it).filter { s -> s.type == kind } }
            val seen = exclude.toMutableSet().apply { addAll(bases) }
            val out = mutableListOf<BaseItem>()
            var round = 0
            while (out.size < 40 && lists.any { round < it.size }) {
                for (list in lists) {
                    val item = list.getOrNull(round) ?: continue
                    if (item.data.userData?.played == true) continue
                    if (seen.add(item.id)) out.add(item)
                }
                round++
            }
            if (out.size < 40) {
                items(
                    GetItemsRequest(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(kind),
                        filters = listOf(ItemFilter.IS_UNPLAYED),
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                        fields = fields,
                        limit = 60,
                        enableUserData = true,
                    ),
                ).forEach { if (out.size < 40 && seen.add(it.id)) out.add(it) }
            }
            return out
        }
    }
