package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class Platform(
    val id: String,
    val displayName: String,
    val badgeColor: Color,
    val textColor: Color
) {
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        badgeColor = Color(0xFFFF0000),
        textColor = Color.White
    ),
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        badgeColor = Color(0xFF00F2FE),
        textColor = Color(0xFF0F172A)
    ),
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        badgeColor = Color(0xFFE1306C),
        textColor = Color.White
    ),
    TWITTER(
        id = "twitter",
        displayName = "Twitter/X",
        badgeColor = Color(0xFF1DA1F2),
        textColor = Color.White
    ),
    OTHER(
        id = "other",
        displayName = "Web Média",
        badgeColor = Color(0xFF64748B),
        textColor = Color.White
    );

    companion object {
        fun detect(url: String): Platform {
            val lower = url.trim().lowercase()
            return when {
                lower.contains("youtube.com") || lower.contains("youtu.be") -> YOUTUBE
                lower.contains("tiktok.com") -> TIKTOK
                lower.contains("instagram.com") -> INSTAGRAM
                lower.contains("twitter.com") || lower.contains("x.com") -> TWITTER
                else -> OTHER
            }
        }

        fun fromId(id: String?): Platform {
            if (id == null) return YOUTUBE
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: YOUTUBE
        }

        fun fromString(value: String?): Platform = fromId(value)
    }
}
