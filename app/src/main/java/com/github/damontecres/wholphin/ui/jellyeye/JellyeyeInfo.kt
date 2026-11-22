package com.github.damontecres.wholphin.ui.jellyeye

import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DialogItem
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.VideoRangeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

/**
 * Jellyeye info screen for films, TV shows and people: the TV version of the web pop-up.
 * Everything is stacked top to bottom, so you only ever scroll down.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JellyeyeInfo(
    itemId: UUID,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    seasonEpisode: SeasonEpisodeIds? = null,
    viewModel: JellyeyeInfoViewModel = hiltViewModel(key = "jellyeye-info-$itemId-${seasonEpisode?.seasonId}-${seasonEpisode?.episodeId}"),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId, seasonEpisode) }
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var episodeChoice by remember { mutableStateOf<BaseItem?>(null) }
    var playEpisodeFromMenu: (BaseItem) -> Unit by remember { mutableStateOf({ _: BaseItem -> }) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(JE.bg)) {
        state.item?.let { current ->
            if (showMenu) {
                JellyeyeOptions(current, state.chosenStreams, state.canDelete, viewModel, onDismiss = { showMenu = false })
            }
        }
        // one episode's full options (from its quick menu's "More options")
        state.menuItem?.let { episode ->
            JellyeyeOptions(episode, state.menuStreams, state.menuCanDelete, viewModel, onDismiss = viewModel::closeOptions)
        }
        // an episode's quick menu: Menu button or a long press on the episode
        episodeChoice?.let { episode ->
            EpisodeQuickMenu(
                episode = episode,
                onDismiss = { episodeChoice = null },
                onPlay = { playEpisodeFromMenu(episode) },
                onToggleWatched = { viewModel.toggleWatched(episode) },
                onPreviousWatched = { viewModel.markPreviousWatched(episode) },
                onMoreOptions = { viewModel.optionsFor(episode) },
            )
        }
        val item = state.item
        if (item == null) {
            Text(
                if (state.failed) "Couldn't load this title" else "Loading…",
                color = JE.dim,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        val firstFocus = remember { FocusRequester() }
        val listState = rememberLazyListState()
        // focus: the title you came back to (a poster, episode, cast member...), else the page's first button
        // (see JellyeyeFocus.kt). A collection's Play button only appears once its films have loaded.
        val memory = viewModel.focusMemory
        val restoreTile = remember { FocusRequester() }
        val focusMap = rememberReturnFocusMap(memory, restoreTile)
        PageFocus(
            memory,
            listState,
            ready = item.type != BaseItemKind.BOX_SET || state.films.isNotEmpty(),
            restore = restoreTile,
            main = firstFocus,
        )
        val open = { target: BaseItem -> memory.leaving(target.id, listState); viewModel.open(target) }
        val openPerson = { id: UUID -> memory.leaving(id, listState); viewModel.openPerson(id) }
        val playEpisode = { ep: BaseItem -> memory.leaving(ep.id, listState); viewModel.play(ep) }
        playEpisodeFromMenu = playEpisode
        val scope = rememberCoroutineScope()
        // anything focused in the header brings the whole header (the top of the page) back into view
        val showTop =
            Modifier.onFocusChanged {
                if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) }
            }
        CompositionLocalProvider(LocalBringIntoViewSpec provides JellyeyeScrollSpec) {
            LazyColumn(
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = JeLayout.pageMargin,
                        end = JeLayout.pageMargin,
                        top = JeLayout.pageTop,
                        bottom = JeLayout.pageBottom,
                    ),
                // no automatic gaps: long grids are drawn as touching pieces, a row at a time,
                // so every other block adds its own gap (gapItem)
                modifier = Modifier.fillMaxSize(),
            ) {
                if (item.type == BaseItemKind.PERSON) {
                    gapItem("person") { Box(showTop) { PersonHeader(item, state, firstFocus, viewModel) } }
                    item.data.overview?.takeIf { it.isNotBlank() }?.let { bio ->
                        gapItem("bio") { Biography(bio) }
                    }
                    posterSection("films", "Films in your library · ${state.films.size}", state.films, open, focusRequesters = focusMap)
                    posterSection("shows", "TV in your library · ${state.shows.size}", state.shows, open, focusRequesters = focusMap)
                } else {
                    gapItem("hero") {
                        Box(showTop) {
                            if (item.type == BaseItemKind.BOX_SET) {
                                CollectionSpotlight(item, state, now, firstFocus, viewModel, onOpen = open)
                            } else {
                                InfoHeader(item, state, now, firstFocus, viewModel, onMore = { showMenu = true })
                            }
                        }
                    }
                    if (hasAbout(item)) gapItem("about") { AboutRow(item) }
                    if (item.type == BaseItemKind.BOX_SET) {
                        posterSection("collection", "In this collection · ${state.films.size}", state.films, open, focusRequesters = focusMap)
                    }
                    if (item.type == BaseItemKind.SERIES && state.seasons.isNotEmpty()) {
                        episodeSection(state, viewModel, playEpisode, { episodeChoice = it }, focusMap)
                    }
                    val cast =
                        item.data.people.orEmpty().filter {
                            it.type == PersonKind.ACTOR || it.type == PersonKind.GUEST_STAR
                        }
                    if (cast.isNotEmpty()) {
                        gapItem("cast") {
                            SectionTile("Cast") { CastGrid(cast.take(32), onClick = { openPerson(it.id) }, focusRequesters = focusMap) }
                        }
                    }
                    if (state.similar.isNotEmpty()) {
                        gapItem("similar") {
                            SectionTile("More like this") {
                                PosterGrid(state.similar, rows = 3, onClick = open, fullRowsOnly = false, focusRequesters = focusMap)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------
   Header: backdrop, title or logo, details and the buttons
   ------------------------------------------------------------------ */
