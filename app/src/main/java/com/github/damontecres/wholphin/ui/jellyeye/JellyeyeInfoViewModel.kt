package com.github.damontecres.wholphin.ui.jellyeye

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
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
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class JellyeyeInfoState(
    val item: BaseItem? = null,
    val failed: Boolean = false,
    // TV shows
    val seasons: List<BaseItem> = emptyList(),
    val seasonId: UUID? = null,
    val episodes: List<BaseItem> = emptyList(),
    val nextEpisode: BaseItem? = null,
    // films and TV shows
    val similar: List<BaseItem> = emptyList(),
    // people
    val films: List<BaseItem> = emptyList(),
    val shows: List<BaseItem> = emptyList(),
)

@HiltViewModel
class JellyeyeInfoViewModel
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(JellyeyeInfoState())
        val state: StateFlow<JellyeyeInfoState> = _state

        private var loadedId: UUID? = null

        private val rowFields =
            listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.CHILD_COUNT, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

        fun load(
            itemId: UUID,
            force: Boolean = false,
        ) {
            if (!force && loadedId == itemId) return
            loadedId = itemId
            viewModelScope.launchIO {
                try {
                    val item = BaseItem(api.libraryApi.getItem(itemId).content, false)
                    _state.update { it.copy(item = item, failed = false) }
                    coroutineScope {
                        when (item.type) {
                            BaseItemKind.PERSON -> {
                                val all =
                                    api.libraryApi
                                        .getItems(
                                            GetItemsRequest(
                                                userId = serverRepository.currentUser?.id,
                                                personIds = listOf(itemId),
                                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                                recursive = true,
                                                fields = rowFields,
                                                sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                                                sortOrder = listOf(SortOrder.DESCENDING, SortOrder.DESCENDING, SortOrder.ASCENDING),
                                                enableUserData = true,
                                                limit = 300,
                                            ),
                                        ).content
                                        .items
                                        .map { BaseItem(it, false) }
                                _state.update {
                                    it.copy(
                                        films = all.filter { i -> i.type == BaseItemKind.MOVIE },
                                        shows = all.filter { i -> i.type == BaseItemKind.SERIES },
                                    )
                                }
                            }

                            else -> {
                                val similarJob = async { similar(itemId) }
                                if (item.type == BaseItemKind.SERIES) loadSeries(item)
                                _state.update { it.copy(similar = similarJob.await()) }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Jellyeye: info failed to load for %s", itemId)
                    _state.update { it.copy(failed = true) }
                }
            }
        }

        private suspend fun similar(itemId: UUID): List<BaseItem> =
            try {
                api.libraryApi
                    .getSimilarItems(
                        GetSimilarItemsRequest(
                            itemId = itemId,
                            userId = serverRepository.currentUser?.id,
                            fields = rowFields,
                            limit = 24,
                        ),
                    ).content
                    .items
                    .map { BaseItem(it, false) }
            } catch (ex: Exception) {
                emptyList()
            }

        private suspend fun loadSeries(series: BaseItem) {
            val userId = serverRepository.currentUser?.id
            val seasons =
                try {
                    api.showApi
                        .getSeasons(GetSeasonsRequest(seriesId = series.id, userId = userId, enableUserData = true))
                        .content
                        .items
                        .map { BaseItem(it, false) }
                } catch (ex: Exception) {
                    emptyList()
                }
            val next =
                try {
                    api.showApi
                        .getNextUp(
                            GetNextUpRequest(
                                userId = userId,
                                seriesId = series.id,
                                limit = 1,
                                enableUserData = true,
                                enableResumable = true,
                                fields = rowFields,
                            ),
                        ).content
                        .items
                        .firstOrNull()
                        ?.let { BaseItem(it, false) }
                } catch (ex: Exception) {
                    null
                }
            val seasonId =
                next?.data?.seasonId
                    ?: seasons.firstOrNull { (it.data.indexNumber ?: 0) > 0 }?.id
                    ?: seasons.firstOrNull()?.id
            _state.update { it.copy(seasons = seasons, nextEpisode = next, seasonId = seasonId) }
            if (seasonId != null) loadEpisodes(series.id, seasonId)
        }

        private suspend fun loadEpisodes(
            seriesId: UUID,
            seasonId: UUID,
        ) {
            val episodes =
                try {
                    api.showApi
                        .getEpisodes(
                            GetEpisodesRequest(
                                seriesId = seriesId,
                                userId = serverRepository.currentUser?.id,
                                seasonId = seasonId,
                                fields = rowFields,
                                enableUserData = true,
                            ),
                        ).content
                        .items
                        .map { BaseItem(it, false) }
                } catch (ex: Exception) {
                    emptyList()
                }
            _state.update { if (it.seasonId == seasonId) it.copy(episodes = episodes) else it }
        }

        fun selectSeason(seasonId: UUID) {
            val series = _state.value.item ?: return
            if (_state.value.seasonId == seasonId) return
            _state.update { it.copy(seasonId = seasonId, episodes = emptyList()) }
            viewModelScope.launchIO { loadEpisodes(series.id, seasonId) }
        }

        fun play(
            item: BaseItem,
            fromStart: Boolean = false,
        ) {
            navigationManager.navigateTo(Destination.Playback(item.id, if (fromStart) 0L else item.resumeMs))
        }

        /** Another film, show or person: opens it in this same info screen. */
        fun open(item: BaseItem) {
            navigationManager.navigateTo(Destination.MediaItem(item))
        }

        fun openPerson(
            id: UUID,
        ) {
            navigationManager.navigateTo(Destination.MediaItem(id, BaseItemKind.PERSON))
        }

        /** Wholphin's original details page, for its extra options. */
        fun openOriginal() {
            val item = _state.value.item ?: return
            navigationManager.navigateTo(Destination.OriginalDetails(item.id, item.type))
        }

        fun toggleFavorite() {
            val item = _state.value.item ?: return
            val on = item.data.userData?.isFavorite != true
            viewModelScope.launchIO {
                try {
                    favoriteWatchManager.setFavorite(item.id, on)
                    refreshItem(item.id)
                } catch (ex: Exception) {
                    Timber.w(ex, "Jellyeye: favourite failed")
                }
            }
        }

        fun toggleWatched() {
            val item = _state.value.item ?: return
            val on = item.data.userData?.played != true
            viewModelScope.launchIO {
                try {
                    favoriteWatchManager.setWatched(item.id, on)
                    refreshItem(item.id)
                    val seasonId = _state.value.seasonId
                    if (item.type == BaseItemKind.SERIES && seasonId != null) loadEpisodes(item.id, seasonId)
                } catch (ex: Exception) {
                    Timber.w(ex, "Jellyeye: watched failed")
                }
            }
        }

        private suspend fun refreshItem(itemId: UUID) {
            val item = BaseItem(api.libraryApi.getItem(itemId).content, false)
            withContext(Dispatchers.Main) { _state.update { it.copy(item = item) } }
        }
    }
