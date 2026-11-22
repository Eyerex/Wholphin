package com.github.damontecres.wholphin.ui.jellyeye

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.UUID
import androidx.compose.runtime.MutableState
import com.github.damontecres.wholphin.preferences.JellyeyePreferences
import com.github.damontecres.wholphin.preferences.AppThemeColors
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * How the page scrolls when the remote moves: as little as possible, but always keeping what you've
 * selected fully on screen with a little room around it. (Android TV's default moves the selected
 * thing to about a third of the way down, which pushed the top of the spotlight off the screen.)
 */
@OptIn(ExperimentalFoundationApi::class)
internal object JellyeyeScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val margin = containerSize * 0.10f
        val above = offset - margin
        val below = offset + size + margin - containerSize
        return when {
            above < 0f -> above
            below > 0f -> minOf(below, above)
            else -> 0f
        }
    }
}

/** Jellyeye colours: the same palette as the web design. */
/** The side menu's focus target (the selected menu item). Left from the first title in a row goes straight to it. */
val LocalMenuFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** Left (on the first title of a row, or the first button) goes into the side menu; if that can't happen, Left works as usual. */
internal fun Modifier.leftOpensMenu(menu: FocusRequester): Modifier =
    onPreviewKeyEvent { e ->
        e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && menu.focusNow()
    }

/** One Jellyeye colour set per Wholphin colour theme (vivid versions, so rings and buttons stay punchy). */
data class JePalette(
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val bg: Color,
    val tile: Color,
)

private val darkText = Color(0xFF1A1A1A)
private val JE_PALETTES: Map<AppThemeColors, JePalette> =
    mapOf(
        AppThemeColors.ORANGE to JePalette(Color(0xFFE5A00D), Color(0xFFF0B429), darkText, Color(0xFF16171A), Color(0xB81E2023)),
        AppThemeColors.BLUE to JePalette(Color(0xFF3B82F6), Color(0xFF7CB0FF), Color.White, Color(0xFF111318), Color(0xB81A1E26)),
        AppThemeColors.BOLD_BLUE to JePalette(Color(0xFF2563EB), Color(0xFF5B8DF5), Color.White, Color(0xFF0D1220), Color(0xB8141B2C)),
        AppThemeColors.GREEN to JePalette(Color(0xFF22C55E), Color(0xFF5EE08C), darkText, Color(0xFF101510), Color(0xB8182019)),
        AppThemeColors.PURPLE to JePalette(Color(0xFFA855F7), Color(0xFFC79BFF), Color.White, Color(0xFF15121C), Color(0xB81F1A28)),
        AppThemeColors.RED to JePalette(Color(0xFFEF4444), Color(0xFFFF8A80), Color.White, Color(0xFF1A1110), Color(0xB8261A19)),
        AppThemeColors.BROWN to JePalette(Color(0xFFC9803E), Color(0xFFE6A267), darkText, Color(0xFF19120D), Color(0xB8261C15)),
        // OLED: pure black backgrounds, keeping Jellyeye's orange
        AppThemeColors.OLED_BLACK to JePalette(Color(0xFFE5A00D), Color(0xFFF0B429), darkText, Color(0xFF000000), Color(0xB80E0E0E)),
    )

/** Jellyeye's colours. They follow the colour theme chosen in Settings (orange unless you pick another). */
object JE {
    private val palette: MutableState<JePalette> = mutableStateOf(JE_PALETTES.getValue(AppThemeColors.ORANGE))

    fun apply(theme: AppThemeColors) {
        val next = JE_PALETTES[theme] ?: JE_PALETTES.getValue(AppThemeColors.ORANGE)
        if (palette.value != next) palette.value = next
    }

    val bg: Color get() = palette.value.bg
    val tile: Color get() = palette.value.tile
    val accent: Color get() = palette.value.accent
    val accent2: Color get() = palette.value.accent2
    val onAccent: Color get() = palette.value.onAccent
    val line = Color(0x14FFFFFF)
    val text = Color(0xFFECEFF1)
    val soft = Color(0xFFC9CDD2)
    val dim = Color(0xFF9A9FA5)
    val critic = Color(0xFF7AA2F7)
    val ghost = Color(0x21FFFFFF)
}

