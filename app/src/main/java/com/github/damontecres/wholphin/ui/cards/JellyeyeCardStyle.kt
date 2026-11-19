package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.Glow

/**
 * Jellyeye look for every Wholphin card: rounded corners, no zoom, no glow and no built-in border.
 * The orange ring is drawn around the picture with [com.github.damontecres.wholphin.ui.jellyeye.coverRing],
 * the same as on the Jellyeye home page.
 */
internal val JeCardCorner = RoundedCornerShape(14.dp)
internal val JeRingCorner = RoundedCornerShape(19.dp)

@Composable
internal fun jeCardShape(shape: Shape = JeCardCorner): CardShape = CardDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape)

@Composable
internal fun jeCardScale(): CardScale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.97f)

@Composable
internal fun jeCardBorder(): CardBorder = CardDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None)

@Composable
internal fun jeCardGlow(): CardGlow = CardDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None)
