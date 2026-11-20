package com.github.damontecres.wholphin.ui.jellyeye

import org.jellyfin.sdk.model.api.MediaStreamType
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ChosenStreams
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
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
    // the episode that was opened (from search, a library row…), if any
    val chosenEpisodeId: UUID? = null,
    // films and TV shows
    val similar: List<BaseItem> = emptyList(),
    // people
    val films: List<BaseItem> = emptyList(),
    val shows: List<BaseItem> = emptyList(),
    // for the options pop-up: saved audio/subtitle/version choices, and whether delete is allowed
    val chosenStreams: ChosenStreams? = null,
    val canDelete: Boolean = false,
    // collections: a film series (kept in release order) or a themed mix (random banner, A-Z grid)
    val collectionInOrder: Boolean = true,
)

@HiltViewModel
class JellyeyeInfoViewModel
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        @param:ApplicationContext private val context: Context,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        val streamChoiceService: StreamChoiceService,
        private val serverReportService: ServerReportService,
        private val mediaManagementService: MediaManagementService,
        private val userPreferencesService: UserPreferencesService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(JellyeyeInfoState())
        val state: StateFlow<JellyeyeInfoState> = _state

        private var loadedId: UUID? = null
        private var seasonEpisode: SeasonEpisodeIds? = null

        private val rowFields =
            listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.CHILD_COUNT, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

        fun load(
            itemId: UUID,
            seasonEpisode: SeasonEpisodeIds? = null,
            force: Boolean = false,
        ) {
            if (!force && loadedId == itemId) return
            loadedId = itemId
            this.seasonEpisode = seasonEpisode
            _state.update { it.copy(chosenEpisodeId = seasonEpisode?.episodeId) }
            viewModelScope.launchIO {
                try {
                    val item = BaseItem(api.libraryApi.getItem(itemId).content, false)
                    _state.update { it.copy(item = item, failed = false) }
                    viewModelScope.launchIO { loadOptions(item) }
                    coroutineScope {
                        when (item.type) {
                            BaseItemKind.BOX_SET -> loadCollection(itemId)

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

        /** A collection: everything in it, oldest first, shown as a grid going down. */
        private suspend fun loadCollection(itemId: UUID) {
            val children =
                api.libraryApi
                    .getItems(
                        GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
                            parentId = itemId,
                            fields = rowFields + ItemFields.PROVIDER_IDS + ItemFields.SORT_NAME,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING, SortOrder.ASCENDING, SortOrder.ASCENDING),
                            enableUserData = true,
                            limit = 500,
                        ),
                    ).content
                    .items
                    .map { BaseItem(it, false) }
            val inOrder = isFilmSeries(_state.value.item, children)
            val films = if (inOrder) children else children.sortedBy { (it.data.sortName ?: it.name ?: "").lowercase() }
            _state.update { it.copy(films = films, collectionInOrder = inOrder) }
        }

        /**
         * Is this collection a film series (keep release order) or a themed mix (random banner, A-Z grid)?
         * 1. a "shuffle" or "in order" tag on the collection always wins;
         * 2. otherwise, if half or more of the films belong to the same film series (as recorded by
         *    Jellyfin's film details), it's a series;
         * 3. with no series information, 12 titles or fewer count as a series, bigger ones as a mix.
         */
        private fun isFilmSeries(
            collection: BaseItem?,
            films: List<BaseItem>,
        ): Boolean {
            val tags = collection?.data?.tags.orEmpty().map { it.trim().lowercase() }
            if (tags.any { it == "shuffle" || it == "random" || it == "shuffled" }) return false
            if (tags.any { it == "in order" || it == "inorder" || it == "ordered" || it == "in-order" }) return true
            val seriesIds =
                films.mapNotNull { f ->
                    f.data.providerIds
                        ?.entries
                        ?.firstOrNull { it.key.equals("TmdbCollection", ignoreCase = true) && !it.value.isNullOrBlank() }
                        ?.value
                }
            if (seriesIds.isNotEmpty()) {
                val biggest = seriesIds.groupingBy { it }.eachCount().values.max()
                return biggest * 2 >= films.size
            }
            return films.size <= 12
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
                seasonEpisode?.seasonId?.takeIf { id -> seasons.any { it.id == id } }
                    ?: next?.data?.seasonId
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

        /* ---------------- the options pop-up (Wholphin's own menu) ---------------- */

        private suspend fun loadOptions(item: BaseItem) {
            try {
                val prefs = userPreferencesService.getCurrent()
                val chosen =
                    if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.VIDEO || item.type == BaseItemKind.EPISODE) {
                        itemPlaybackRepository.getSelectedTracks(item.id, item, prefs)
                    } else {
                        null
                    }
                val canDelete = mediaManagementService.canDelete(item)
                _state.update { it.copy(chosenStreams = chosen, canDelete = canDelete) }
            } catch (ex: Exception) {
                Timber.w(ex, "Jellyeye: options failed to load")
            }
        }

        val isAdministrator: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        val subtitleLanguage: String? get() = serverRepository.currentUserDto?.configuration?.subtitleLanguagePreference

        fun navigateTo(destination: Destination) = navigationManager.navigateTo(destination)

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    favoriteWatchManager.setWatched(itemId, played)
                    _state.value.item?.let { refreshItem(it.id) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Jellyeye: watched failed")
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    favoriteWatchManager.setFavorite(itemId, favorite)
                    _state.value.item?.let { refreshItem(it.id) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Jellyeye: favourite failed")
                }
            }
        }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen = result?.let { itemPlaybackRepository.getChosenItemFromPlayback(item, it, prefs) }
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val result = itemPlaybackRepository.saveTrackSelection(item = item, itemPlayback = itemPlayback, trackIndex = trackIndex, type = type)
                val chosen = itemPlaybackRepository.getChosenItemFromPlayback(item, result, prefs)
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun clearChosenStreams(chosenStreams: ChosenStreams?) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                _state.value.item?.let { loadOptions(it) }
            }
        }

        fun sendMediaReport(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        fun deleteThis(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) { navigationManager.goBack() }
        }

        /** A collection played start to finish in release order, or shuffled. */
        fun playCollection(shuffle: Boolean) {
            val item = _state.value.item ?: return
            navigationManager.navigateTo(
                Destination.PlaybackList(
                    itemId = item.id,
                    startIndex = 0,
                    shuffle = shuffle,
                    recursive = true,
                    sortAndDirection = SortAndDirection(ItemSortBy.PREMIERE_DATE, SortOrder.ASCENDING),
                ),
            )
        }

        /** Wholphin's original details page, for its extra options. */
        fun openOriginal() {
            val item = _state.value.item ?: return
            navigationManager.navigateTo(Destination.OriginalDetails(item.id, item.type, seasonEpisode))
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
                    if (item.type == BaseItemKind.BOX_SET) loadCollection(item.id)
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