/** Jellyeye's own settings (Settings → Jellyeye). */
object JeSettings {
    var bannerZoom by mutableStateOf(true)
    var slideMs by mutableIntStateOf(8_000)
    var showSeasonalPanel by mutableStateOf(true)
    var showBecauseYouWatched by mutableStateOf(true)

    fun apply(prefs: JellyeyePreferences) {
        bannerZoom = !prefs.noBannerZoom
        slideMs = (prefs.slideSeconds.takeIf { it > 0 } ?: 8) * 1_000
        showSeasonalPanel = !prefs.hideSeasonalPanel
        showBecauseYouWatched = !prefs.hideBecauseYouWatched
    }
}

/** The same space everywhere: banner height, page margins and the gap between panels. */
internal object JeLayout {
    val bannerHeight = 372.dp
    val pageMargin = 36.dp
    val pageTop = 16.dp
    val pageBottom = 48.dp
    val panelGap = 16.dp
}

internal val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun minutesOf(ticks: Long?): Long = (ticks ?: 0L) / 600_000_000L

internal fun finishAt(
    now: LocalDateTime,
    minutes: Long,
): String = now.plusMinutes(minutes).format(clockFormat)

internal fun lengthText(item: BaseItem): String? =
    if (item.type == BaseItemKind.SERIES) {
        item.data.childCount?.let { if (it == 1) "1 season" else "$it seasons" }
    } else {
        minutesOf(item.data.runTimeTicks).takeIf { it > 0 }?.let { m ->
            val h = m / 60
            val r = m % 60
            if (h > 0) (if (r > 0) "${h}h ${r}m" else "${h}h") else "${r}m"
        }
    }

/**
 * Everything selectable on the Jellyeye screens: when the remote lands on it, it gets the same
 * even orange outline (with a small dark gap) that the web version shows on hover.
 */
@Composable
fun JeFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    focusRequester: FocusRequester? = null,
    leftEdge: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val menu = if (leftEdge) LocalMenuFocus.current else null
    Box(
        modifier =
            modifier
                .border(3.dp, if (focused) JE.accent else Color.Transparent, RoundedCornerShape(corner + 5.dp))
                .padding(5.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (menu != null) Modifier.leftOpensMenu(menu) else Modifier)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

/**
 * A cover tile (poster or wide picture with its title underneath). The tile is what gets selected,
 * but the orange ring is drawn only around the picture, via [coverRing].
 */
@Composable
internal fun JeTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    leftEdge: Boolean = false,
    // holding OK, or the remote's Menu (☰) button
    onLongClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val menu = if (leftEdge) LocalMenuFocus.current else null
    Column(
        modifier =
            modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (menu != null) Modifier.leftOpensMenu(menu) else Modifier)
                .then(
                    if (onLongClick != null) {
                        Modifier
                            .onPreviewKeyEvent { e ->
                                val menu = e.key == Key.Menu
                                if (menu && e.type == KeyEventType.KeyUp) onLongClick()
                                menu
                            }.combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onClick,
                                onLongClick = onLongClick,
                            )
                    } else {
                        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    },
                ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content(focused)
    }
}

/** The orange ring with a small dark gap, around just the picture. Always takes the same space so nothing jumps. */
internal fun Modifier.coverRing(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(19.dp),
): Modifier =
    this
        .border(3.dp, if (focused) JE.accent else Color.Transparent, shape)
        .padding(5.dp)

