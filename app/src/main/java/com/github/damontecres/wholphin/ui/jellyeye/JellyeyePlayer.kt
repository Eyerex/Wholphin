package com.github.damontecres.wholphin.ui.jellyeye

import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/*
 * Jellyeye's look for the player: the title header on the controls, the pause screen, the "Up next" card,
 * the info pop-up while watching, and the screensaver.
 */

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** The title's logo for the player: an episode uses its show's logo. Asked for at about the size drawn. */
internal fun playerLogoUrl(
    imageUrls: ImageUrlService,
    item: BaseItem,
): String? {
    item.data.imageTags?.get(ImageType.LOGO)?.let { tag ->
        return imageUrls.getItemImageUrl(itemId = item.id, imageType = ImageType.LOGO, maxWidth = 700, maxHeight = 260, tag = tag)
    }
    val parent = item.data.parentLogoItemId ?: item.data.seriesId ?: return null
    val tag = item.data.parentLogoImageTag ?: return null
    return imageUrls.getItemImageUrl(itemId = parent, imageType = ImageType.LOGO, maxWidth = 700, maxHeight = 260, tag = tag)
}

/** "S2:E5 · Episode title" for episodes; "2019 · 12A · Drama" otherwise. */
internal fun playerSubtitle(item: BaseItem): String =
    if (item.type == BaseItemKind.EPISODE) {
        listOfNotNull(
            item.data.parentIndexNumber?.let { s -> item.data.indexNumber?.let { e -> "S$s:E$e" } },
            item.name,
        ).joinToString(" · ")
    } else {
        listOfNotNull(
            item.data.productionYear?.toString(),
            item.data.officialRating,
            item.data.genres?.take(2)?.joinToString(" · ")?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

/** Logo (or the name in text) for the player and pause screen; text shows until the logo has loaded. */
@Composable
internal fun PlayerLogo(
    item: BaseItem,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    textSize: Int,
) {
    val imageUrls = LocalImageUrlService.current
    val url = remember(item.id) { playerLogoUrl(imageUrls, item) }
    var ready by remember(item.id) { mutableStateOf(false) }
    val name = if (item.type == BaseItemKind.EPISODE) item.data.seriesName ?: item.name else item.name
    Box(contentAlignment = Alignment.BottomStart) {
        if (!ready) {
            Text(
                name ?: "",
                color = Color.White,
                fontSize = textSize.sp,
                lineHeight = (textSize + 2).sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onSuccess = { ready = true },
                onError = { ready = false },
                modifier =
                    Modifier
                        .heightIn(max = maxHeight)
                        .widthIn(max = maxWidth)
                        .graphicsLayer { alpha = if (ready) 1f else 0f },
            )
        }
    }
}

/** A small rounded label, e.g. "Ends at 21:47". */
@Composable
internal fun PlayerChip(
    text: String,
    gold: Boolean = false,
) {
    Text(
        text,
        color = if (gold) JE.accent2 else JE.text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (gold) JE.accent.copy(alpha = 0.18f) else Color(0x33000000))
                .border(1.dp, if (gold) JE.accent.copy(alpha = 0.45f) else Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Minutes left and the finishing time, kept up to date. */
@Composable
internal fun rememberFinish(player: Player): Pair<Long, String> {
    var left by remember(player) { mutableLongStateOf(0L) }
    var ends by remember(player) { mutableStateOf("") }
    LaunchedEffect(player) {
        while (true) {
            val remainingMs =
                ((player.duration - player.currentPosition).coerceAtLeast(0) / player.playbackParameters.speed).toLong()
            left = (remainingMs / 60_000).coerceAtLeast(if (remainingMs > 0) 1 else 0)
            ends = LocalDateTime.now().plusSeconds(remainingMs / 1000).format(clock)
            delay(1_000)
        }
    }
    return left to ends
}

/* ------------------------------------------------------------------
   1. The title header on the player controls
   ------------------------------------------------------------------ */
@Composable
fun JellyeyePlayerTitle(
    item: BaseItem?,
    player: Player,
    modifier: Modifier = Modifier,
) {
    val (left, ends) = rememberFinish(player)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item != null) {
            PlayerLogo(item, maxWidth = 360.dp, maxHeight = 72.dp, textSize = 30)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            item?.let { i ->
                playerSubtitle(i).takeIf { it.isNotBlank() }?.let {
                    Text(it, color = JE.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (ends.isNotEmpty() && player.duration > 0) {
                PlayerChip("$left min left · ends at $ends", gold = true)
            }
        }
    }
}

/* ------------------------------------------------------------------
   2. The pause screen: after a few seconds paused (with the controls hidden)
   ------------------------------------------------------------------ */
@Composable
fun JellyeyePauseScreen(
    player: Player,
    item: BaseItem?,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var pausedFor by remember(player) { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            val paused =
                !player.playWhenReady &&
                    (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            pausedFor = if (paused) pausedFor + 250 else 0
            delay(250)
        }
    }
    val show = item != null && !controlsVisible && pausedFor >= 4_000
    AnimatedVisibility(show, enter = fadeIn(tween(700)), exit = fadeOut(tween(250)), modifier = modifier) {
        if (item == null) return@AnimatedVisibility
        val (left, ends) = rememberFinish(player)
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x8C000000)))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(0xE6000000), Color(0x66000000), Color.Transparent))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 56.dp, bottom = 56.dp)
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("PAUSED", color = JE.accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                PlayerLogo(item, maxWidth = 420.dp, maxHeight = 110.dp, textSize = 40)
                playerSubtitle(item).takeIf { it.isNotBlank() }?.let {
                    Text(it, color = JE.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                item.data.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFDFE2E6), fontSize = 14.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (ends.isNotEmpty() && player.duration > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerChip("$left min left", gold = true)
                        PlayerChip("Carry on now and it ends at $ends")
                    }
                }
                Text("Press any button to carry on", color = JE.dim, fontSize = 12.sp)
            }
        }
    }
}

