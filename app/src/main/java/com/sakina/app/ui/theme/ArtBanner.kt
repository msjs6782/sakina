package com.sakina.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen's painted header (Higgsfield art in the style of the sky paintings, docs/ART.md): the picture with a veil
 * that keeps the white title readable, and optional actions at the top. Shown when the paintings are turned on.
 */
@Composable
fun ArtBanner(
    res: Int,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    tag: String = "art_banner",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp)).background(Color(0xFF14213D)).testTag(tag)) {
        Image(painterResource(res), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 0.45f to Color.Black.copy(alpha = 0.18f), 1f to Color.Black.copy(alpha = 0.62f)),
            ),
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), content = actions)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = Color.White)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
        }
    }
}