@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    leftEdge: Boolean = false,
) {
    JeFocusable(onClick = onClick, corner = 30.dp, focusRequester = focusRequester, leftEdge = leftEdge) {
        Text(
            text = text,
            color = if (primary) JE.onAccent else Color.White,
            fontSize = 13.sp,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (primary) JE.accent else JE.ghost)
                    .padding(horizontal = 15.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun Chip(
    text: String,
    gold: Boolean = false,
) {
    Text(
        text = if (gold) text.uppercase() else text,
        color = if (gold) JE.accent2 else Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (gold) 0.8.sp else 0.sp,
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (gold) JE.accent.copy(alpha = 0.18f) else Color(0x1AFFFFFF))
                .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

@Composable
internal fun Meter(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
) {
    Column(modifier = Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFB8BCC2), fontSize = 12.sp)
            Text(value, color = JE.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x1FFFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color),
            )
        }
    }
}

@Composable
internal fun SectionTile(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(JE.tile)
                .border(1.dp, JE.line, RoundedCornerShape(20.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = JE.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        content()
    }
}

/* ------------------------------------------------------------------
   Long grids, a row at a time. A panel like SectionTile, but split into pieces (top, one per row,
   bottom) so the page only builds the rows near the screen instead of every poster at once.
   ------------------------------------------------------------------ */
internal enum class PanelPart { Top, Middle, Bottom }

/** Draws this piece of a rounded panel: the top piece gets the rounded top, the bottom piece the rounded bottom. */
internal fun Modifier.panelPart(part: PanelPart): Modifier =
    drawBehind {
        val r = 20.dp.toPx()
        val stroke = 1.dp.toPx()
        val extend = r * 2
        val top = if (part == PanelPart.Top) 0f else -extend
        val bottom = if (part == PanelPart.Bottom) size.height else size.height + extend
        clipRect {
            drawRoundRect(JE.tile, topLeft = Offset(0f, top), size = Size(size.width, bottom - top), cornerRadius = CornerRadius(r))
            drawRoundRect(
                JE.line,
                topLeft = Offset(stroke / 2, top + stroke / 2),
                size = Size(size.width - stroke, bottom - top - stroke),
                cornerRadius = CornerRadius(r),
                style = Stroke(stroke),
            )
        }
    }

/** The top of a split panel: its title (and anything else that sits above the rows). */
@Composable
internal fun PanelTop(
    title: String,
    content: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .panelPart(PanelPart.Top)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = JE.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        content()
    }
}

/** One row inside a split panel. */
@Composable
internal fun PanelRow(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .panelPart(PanelPart.Middle)
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) { content() }
}

/** The rounded bottom of a split panel, plus the usual gap before the next panel. */
@Composable
internal fun PanelBottom() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = JeLayout.panelGap)
            .height(20.dp)
            .panelPart(PanelPart.Bottom),
    )
}

/** A poster grid of any length, built a row at a time. */
internal fun LazyListScope.posterSection(
    key: String,
    title: String,
    items: List<BaseItem>,
    onClick: (BaseItem) -> Unit,
    columns: Int = 8,
    focusRequesters: Map<UUID, FocusRequester> = emptyMap(),
) {
    if (items.isEmpty()) return
    val rows = items.chunked(columns)
    item(key = "$key-top") { PanelTop(title) }
    itemsIndexed(rows, key = { i, _ -> "$key-row-$i" }) { _, row ->
        PanelRow { PosterRow(row, columns, onClick, focusRequesters) }
    }
    item(key = "$key-end") { PanelBottom() }
}

