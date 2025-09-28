package org.example.project.domain.model

data class NavigationItem(
    val id: String,
    val title: String,
    val icon: String,
    val route: String,
    val isSelected: Boolean = false
) {
    companion object {
        fun getDefaultNavigationItems(): List<NavigationItem> = listOf(
            NavigationItem(
                id = "home",
                title = "Home",
                icon = "home",
                route = "/home",
                isSelected = true
            ),
            NavigationItem(
                id = "lessons",
                title = "Lessons",
                icon = "lessons",
                route = "/lessons"
            ),
            NavigationItem(
                id = "vocabulary",
                title = "Vocabulary Bank",
                icon = "vocabulary",
                route = "/vocabulary"
            ),
            NavigationItem(
                id = "speaking",
                title = "Speaking Practice",
                icon = "speaking",
                route = "/speaking"
            ),
            NavigationItem(
                id = "ai_chat",
                title = "AI Chat Tutor",
                icon = "ai_chat",
                route = "/ai-chat"
            ),
            NavigationItem(
                id = "progress",
                title = "Progress Tracker",
                icon = "progress",
                route = "/progress"
            ),
            NavigationItem(
                id = "settings",
                title = "Settings",
                icon = "settings",
                route = "/settings"
            )
        )
    }
}
