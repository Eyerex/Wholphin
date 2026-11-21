package com.github.damontecres.wholphin.ui.jellyeye

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.focus.FocusDirection
import kotlinx.coroutines.delay
import java.util.UUID

/*
 * How focus works on every Jellyeye page:
 *  1. Each page remembers the title you opened from it (and where the page was scrolled), in its view model.
 *  2. Coming back, the page scrolls back to where it was and selects that title again; if that title isn't
 *     there any more, it selects the page's main button (Play, the first title...). It keeps trying for a
 *     moment while the page draws, so focus is never left with nothing.
 *  3. Your place is only kept for coming back from a title; leaving any other way (another tab, the menu)
 *     forgets it, so the page starts from the top next time.
 *  4. Safety net: the side menu only opens when you press Left or Back to open it. If focus lands on the menu
 *     by accident (a page appearing with nothing selected), it's handed back to the page instead.
 */

/** Asks for focus and says whether it actually got it (false if the title isn't drawn yet, or isn't there). */
internal fun FocusRequester.focusNow(): Boolean =
    try {
        requestFocus(FocusDirection.Enter)
    } catch (ex: IllegalStateException) {
        false
    }

/** Where you were on a page when you opened a title. Lives in the page's view model, so it survives the trip. */
class JeReturn {
    /** The title you opened (or played) from this page. */
    var id: UUID? = null
        private set
    private var index = 0
    private var offset = 0

    /** True between opening a title and coming back; any other way of leaving forgets your place. */
    internal var away = false

    /** Call when a title is opened or played from this page. */
    fun leaving(
        itemId: UUID,
        list: LazyListState?,
    ) {
        id = itemId
        index = list?.firstVisibleItemIndex ?: 0
        offset = list?.firstVisibleItemScrollOffset ?: 0
        away = true
    }

    fun forget() {
        id = null
        index = 0
        offset = 0
    }

    internal suspend fun scrollBack(list: LazyListState) {
        if (list.firstVisibleItemIndex != index || list.firstVisibleItemScrollOffset != offset) {
            list.scrollToItem(index, offset)
        }
    }
}

/** For pages without a view model of their own: keeps a [JeReturn] alive while you're away in a title. */
class JeReturnHolder : ViewModel() {
    val memory = JeReturn()
}

@Composable
internal fun rememberJeReturn(key: String): JeReturn = viewModel<JeReturnHolder>(key = key).memory

/** The requesters a grid needs so the title you came back to can be selected: give it to PosterGrid/WideGrid. */
@Composable
internal fun rememberReturnFocusMap(
    memory: JeReturn,
    restore: FocusRequester,
    extra: Map<UUID, FocusRequester> = emptyMap(),
): Map<UUID, FocusRequester> {
    val id = memory.id
    return remember(id, extra) { if (id != null) extra + (id to restore) else extra }
}

/**
 * Puts focus somewhere sensible when a page appears: the title you came back to (scrolling to it first), else
 * the page's main item. Also registers the page with the menu safety net, and forgets your place when you
 * leave other than into a title.
 */
@Composable
internal fun PageFocus(
    memory: JeReturn,
    list: LazyListState?,
    ready: Boolean,
    restore: FocusRequester,
    main: FocusRequester,
) {
    DisposableEffect(memory) {
        memory.away = false
        JePageFocus.register(main)
        onDispose {
            JePageFocus.unregister(main)
            if (!memory.away) memory.forget()
        }
    }
    val backTo = memory.id
    LaunchedEffect(ready, backTo) {
        if (!ready) return@LaunchedEffect
        if (backTo != null) {
            list?.let { memory.scrollBack(it) }
            repeat(30) {
                if (restore.focusNow()) return@LaunchedEffect
                delay(40)
            }
        }
        delay(60)
        repeat(40) {
            if (main.focusNow()) return@LaunchedEffect
            delay(50)
        }
    }
}

/** The page on screen's main focus target, for handing back focus that strayed onto the menu. */
object JePageFocus {
    private var current: FocusRequester? = null

    fun register(target: FocusRequester) {
        current = target
    }

    fun unregister(target: FocusRequester) {
        if (current === target) current = null
    }

    /** Is a Jellyeye page on screen to hand focus back to? */
    val hasPage: Boolean get() = current != null

    /**
     * Hand stray focus back to the page, trying for a moment while it finishes drawing. False if there's no
     * Jellyeye page, or it never became ready (the menu then opens as normal, so focus is never stuck).
     */
    suspend fun takeBack(): Boolean {
        repeat(25) {
            val page = current ?: return false
            if (page.focusNow()) return true
            delay(40)
        }
        return false
    }
}

/** Remembers when Left or Back was last pressed, so the menu can tell being opened on purpose from stray focus. */
object JeMenuIntent {
    private var lastPress = 0L

    fun pressed() {
        lastPress = SystemClock.uptimeMillis()
    }

    /** Was Left or Back pressed just now? */
    fun recent(): Boolean = SystemClock.uptimeMillis() - lastPress < 600
}
