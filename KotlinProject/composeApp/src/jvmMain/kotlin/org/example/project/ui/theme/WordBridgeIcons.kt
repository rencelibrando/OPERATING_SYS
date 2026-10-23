package org.example.project.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector

object WordBridgeIcons {
    val Home: ImageVector = Icons.Outlined.Home
    val Lessons: ImageVector = Icons.AutoMirrored.Outlined.MenuBook
    val Vocabulary: ImageVector = Icons.Outlined.Translate
    val Speaking: ImageVector = Icons.Outlined.Mic
    val AIChat: ImageVector = Icons.Outlined.ChatBubbleOutline
    val Progress: ImageVector = Icons.AutoMirrored.Outlined.TrendingUp
    val Settings: ImageVector = Icons.Outlined.Settings
    val Streak: ImageVector = Icons.Outlined.LocalFireDepartment
    val Trophy: ImageVector = Icons.Outlined.EmojiEvents
    val Check: ImageVector = Icons.Outlined.CheckCircleOutline
    val Book: ImageVector = Icons.AutoMirrored.Outlined.MenuBook
    val Mic: ImageVector = Icons.Outlined.Mic
    val Chat: ImageVector = Icons.Outlined.ChatBubbleOutline
    val Explore: ImageVector = Icons.Outlined.Explore
    val Play: ImageVector = Icons.Outlined.PlayArrow
    val LightMode: ImageVector = Icons.Outlined.LightMode
    val DarkMode: ImageVector = Icons.Outlined.DarkMode
    val Logout: ImageVector = Icons.AutoMirrored.Outlined.Logout
    val ChevronRight: ImageVector = Icons.Outlined.ChevronRight
    val ChevronLeft: ImageVector = Icons.Outlined.ChevronLeft
    val Lock: ImageVector = Icons.Outlined.Lock
    val Notifications: ImageVector = Icons.Outlined.Notifications
    val School: ImageVector = Icons.Outlined.School
    val Person: ImageVector = Icons.Outlined.Person
    val VolumeUp: ImageVector = Icons.AutoMirrored.Outlined.VolumeUp
    val Search: ImageVector = Icons.Outlined.Search
    val Star: ImageVector = Icons.Outlined.Star
    val Bolt: ImageVector = Icons.Outlined.Bolt
    val Sparkle: ImageVector = Icons.Outlined.AutoAwesome
    val Brain: ImageVector = Icons.Outlined.Psychology
    val Time: ImageVector = Icons.Outlined.Schedule
    val Timer: ImageVector = Icons.Outlined.Timer
    val Delete: ImageVector = Icons.Outlined.Delete
    val Edit: ImageVector = Icons.Outlined.Edit
    val Headphones: ImageVector = Icons.Outlined.Headphones
    val Language: ImageVector = Icons.Outlined.Language
    val Send: ImageVector = Icons.AutoMirrored.Outlined.Send
    val Robot: ImageVector = Icons.Outlined.SmartToy
    val Camera: ImageVector = Icons.Outlined.CameraAlt
    val Palette: ImageVector = Icons.Outlined.Palette

    fun getByName(name: String): ImageVector = when (name.lowercase().trim()) {
        "home" -> Home
        "lessons", "lesson", "book", "books" -> Lessons
        "vocabulary", "vocab", "words", "translate" -> Vocabulary
        "speaking", "speech", "mic", "microphone" -> Speaking
        "ai_chat", "aichat", "chat", "message", "conversation" -> AIChat
        "progress", "stats", "chart", "trending" -> Progress
        "settings", "gear" -> Settings
        "streak", "fire" -> Streak
        "trophy", "award", "xp" -> Trophy
        "check", "completed", "accuracy", "done" -> Check
        "play" -> Play
        "explore", "rocket" -> Explore
        "lock", "locked" -> Lock
        "notifications", "bell" -> Notifications
        "learning", "school", "education", "beginner" -> School
        "account", "profile", "person", "user" -> Person
        "volume", "audio", "sound" -> VolumeUp
        "search" -> Search
        "star", "advanced" -> Star
        "bolt", "lightning", "speed", "fast" -> Bolt
        "sparkle", "ai", "magic" -> Sparkle
        "brain", "smart" -> Brain
        else -> Explore
    }
}