/** An ordinary panel or block in a list that has split panels in it (those lists have no automatic gaps). */
internal fun LazyListScope.gapItem(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key) { Box(Modifier.padding(bottom = JeLayout.panelGap)) { content() } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JellyeyeHome(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: JellyeyeHomeViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(Unit) {
        viewModel.refresh()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()

    // one clock for every "finishes at" time on the page
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(JE.accent.copy(alpha = 0.13f), JE.bg),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 1400f,
                    ),
                ).background(JE.bg.copy(alpha = 0f)),
    ) {
        if (!state.loaded) {
            Text(
                "Loading your library…",
                color = JE.dim,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        val playFocus = remember { FocusRequester() }
        val listState = rememberLazyListState()
        // focus: the title you came back to, else the banner's Play (see JellyeyeFocus.kt)
        val memory = viewModel.focusMemory
        val restoreTile = remember { FocusRequester() }
        val focusMap = rememberReturnFocusMap(memory, restoreTile)
        PageFocus(memory, listState, ready = state.spotlight.isNotEmpty(), restore = restoreTile, main = playFocus)
        // opening or playing a title notes where you were, for coming back
        val open = { item: BaseItem -> memory.leaving(item.id, listState); viewModel.open(item) }
        val play = { item: BaseItem -> memory.leaving(item.id, listState); viewModel.play(item) }
        val scope = rememberCoroutineScope()
        CompositionLocalProvider(LocalBringIntoViewSpec provides JellyeyeScrollSpec) {
        LazyColumn(
            state = listState,
            // equal margins left and right; the left one also keeps clear of the hidden menu strip
            contentPadding =
                PaddingValues(
                    start = JeLayout.pageMargin,
                    end = JeLayout.pageMargin,
                    top = JeLayout.pageTop,
                    bottom = JeLayout.pageBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(JeLayout.panelGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.spotlight.isNotEmpty()) {
                item(key = "spotlight") {
                    Spotlight(
                        // whenever anything in the spotlight is selected, show the whole card from the top
                        modifier =
                            Modifier.onFocusChanged {
                                if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) }
                            },
                        items = state.spotlight,
                        now = now,
                        playFocus = playFocus,
                        onPlay = play,
                        onInfo = open,
                        onWornOut = viewModel::refreshSpotlight,
                    )
                }
            }
            if (state.pickUp.isNotEmpty()) {
                item(key = "pickup") {
                    SectionTile("Pick up where you left off") {
                        WideGrid(state.pickUp, now, onClick = play, focusRequesters = focusMap)
                    }
                }
            }
            if (state.recommendedShows.isNotEmpty()) {
                item(key = "shows") {
                    SectionTile("Recommended TV shows") {
                        PosterGrid(state.recommendedShows, rows = 2, onClick = open, focusRequesters = focusMap)
                    }
                }
            }
            if (state.recommendedFilms.isNotEmpty()) {
                item(key = "films") {
                    SectionTile("Recommended films") {
                        PosterGrid(state.recommendedFilms, rows = 2, onClick = open, focusRequesters = focusMap)
                    }
                }
            }
            val season = state.season
            if (!JeSettings.showSeasonalPanel) {
                // switched off in Settings → Jellyeye
            } else if (season != null && state.seasonal.isNotEmpty()) {
                item(key = "season") {
                    SeasonPanel(season, state.seasonal, onClick = open, focusRequesters = focusMap)
                }
            } else if (state.gems.isNotEmpty()) {
                // no holiday right now: the same spot shows highly rated titles you haven't seen
                item(key = "gems") {
                    SectionTile("Hidden gems") {
                        Text("Highly rated in your library, and you haven't watched them yet", color = JE.dim, fontSize = 12.sp)
                        PosterGrid(state.gems, rows = 2, onClick = open, focusRequesters = focusMap)
                    }
                }
            }
        }
        }
    }
}

/* ------------------------------------------------------------------
   Spotlight: the big rotating card at the top
   ------------------------------------------------------------------ */

// how many times the home banner goes round before it fetches a new set (5 slides x 8s x 3 = about 2 minutes)
private const val SPOTLIGHT_ROUNDS = 3

