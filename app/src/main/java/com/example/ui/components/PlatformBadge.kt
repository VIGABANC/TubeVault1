package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Platform

@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val backgroundColor = when (platform) {
        Platform.YOUTUBE -> Color(0xFFE50914)
        Platform.TIKTOK -> Color(0xFF00E5FF)
        Platform.INSTAGRAM -> Color(0xFFE1306C)
        Platform.TWITTER -> Color(0xFF1DA1F2)
        Platform.OTHER -> Color(0xFF64748B)
    }

    val contentColor = when (platform) {
        Platform.TIKTOK -> Color(0xFF0B0F19)
        else -> Color.White
    }

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor.copy(alpha = 0.92f))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini dot indicator or icon
            Box(
                modifier = Modifier
                    .size(if (compact) 6.dp else 8.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
            Text(
                text = platform.displayName,
                color = contentColor,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}