/* ------------------------------------------------------------------
   3. "Up next" at the end of an episode
   ------------------------------------------------------------------ */
@Composable
fun JellyeyeUpNext(
    next: BaseItem,
    timeLeftSeconds: Long?,
    onPlay: () -> Unit,
    onBackToShow: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val imageUrls = LocalImageUrlService.current
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (playFocus.tryRequestFocus()) return@LaunchedEffect
            delay(50)
        }
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(JE.bg.copy(alpha = 0.94f))
            .border(1.dp, JE.line, RoundedCornerShape(22.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2B2E33)),
        ) {
            val still = remember(next.id) { imageUrls.getItemImageUrl(next, ImageType.PRIMARY, fillWidth = 640) }
            AsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (timeLeftSeconds != null && timeLeftSeconds > 0) {
                Text(
                    "$timeLeftSeconds",
                    color = JE.onAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(JE.accent)
                            .padding(top = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("UP NEXT", color = JE.accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text(playerSubtitle(next), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            next.data.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = JE.soft, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val mins = minutesOf(next.data.runTimeTicks)
            if (mins > 0) Text("${mins}m · ends at ${finishAt(now, mins)}", color = JE.dim, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PillButton(
                    if (timeLeftSeconds != null && timeLeftSeconds > 0) "▶  Play now · ${timeLeftSeconds}s" else "▶  Play now",
                    onClick = onPlay,
                    primary = true,
                    focusRequester = playFocus,
                )
                onBackToShow?.let { PillButton("Back to the show", onClick = it) }
            }
        }
    }
}

/* ------------------------------------------------------------------
   4. Info while watching: summary, details and cast, without stopping
   ------------------------------------------------------------------ */
@HiltViewModel
class JellyeyePlaybackInfoViewModel
    @Inject
    constructor(
        private val api: ApiClient,
    ) : ViewModel() {
        private val _item = MutableStateFlow<BaseItem?>(null)
        val item: StateFlow<BaseItem?> = _item
        private var loaded: UUID? = null

        fun load(itemId: UUID) {
            if (loaded == itemId) return
            loaded = itemId
            viewModelScope.launchIO {
                try {
                    _item.value = BaseItem(api.libraryApi.getItem(itemId).content, false)
                } catch (ex: Exception) {
                    _item.value = null
                }
            }
        }
    }

@Composable
fun JellyeyePlaybackInfo(
    playing: BaseItem,
    onDismiss: () -> Unit,
    viewModel: JellyeyePlaybackInfoViewModel = hiltViewModel(key = "jellyeye-playing-info"),
) {
    LaunchedEffect(playing.id) { viewModel.load(playing.id) }
    val full by viewModel.item.collectAsState()
    val item = full?.takeIf { it.id == playing.id } ?: playing
    val imageUrls = LocalImageUrlService.current
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (closeFocus.tryRequestFocus()) return@LaunchedEffect
            delay(50)
        }
    }
    // Nothing in the middle can take focus, so Up/Down scroll it while you stay on "Back to watching"
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    val down = e.key == Key.DirectionDown
                    val up = e.key == Key.DirectionUp
                    when {
                        !down && !up -> false
                        down && !scroll.canScrollForward -> false
                        up && !scroll.canScrollBackward -> false
                        else -> {
                            if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(if (down) 220f else -220f) }
                            true
                        }
                    }
                }.width(720.dp)
                .heightIn(max = 470.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(JE.bg.copy(alpha = 0.97f))
                .border(1.dp, JE.line, RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerLogo(item, maxWidth = 360.dp, maxHeight = 72.dp, textSize = 30)
            playerSubtitle(item).takeIf { it.isNotBlank() }?.let { Text(it, color = JE.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item.data.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFDFE2E6), fontSize = 14.sp, lineHeight = 21.sp)
                }
                val facts =
                    listOfNotNull(
                        item.data.communityRating?.let { "★ ${"%.1f".format(it)}" },
                        item.data.criticRating?.let { "Critics ${it.toInt()}%" },
                        item.data.people.orEmpty().filter { it.type == PersonKind.DIRECTOR }.take(2)
                            .joinToString(", ") { it.name ?: "" }.takeIf { it.isNotBlank() }?.let { "Directed by $it" },
                        item.data.premiereDate?.toLocalDate()?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)),
                    )
                if (facts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts.forEach { PlayerChip(it) }
                    }
                }
                val cast: List<BaseItemPerson> =
                    item.data.people.orEmpty().filter { it.type == PersonKind.ACTOR || it.type == PersonKind.GUEST_STAR }.take(16)
                if (cast.isNotEmpty()) {
                    Text("CAST", color = JE.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cast.forEach { p ->
                            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF2B2E33)), contentAlignment = Alignment.Center) {
                                    if (p.primaryImageTag != null) {
                                        val url = remember(p.id) { imageUrls.getItemImageUrl(itemId = p.id, imageType = ImageType.PRIMARY, tag = p.primaryImageTag, fillWidth = 160) }
                                        AsyncImage(model = url, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Text((p.name ?: "?").take(1), color = JE.dim, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text(p.name ?: "", color = JE.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                p.role?.takeIf { it.isNotBlank() }?.let { Text(it, color = JE.dim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (scroll.canScrollForward || scroll.canScrollBackward) {
                    Text(
                        when {
                            scroll.canScrollForward && scroll.canScrollBackward -> "▲ ▼  to scroll"
                            scroll.canScrollForward -> "▼  more below"
                            else -> "▲  back to the top"
                        },
                        color = JE.dim,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                PillButton("Back to watching", onClick = onDismiss, primary = true, focusRequester = closeFocus)
            }
        }
    }
}

/* ------------------------------------------------------------------
   8. The screensaver: your library's backdrops, slowly pushing in, with the title and a big clock
   ------------------------------------------------------------------ */
@Composable
fun JellyeyeScreensaverSlide(
    item: BaseItem?,
    backdropUrl: String?,
    title: String,
    showClock: Boolean,
    animate: Boolean,
    slideMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black)) {
        Crossfade(targetState = backdropUrl, animationSpec = tween(2_000), label = "screensaver") { url ->
            val zoom = remember(url) { Animatable(1f) }
            LaunchedEffect(url) {
                if (animate) zoom.animateTo(1.08f, tween(slideMs.toInt().coerceAtLeast(4_000) + 2_000, easing = LinearEasing))
            }
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                    },
            )
        }
        // darken the bottom-left for the title, the top-right for the clock
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xD9000000))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x99000000), Color.Transparent, Color.Transparent))))
        Crossfade(targetState = item, animationSpec = tween(1_200), label = "screensaverTitle", modifier = Modifier.align(Alignment.BottomStart)) { i ->
            Column(
                Modifier
                    .padding(start = 56.dp, bottom = 48.dp)
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (i != null) {
                    PlayerLogo(i, maxWidth = 380.dp, maxHeight = 100.dp, textSize = 38)
                    playerSubtitle(i).takeIf { it.isNotBlank() }?.let { Text(it, color = JE.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                } else if (title.isNotBlank()) {
                    Text(title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                }
            }
        }
        if (showClock) {
            var now by remember { mutableStateOf(LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = LocalDateTime.now()
                    delay(5_000)
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 48.dp), horizontalAlignment = Alignment.End) {
                Text(now.format(clock), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)), color = JE.soft, fontSize = 16.sp)
            }
        }
    }
}