@Composable
internal fun Spotlight(
    modifier: Modifier = Modifier,
    items: List<SpotlightItem>,
    now: LocalDateTime,
    playFocus: FocusRequester,
    onPlay: (BaseItem) -> Unit,
    onInfo: (BaseItem) -> Unit,
    // the gold label top-left; the home page counts the slides, a collection names itself
    badge: (index: Int, count: Int) -> String = { i, n -> "Spotlight ${i + 1} of $n" },
    // extra buttons after Play and More info (a collection adds Favourite, Watched, More options)
    extraButtons: @Composable () -> Unit = {},
    // called after the banner has gone all the way round a few times (the home page fetches new titles)
    onWornOut: (() -> Unit)? = null,
) {
    var index by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableIntStateOf(0) }
    if (index >= items.size) index = 0
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index, items) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(JeSettings.slideMs, easing = LinearEasing))
        if (items.size > 1) {
            val next = (index + 1) % items.size
            if (next == 0 && onWornOut != null && ++rounds >= SPOTLIGHT_ROUNDS) {
                rounds = 0
                onWornOut()
            }
            index = next
        }
    }
    val current = items[index]
    val imageUrls = LocalImageUrlService.current

    // fetch the next slide's pictures while this one is showing, so the crossfade never waits on the network
    val context = LocalContext.current
    LaunchedEffect(index, items) {
        val next = items.getOrNull((index + 1) % items.size)?.item ?: return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        listOfNotNull(
            imageUrls.getItemImageUrl(next, ImageType.BACKDROP, fillWidth = 1280),
            logoUrl(imageUrls, next),
        ).forEach { url -> loader.enqueue(ImageRequest.Builder(context).data(url).build()) }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(JeLayout.bannerHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF111214))
                .border(1.dp, JE.line, RoundedCornerShape(24.dp)),
    ) {
        Crossfade(targetState = current.item, label = "spotlightBg") { item ->
            // slow push-in: every slide starts at normal size and zooms in gently while it's on screen
            val zoom = remember(item.id) { Animatable(1f) }
            LaunchedEffect(item.id) {
                if (JeSettings.bannerZoom) zoom.animateTo(1.06f, tween(JeSettings.slideMs + 1_000, easing = LinearEasing))
            }
            val backdrop = remember(item.id) { imageUrls.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = 1280) }
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                        },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color(0xF0111214), Color(0x8C111214), Color(0x1A111214)))),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF5111214))),
        )

        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(badge(index, items.size), gold = true)
                if (current.why.isNotBlank()) Chip(current.why)
            }

            val item = current.item
            // everything between the labels and the tabs shares the space that's left, sitting at the bottom;
            // if a title has a big logo or lots of details, the summary gives way so the tabs are never pushed off
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            ) {
                TitleLogo(item, maxWidth = 300.dp, textSize = 40)
                val runMins = if (item.type == BaseItemKind.SERIES) 0L else minutesOf(item.data.runTimeTicks)
                val meta =
                    listOfNotNull(
                        item.data.productionYear?.toString(),
                        lengthText(item),
                        item.data.genres?.take(2)?.joinToString(" · ")?.takeIf { it.isNotBlank() },
                        if (runMins > 0) "ends at ${finishAt(now, runMins)}" else null,
                    )
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
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item.data.communityRating?.let {
                        Meter("Audience", "★ ${"%.1f".format(it)}", it / 10f, JE.accent)
                    }
                    item.data.criticRating?.let {
                        Meter("Critics", "${it.toInt()}%", it / 100f, JE.critic)
                    }
                }
                Text(
                    item.data.overview ?: "",
                    color = Color(0xFFDFE2E6),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 560.dp).weight(1f, fill = false),
                )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton("▶  Play", onClick = { onPlay(item) }, primary = true, focusRequester = playFocus, leftEdge = true)
                PillButton("ⓘ  More info", onClick = { onInfo(item) })
                extraButtons()
            }
            }

            Spacer(Modifier.height(10.dp))
            // the row of spotlight titles, with a bar showing when the next one comes up
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, s ->
                    JeFocusable(onClick = { index = i }, corner = 12.dp, modifier = Modifier.weight(1f), leftEdge = i == 0) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (i == index) JE.accent.copy(alpha = 0.14f) else Color(0x61000000))
                                    .border(1.dp, if (i == index) JE.accent.copy(alpha = 0.60f) else JE.line, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(s.item.name ?: "", color = JE.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                s.item.data.genres?.firstOrNull() ?: (if (s.item.type == BaseItemKind.SERIES) "Series" else "Film"),
                                color = JE.dim,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color(0x1FFFFFFF)),
                            ) {
                                val f = if (i == index) progress.value else if (i < index) 1f else 0f
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(f)
                                        .background(JE.accent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------
   Pick up where you left off: wide pictures with progress and finish time
   ------------------------------------------------------------------ */
@Composable
internal fun WideGrid(
    items: List<BaseItem>,
    now: LocalDateTime,
    onClick: (BaseItem) -> Unit,
    focusRequesters: Map<UUID, FocusRequester> = emptyMap(),
) {
    val imageUrls = LocalImageUrlService.current
    val columns = 5
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.take(columns * 2).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    JeTile(onClick = { onClick(item) }, modifier = Modifier.weight(1f), focusRequester = focusRequesters[item.id], leftEdge = item === row.first()) { focused ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                Modifier
                                    .coverRing(focused)
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF2B2E33)),
                            ) {
                                // try the episode's own picture first, then the show's artwork, so nothing is left blank
                                val candidates =
                                    remember(item.id) {
                                        val types =
                                            if (item.type == BaseItemKind.EPISODE) {
                                                listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP)
                                            } else {
                                                listOf(ImageType.THUMB, ImageType.BACKDROP, ImageType.PRIMARY)
                                            }
                                        types.mapNotNull { imageUrls.getItemImageUrl(item, it, fillWidth = 480) }.distinct()
                                    }
                                var attempt by remember(item.id) { mutableIntStateOf(0) }
                                candidates.getOrNull(attempt)?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        onError = { attempt++ },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                val pct = (item.data.userData?.playedPercentage ?: 0.0).toFloat()
                                if (pct > 0f) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .background(Color(0x99000000)),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(pct / 100f)
                                                .background(JE.accent),
                                        )
                                    }
                                }
                            }
                            val title = if (item.type == BaseItemKind.EPISODE) item.data.seriesName ?: "" else item.name ?: ""
                            Text(title, color = JE.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp, top = 2.dp))
                            val sub =
                                if (item.type == BaseItemKind.EPISODE) {
                                    "S${item.data.parentIndexNumber ?: "?"}:E${item.data.indexNumber ?: "?"} · ${item.name ?: ""}"
                                } else {
                                    item.data.productionYear?.toString() ?: ""
                                }
                            Text(sub, color = JE.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
                            val run = minutesOf(item.data.runTimeTicks)
                            val pos = minutesOf(item.data.userData?.playbackPositionTicks)
                            if (run > 0) {
                                val left = (run - pos).coerceAtLeast(1)
                                val text =
                                    if (pos > 0) "$left min left · finishes at ${finishAt(now, left)}" else "Up next · finishes at ${finishAt(now, run)}"
                                Text(text, color = JE.accent2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
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
   Poster grids: full rows that move top to bottom, rating bottom-right
   ------------------------------------------------------------------ */
@Composable
internal fun PosterGrid(
    items: List<BaseItem>,
    rows: Int,
    onClick: (BaseItem) -> Unit,
    columns: Int = 8,
    fullRowsOnly: Boolean = true,
    focusRequesters: Map<UUID, FocusRequester> = emptyMap(),
) {
    // only complete rows, like the web version (unless every title should be shown, e.g. an actor's films)
    val count =
        if (!fullRowsOnly) {
            items.size.coerceAtMost(columns * rows)
        } else {
            (items.size.coerceAtMost(columns * rows) / columns * columns).takeIf { it > 0 } ?: items.size.coerceAtMost(columns)
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.take(count).chunked(columns).forEach { row -> PosterRow(row, columns, onClick, focusRequesters) }
    }
}

/** One row of the poster grid (also used on its own by the long grids, which load a row at a time). */
@Composable
internal fun PosterRow(
    row: List<BaseItem>,
    columns: Int,
    onClick: (BaseItem) -> Unit,
    focusRequesters: Map<UUID, FocusRequester> = emptyMap(),
) {
    val imageUrls = LocalImageUrlService.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { item ->
            JeTile(onClick = { onClick(item) }, modifier = Modifier.weight(1f), focusRequester = focusRequesters[item.id], leftEdge = item === row.first()) { focused ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        Modifier
                            .coverRing(focused)
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2B2E33)),
                    ) {
                        val url = remember(item.id) { imageUrls.getItemImageUrl(item, ImageType.PRIMARY, fillWidth = 300) }
                        AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        item.data.communityRating?.let {
                            Text(
                                "★ ${"%.1f".format(it)}",
                                color = JE.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(5.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xB3000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (item.data.userData?.played == true) {
                            JeWatchedTick(Modifier.align(Alignment.TopEnd).padding(5.dp))
                        }
                    }
                    Text(
                        item.name ?: "",
                        color = if (focused) Color.White else JE.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 5.dp, top = 2.dp),
                    )
                    val sub = listOfNotNull(item.data.productionYear?.toString(), lengthText(item)).joinToString(" · ")
                    Text(sub, color = JE.dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/* ------------------------------------------------------------------
   The holiday panel: tinted in the season's colours, with a live countdown
   ------------------------------------------------------------------ */
@Composable
private fun SeasonPanel(
    season: JellyeyeSeason,
    items: List<BaseItem>,
    onClick: (BaseItem) -> Unit,
    focusRequesters: Map<UUID, FocusRequester> = emptyMap(),
) {
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick = System.currentTimeMillis()
        }
    }
    val countdown =
        remember(nowTick, season) {
            val now = LocalDateTime.now()
            val target = season.target.atStartOfDay()
            if (!now.isBefore(target)) {
                "Happy ${season.targetName}!"
            } else {
                val mins = ChronoUnit.MINUTES.between(now, target)
                val d = mins / 1440
                val h = (mins % 1440) / 60
                val m = mins % 60
                "$d ${if (d == 1L) "day" else "days"} ${"%02d".format(h)}h ${"%02d".format(m)}m to ${season.targetName}"
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(season.colorA.copy(alpha = 0.42f), season.colorB.copy(alpha = 0.42f))))
                .background(Color(0x661E2023))
                .border(1.dp, season.colorA.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(season.name.uppercase(), color = Color(0xCCFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(season.rowTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(season.rowWhy, color = JE.soft, fontSize = 12.sp)
            }
            Text(
                "❄  $countdown",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x59000000))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        PosterGrid(items, rows = 2, onClick = onClick, focusRequesters = focusRequesters)
    }
}

/* ------------------------------------------------------------------
   Title logos: asked for at about the size they're drawn (some originals are thousands of pixels and
   several MB, too slow or too big for a Fire Stick), with the title in text until the logo is ready.
   ------------------------------------------------------------------ */
internal fun logoUrl(
    imageUrls: com.github.damontecres.wholphin.services.ImageUrlService,
    item: BaseItem,
): String? {
    val tag = item.data.imageTags?.get(ImageType.LOGO) ?: return null
    return imageUrls.getItemImageUrl(itemId = item.id, imageType = ImageType.LOGO, maxWidth = 700, maxHeight = 260, tag = tag)
}

@Composable
internal fun TitleLogo(
    item: BaseItem,
    maxWidth: Dp,
    textSize: Int,
) {
    val imageUrls = LocalImageUrlService.current
    val url = remember(item.id) { logoUrl(imageUrls, item) }
    var ready by remember(item.id) { mutableStateOf(false) }
    Box(contentAlignment = Alignment.BottomStart) {
        if (!ready) {
            Text(
                item.name ?: "",
                color = Color.White,
                fontSize = textSize.sp,
                lineHeight = (textSize + 2).sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (url != null) {
            // loads behind the text; swaps in once it's actually there (a failed logo just leaves the text)
            AsyncImage(
                model = url,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onSuccess = { ready = true },
                onError = { ready = false },
                modifier =
                    Modifier
                        .heightIn(max = 80.dp)
                        .widthIn(max = maxWidth)
                        .graphicsLayer { alpha = if (ready) 1f else 0f },
            )
        }
    }
}


/** The one watched tick used on every cover: a round accent badge, always top-right. */
@Composable
fun JeWatchedTick(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(JE.accent)
                .border(1.dp, Color(0x66000000), CircleShape),
    ) {
        Text("✓", color = JE.onAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    }
}