@Composable
private fun InfoHeader(
    item: BaseItem,
    state: JellyeyeInfoState,
    now: LocalDateTime,
    firstFocus: FocusRequester,
    viewModel: JellyeyeInfoViewModel,
    onMore: () -> Unit,
) {
    val imageUrls = LocalImageUrlService.current
    val isSeries = item.type == BaseItemKind.SERIES
    val isCollection = item.type == BaseItemKind.BOX_SET
    val ud = item.data.userData

    // what Play does: a film resumes; a show resumes or plays its next episode
    val chosen = state.episodes.firstOrNull { it.id == state.chosenEpisodeId }
    val target =
        when {
            isSeries -> chosen ?: state.nextEpisode
            // a collection plays the first thing you haven't watched yet
            isCollection -> state.films.firstOrNull { it.data.userData?.played != true } ?: state.films.firstOrNull()
            else -> item
        }
    val resumeMs = target?.resumeMs ?: 0L
    // keep the button short; what it plays goes on the line underneath
    val playLabel =
        when {
            isSeries && target != null ->
                (if (resumeMs > 0) "Resume" else "Play") + " S${target.data.parentIndexNumber ?: "?"}:E${target.data.indexNumber ?: "?"}"
            resumeMs > 0 -> "Resume"
            else -> "Play"
        }
    val runMins = minutesOf((target ?: item).data.runTimeTicks)
    val leftMins = (runMins - resumeMs / 60_000).coerceAtLeast(1)

    Box(
        Modifier
            .fillMaxWidth()
            .height(JeLayout.bannerHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF111214))
            .border(1.dp, JE.line, RoundedCornerShape(24.dp)),
    ) {
        val backdrop = remember(item.id) { imageUrls.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = 1280) }
        val zoom = bannerPushIn(item.id)
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF21B1C1F), Color(0x801B1C1F), Color(0x261B1C1F)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xF51B1C1F))))

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                when {
                    isSeries -> "TV SHOW"
                    isCollection -> "COLLECTION"
                    else -> "FILM"
                },
                color = JE.accent2,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            TitleLogo(item, maxWidth = 320.dp, textSize = 38)
            val years =
                if (isSeries) {
                    val start = item.data.productionYear
                    val end = item.data.endDate?.year
                    when {
                        start == null -> null
                        item.data.status == "Continuing" -> "$start–"
                        end != null && end != start -> "$start–$end"
                        else -> "$start"
                    }
                } else if (isCollection) {
                    val ys = state.films.mapNotNull { it.data.productionYear }
                    if (ys.isEmpty()) null else if (ys.min() == ys.max()) "${ys.min()}" else "${ys.min()}–${ys.max()}"
                } else {
                    item.data.productionYear?.toString()
                }
            val meta = listOfNotNull(years, if (isCollection) state.films.size.takeIf { it > 0 }?.let { "$it titles" } else lengthText(item), item.data.genres?.take(3)?.joinToString(" · ")?.takeIf { it.isNotBlank() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meta.joinToString("  ·  "), color = JE.soft, fontSize = 13.sp)
                item.data.officialRating?.let {
                    Text(
                        it,
                        color = JE.soft,
                        fontSize = 11.sp,
                        modifier =
                            Modifier
                                .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            // buttons drop onto a second line rather than squashing when there isn't room
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PillButton("▶  $playLabel", onClick = { (target ?: item).let { viewModel.play(it) } }, primary = true, focusRequester = firstFocus, leftEdge = true)
                if (resumeMs > 0 && target != null) {
                    PillButton("From the start", onClick = { viewModel.play(target, fromStart = true) })
                }
                PillButton(if (ud?.isFavorite == true) "♥  Favourite" else "♡  Favourite", onClick = viewModel::toggleFavorite)
                PillButton(if (ud?.played == true) "✓  Watched" else "Mark watched", onClick = viewModel::toggleWatched)
                PillButton("•••  More", onClick = onMore)
            }
            val what =
                when {
                    target == null || target.id == item.id -> null
                    isSeries -> "Next: S${target.data.parentIndexNumber ?: "?"}:E${target.data.indexNumber ?: "?"} · ${target.name ?: ""}"
                    else -> "Next: ${target.name ?: ""}"
                }
            val timing =
                when {
                    runMins <= 0 -> null
                    resumeMs > 0 -> "from ${clockText(resumeMs)} · $leftMins min left · ends at ${finishAt(now, leftMins)}"
                    else -> "${lengthOf(runMins)} · ends at ${finishAt(now, runMins)}"
                }
            val line = listOfNotNull(what, timing).joinToString(" · ")
            if (line.isNotBlank()) {
                Text(line, color = Color(0xFFB8BCC2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun lengthOf(mins: Long): String {
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) (if (m > 0) "${h}h ${m}m" else "${h}h") else "${m}m"
}

private fun clockText(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/* ------------------------------------------------------------------
   About: tagline and summary on the left; ratings and details on the right
   ------------------------------------------------------------------ */
/** The facts for the details panel; only the ones the title actually has. */
private fun detailFacts(item: BaseItem): List<Pair<String, String>> {
    val isSeries = item.type == BaseItemKind.SERIES
    val people = item.data.people.orEmpty()
    val streams = item.data.mediaStreams.orEmpty()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault } ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
    val subs = streams.count { it.type == MediaStreamType.SUBTITLE }
    val res =
        video?.let { v ->
            val w = v.width ?: 0
            val h = v.height ?: 0
            val size =
                when {
                    w >= 3800 || h >= 2000 -> "4K"
                    h >= 1000 -> "1080p"
                    h >= 700 -> "720p"
                    else -> "SD"
                }
            val range = v.videoRangeType.takeIf { it != VideoRangeType.SDR && it != VideoRangeType.UNKNOWN }?.name
            listOfNotNull(size, v.codec?.uppercase(), range).joinToString(" ")
        }
    val aud = audio?.let { a -> listOfNotNull(a.codec?.uppercase(), a.channelLayout ?: a.channels?.let { "${it}ch" }).joinToString(" ") }
    return listOfNotNull(
        people.filter { it.type == PersonKind.DIRECTOR }.take(2).joinToString(", ") { it.name ?: "" }.takeIf { it.isNotBlank() }?.let { "Director" to it },
        people.filter { it.type == PersonKind.WRITER }.take(2).joinToString(", ") { it.name ?: "" }.takeIf { it.isNotBlank() }?.let { "Writer" to it },
        item.data.studios.orEmpty().take(2).joinToString(", ") { it.name ?: "" }.takeIf { it.isNotBlank() }?.let { (if (isSeries) "Network" else "Studio") to it },
        item.data.premiereDate?.let { (if (isSeries) "First aired" else "Released") to it.toLocalDate().format(longDate) },
        res?.takeIf { it.isNotBlank() }?.let { "Video" to it },
        aud?.takeIf { it.isNotBlank() }?.let { "Audio" to it },
        subs.takeIf { it > 0 }?.let { "Subtitles" to (if (it == 1) "1 track" else "$it tracks") },
    )
}

private fun hasRatings(item: BaseItem): Boolean =
    item.data.communityRating != null ||
        item.data.criticRating != null ||
        (item.type != BaseItemKind.SERIES && (item.data.userData?.playedPercentage ?: 0.0) > 0.0)

/** Whether the ratings & details panel has anything worth showing. Collections never do: their dates, studio and progress belong to single films. */
private fun showFactsPanel(item: BaseItem): Boolean = item.type != BaseItemKind.BOX_SET && (hasRatings(item) || detailFacts(item).isNotEmpty())

private fun showSummary(item: BaseItem): Boolean =
    !item.data.overview.isNullOrBlank() || !item.data.taglines.isNullOrEmpty() || !item.data.genres.isNullOrEmpty()

/** True when the about section would show anything at all (otherwise it's left out, gap and all). */
private fun hasAbout(item: BaseItem): Boolean = showSummary(item) || showFactsPanel(item)

@Composable
private fun AboutRow(item: BaseItem) {
    val isSeries = item.type == BaseItemKind.SERIES
    Column(verticalArrangement = Arrangement.spacedBy(JeLayout.panelGap)) {
        if (showSummary(item)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item.data.taglines?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = JE.accent2, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                }
                item.data.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFDFE2E6), fontSize = 14.sp, lineHeight = 22.sp)
                }
                val genres = item.data.genres.orEmpty()
                if (genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        genres.take(6).forEach {
                            Text(
                                it,
                                color = JE.soft,
                                fontSize = 11.sp,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0x12FFFFFF))
                                        .padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        if (showFactsPanel(item)) {
            // ratings on the left, details laid out across the rest of the width
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(JE.tile)
                    .border(1.dp, JE.line, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (hasRatings(item)) {
                    val community = item.data.communityRating
                    val critic = item.data.criticRating
                    val progress = item.data.userData?.playedPercentage
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Label("Ratings")
                        community?.let { Meter("Audience", "★ ${"%.1f".format(it)} / 10", it / 10f, JE.accent) }
                        critic?.let { Meter("Critics", "${it.toInt()}%", it / 100f, JE.critic) }
                        if (!isSeries && (progress ?: 0.0) > 0.0) Meter("Your progress", "${progress!!.toInt()}%", (progress / 100).toFloat(), Color(0xFF4ADE80))
                    }
                }
                val facts = detailFacts(item)
                if (facts.isNotEmpty()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Label("Details")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            facts.forEach { (k, v) ->
                                Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(k, color = JE.dim, fontSize = 11.sp)
                                    Text(v, color = JE.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = JE.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

/* ------------------------------------------------------------------
   Episodes: season pills, then a list going down the page
   ------------------------------------------------------------------ */
/** Season pills, then the episodes one per row (built as you scroll, not all at once). */
private fun LazyListScope.episodeSection(
    state: JellyeyeInfoState,
    viewModel: JellyeyeInfoViewModel,
    onPlay: (BaseItem) -> Unit,
    onMenu: (BaseItem) -> Unit,
    focusRequesters: Map<UUID, FocusRequester>,
) {
    item(key = "episodes-top") {
        PanelTop("Episodes") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                state.seasons.forEach { season ->
                    val selected = season.id == state.seasonId
                    JeFocusable(onClick = { viewModel.selectSeason(season.id) }, corner = 20.dp, leftEdge = season === state.seasons.first()) {
                        Text(
                            season.name ?: "",
                            color = if (selected) JE.accent2 else JE.text,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) JE.accent.copy(alpha = 0.20f) else Color(0x12FFFFFF))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                // the whole of the season you're looking at, watched or not
                state.seasons.firstOrNull { it.id == state.seasonId }?.let { season ->
                    val done = season.data.userData?.played == true
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        if (done) "✓  Season watched" else "Mark season watched",
                        onClick = { viewModel.toggleWatched(season) },
                    )
                }
            }
            if (state.episodes.isEmpty()) {
                Text("Loading episodes…", color = JE.dim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            } else {
                Text("Right for the ✓ on an episode · hold OK or press ☰ for more", color = JE.dim, fontSize = 11.sp)
            }
        }
    }
    itemsIndexed(state.episodes, key = { _, ep -> "ep-${ep.id}" }) { _, ep ->
        PanelRow {
            EpisodeRow(
                ep = ep,
                state = state,
                onPlay = onPlay,
                onMenu = onMenu,
                onToggleWatched = { viewModel.toggleWatched(ep) },
                focusRequester = focusRequesters[ep.id],
            )
        }
    }
    item(key = "episodes-end") { PanelBottom() }
}

@Composable
private fun EpisodeRow(
    ep: BaseItem,
    state: JellyeyeInfoState,
    onPlay: (BaseItem) -> Unit,
    onMenu: (BaseItem) -> Unit,
    onToggleWatched: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val isChosen = ep.id == state.chosenEpisodeId
    val isNext = isChosen || (state.chosenEpisodeId == null && ep.id == state.nextEpisode?.id)
    val eud = ep.data.userData
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    JeTile(
        onClick = { onPlay(ep) },
        onLongClick = { onMenu(ep) },
        modifier = Modifier.weight(1f),
        focusRequester = focusRequester,
        leftEdge = true,
    ) { focused ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isNext) JE.accent.copy(alpha = 0.08f) else Color(0x08FFFFFF))
                    .border(1.dp, if (isNext) JE.accent.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .coverRing(focused, RoundedCornerShape(15.dp))
                    .width(150.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2B2E33)),
            ) {
                val url = remember(ep.id) { imageUrls.getItemImageUrl(ep, ImageType.PRIMARY, fillWidth = 400) }
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                val pct = (eud?.playedPercentage ?: 0.0).toFloat()
                if (pct > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(JE.accent))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${ep.data.indexNumber?.let { "$it. " } ?: ""}${ep.name ?: ""}",
                    color = if (focused) Color.White else JE.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val bits =
                    listOfNotNull(
                        minutesOf(ep.data.runTimeTicks).takeIf { it > 0 }?.let { lengthOf(it) },
                        ep.data.premiereDate?.toLocalDate()?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)),
                        if (eud?.played == true) "✓ Watched" else null,
                        if (isChosen) "Selected" else if (isNext) "Up next" else null,
                    )
                Text(bits.joinToString(" · "), color = if (isNext) JE.accent2 else JE.dim, fontSize = 11.sp)
                ep.data.overview?.let {
                    Text(it, color = JE.soft, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    WatchedTick(watched = eud?.played == true, onClick = onToggleWatched)
    }
}

/** The round ✓ at the end of an episode: filled when watched, an outline when not. Select to switch. */
@Composable
private fun WatchedTick(
    watched: Boolean,
    onClick: () -> Unit,
) {
    JeFocusable(onClick = onClick, corner = 24.dp) { focused ->
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (watched) JE.accent else Color(0x14FFFFFF))
                .border(1.dp, if (watched) JE.accent else Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✓",
                color = if (watched) JE.onAccent else if (focused) Color.White else JE.dim,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/* ------------------------------------------------------------------
   Cast: round photos in rows going down the page
   ------------------------------------------------------------------ */
@Composable
private fun CastGrid(
    cast: List<BaseItemPerson>,
    onClick: (BaseItemPerson) -> Unit,
    focusRequesters: Map<UUID, FocusRequester> = emptyMap(),
) {
    val imageUrls = LocalImageUrlService.current
    val columns = 8
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cast.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { person ->
                    JeTile(
                        onClick = { onClick(person) },
                        modifier = Modifier.weight(1f),
                        focusRequester = focusRequesters[person.id],
                        leftEdge = person === row.first(),
                    ) { focused ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                Modifier
                                    .coverRing(focused, CircleShape)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2E33)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (person.primaryImageTag != null) {
                                    val url =
                                        remember(person.id) {
                                            imageUrls.getItemImageUrl(itemId = person.id, imageType = ImageType.PRIMARY, tag = person.primaryImageTag, fillWidth = 200)
                                        }
                                    AsyncImage(model = url, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text((person.name ?: "?").take(1), color = JE.dim, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Text(person.name ?: "", color = JE.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            person.role?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = JE.dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ------------------------------------------------------------------
   People: photo, born / age, counts, biography
   ------------------------------------------------------------------ */
@Composable
private fun PersonHeader(
    person: BaseItem,
    state: JellyeyeInfoState,
    firstFocus: FocusRequester,
    viewModel: JellyeyeInfoViewModel,
) {
    val imageUrls = LocalImageUrlService.current
    val photo = remember(person.id) { imageUrls.getItemImageUrl(person, ImageType.PRIMARY, fillWidth = 500) }
    val born = person.data.premiereDate?.toLocalDate()
    val died = person.data.endDate?.toLocalDate()
    val place = person.data.productionLocations?.firstOrNull()
    val line =
        when {
            born != null && died != null -> "Born ${born.format(longDate)}${place?.let { " in $it" } ?: ""} · Died ${died.format(longDate)} aged ${Period.between(born, died).years}"
            born != null -> "Born ${born.format(longDate)}${place?.let { " in $it" } ?: ""} · Age ${Period.between(born, LocalDate.now()).years}"
            place != null -> "Born in $place"
            else -> null
        }
    // a backdrop from their best-known title in your library, so the banner looks like the others
    val backdropFrom = remember(state.films, state.shows) { (state.films + state.shows).maxByOrNull { it.data.communityRating ?: 0f } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(JeLayout.bannerHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(JE.accent.copy(alpha = 0.20f), Color(0xFF111214))))
            .border(1.dp, JE.line, RoundedCornerShape(24.dp)),
    ) {
        if (backdropFrom != null) {
            val url = remember(backdropFrom.id) { imageUrls.getItemImageUrl(backdropFrom, ImageType.BACKDROP, fillWidth = 1280) }
            val zoom = bannerPushIn(person.id)
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 0.45f
                        scaleX = zoom.value
                        scaleY = zoom.value
                    },
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF2111214), Color(0xB3111214), Color(0x40111214)))))
        }
        Row(
            Modifier.fillMaxSize().padding(22.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2B2E33)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = photo, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CAST & CREW", color = JE.accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(person.name ?: "", color = Color.White, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                line?.let { Text(it, color = JE.soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Stat(state.films.size, if (state.films.size == 1) "film in your library" else "films in your library")
                    Stat(state.shows.size, if (state.shows.size == 1) "TV show" else "TV shows")
                    Stat((state.films + state.shows).count { it.data.userData?.played == true }, "watched")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val fav = person.data.userData?.isFavorite == true
                    PillButton(if (fav) "♥  Favourite" else "♡  Favourite", onClick = viewModel::toggleFavorite, focusRequester = firstFocus, leftEdge = true)
                }
            }
        }
    }
}

/** A person's biography, in its own panel under the banner; opens up fully when selected. */
@Composable
private fun Biography(bio: String) {
    var open by remember(bio) { mutableStateOf(false) }
    SectionTile("Biography") {
        JeFocusable(onClick = { open = !open }, corner = 14.dp, modifier = Modifier.fillMaxWidth(), leftEdge = true) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    bio,
                    color = Color(0xFFDFE2E6),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = if (open) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(if (open) "Show less" else "Read more", color = JE.accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ------------------------------------------------------------------
   Collections: the home page spotlight, cycling through the films in it
   ------------------------------------------------------------------ */
@Composable
private fun CollectionSpotlight(
    collection: BaseItem,
    state: JellyeyeInfoState,
    now: LocalDateTime,
    firstFocus: FocusRequester,
    viewModel: JellyeyeInfoViewModel,
    onOpen: (BaseItem) -> Unit,
) {
    val all = state.films
    if (all.isEmpty()) {
        // still loading the films: hold the space so the page doesn't jump
        Box(
            Modifier
                .fillMaxWidth()
                .height(JeLayout.bannerHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(JE.tile)
                .border(1.dp, JE.line, RoundedCornerShape(24.dp)),
        )
        return
    }
    // a new random set for a themed collection each time it wears out (and each time the page is opened)
    var round by remember { mutableIntStateOf(0) }
    // the titles on the banner right now (a plain holder, not state: it only steers the next pick)
    val shown = remember { mutableListOf<UUID>() }
    val picks =
        remember(all, state.collectionInOrder, round) {
            val (unseen, seen) = all.partition { it.data.userData?.played != true }
            val chosen =
                if (state.collectionInOrder) {
                    // a film series: up to six, unwatched first, shown in release order
                    if (all.size <= 6) all else (unseen + seen).take(6).sortedBy { all.indexOf(it) }
                } else {
                    // a themed mix: six at random, unwatched first, avoiding the ones just shown where possible
                    val fresh = { list: List<BaseItem> -> list.filter { it.id !in shown }.shuffled() + list.filter { it.id in shown }.shuffled() }
                    (fresh(unseen) + fresh(seen)).take(6)
                }
            shown.clear()
            shown.addAll(chosen.map { it.id })
            chosen.map { SpotlightItem(it, it.data.productionYear?.toString() ?: "") }
        }
    val ud = collection.data.userData
    Spotlight(
        items = picks,
        now = now,
        playFocus = firstFocus,
        onPlay = { viewModel.play(it) },
        onInfo = onOpen,
        badge = { _, _ -> "Collection · ${collection.name ?: ""} · ${all.size} ${if (all.size == 1) "title" else "titles"}" },
        onWornOut = if (state.collectionInOrder || all.size <= 6) null else ({ round++ }),
        extraButtons = {
            // the whole collection: every film back to back in release order, or shuffled
            PillButton("▶▶  Play all", onClick = { viewModel.playCollection(shuffle = false) })
            PillButton("⇄  Shuffle", onClick = { viewModel.playCollection(shuffle = true) })
            PillButton(if (ud?.isFavorite == true) "♥  Favourite" else "♡  Favourite", onClick = viewModel::toggleFavorite)
            PillButton(if (ud?.played == true) "✓  Watched" else "Mark watched", onClick = viewModel::toggleWatched)
        },
    )
}

@Composable
private fun Stat(
    value: Int,
    label: String,
) {
    Column {
        Text("$value", color = JE.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = JE.dim, fontSize = 11.sp)
    }
}

/* ------------------------------------------------------------------
   "More": Wholphin's own options pop-up (audio, subtitles, version, play with, playlists, media info, delete…)
   ------------------------------------------------------------------ */
@Composable
private fun JellyeyeOptions(
    item: BaseItem,
    chosenStreams: ChosenStreams?,
    canDelete: Boolean,
    viewModel: JellyeyeInfoViewModel,
    onDismiss: () -> Unit,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    var details by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var playlistFor by remember { mutableStateOf<UUID?>(null) }
    var menuOpen by remember { mutableStateOf(true) }
    val playlistState by playlistViewModel.playlistState.collectAsState()
    val actions =
        remember(item.id) {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onShowOverview = { details = ItemDetailsDialogInfo(it) },
                onClickWatch = viewModel::setWatched,
                onClickFavorite = viewModel::setFavorite,
                onClickAddPlaylist = { id ->
                    playlistViewModel.loadPlaylists()
                    playlistFor = id
                },
                onSendMediaInfo = { viewModel.sendMediaReport(it) },
                onDeleteItem = { if (it.type == BaseItemKind.EPISODE) viewModel.deleteEpisode(it) else viewModel.deleteThis(it) },
                onChooseVersion = { i, source -> source.id?.toUUIDOrNull()?.let { viewModel.savePlayVersion(i, it) } },
                onChooseTracks = { r -> viewModel.saveTrackSelection(r.item, r.itemPlayback, r.trackIndex, r.streamType) },
                onClearChosenStreams = viewModel::clearChosenStreams,
                onClickAddToQueue = playlistViewModel::addToQueue,
            )
        }
    if (menuOpen) {
        ContextMenuDialog(
            // closing the menu: if a choice opened a follow-up (media info, playlists) that stays up,
            // otherwise the whole thing closes (see the end of this function)
            onDismissRequest = { menuOpen = false },
            contextMenu =
                ContextMenu.ForBaseItem(
                    fromLongClick = false,
                    item = item,
                    chosenStreams = chosenStreams,
                    showGoTo = false,
                    showStreamChoices = item.type != BaseItemKind.SERIES,
                    canDelete = canDelete,
                    canRemoveContinueWatching = false,
                    canRemoveNextUp = false,
                    actions = actions,
                ),
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            preferredSubtitleLanguage = viewModel.subtitleLanguage,
        )
    }
    details?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = viewModel.isAdministrator,
            onDismissRequest = {
                details = null
                onDismiss()
            },
        )
    }
    playlistFor?.let { id ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = {
                playlistFor = null
                onDismiss()
            },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, id)
                playlistFor = null
                onDismiss()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, id)
                playlistFor = null
                onDismiss()
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
    if (!menuOpen && details == null && playlistFor == null) {
        LaunchedEffect(Unit) { onDismiss() }
    }
}

/** The banner's slow push-in: starts at normal size when the page opens and zooms in gently over about 10 seconds, then holds. */
@Composable
private fun bannerPushIn(key: Any): Animatable<Float, AnimationVector1D> {
    val zoom = remember(key) { Animatable(1f) }
    LaunchedEffect(key, JeSettings.bannerZoom) {
        if (JeSettings.bannerZoom) {
            zoom.animateTo(1.06f, tween(10_000, easing = LinearEasing))
        } else {
            zoom.snapTo(1f)
        }
    }
    return zoom
}

/** An episode's quick menu (Menu button or holding OK): play it, tick it, catch up to it, or every option. */
@Composable
private fun EpisodeQuickMenu(
    episode: BaseItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onPreviousWatched: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    val watched = episode.data.userData?.played == true
    val resume = episode.resumeMs > 0
    val name =
        listOfNotNull(
            episode.data.parentIndexNumber?.let { s -> episode.data.indexNumber?.let { e -> "S$s:E$e" } },
            episode.name,
        ).joinToString(" · ")
    val items =
        remember(episode.id, watched) {
            listOf(
                DialogItem(if (resume) "Resume" else "Play", R.string.fa_play, dismissOnClick = true) { onPlay() },
                DialogItem(if (watched) "Mark as unwatched" else "Mark as watched", if (watched) R.string.fa_eye_slash else R.string.fa_eye, dismissOnClick = true) {
                    onToggleWatched()
                },
                DialogItem("Mark all previous episodes as watched", R.string.fa_check, dismissOnClick = true) { onPreviousWatched() },
                DialogItem("More options…", R.string.fa_ellipsis, dismissOnClick = true) { onMoreOptions() },
            )
        }
    DialogPopup(
        showDialog = true,
        title = name,
        dialogItems = items,
        onDismissRequest = onDismiss,
        dismissOnClick = true,
        elevation = 3.dp,
    )
}
