package com.rustypastechat.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

/** Tablet/foldable-width companion to [AnimatedBottomNavBar] — same item model, stock M3 rail. */
@Composable
fun NavigationRailBar(
    items: List<AnimatedNavItem>,
    currentRoute: NavKey,
    onNavigate: (NavKey) -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            val isSelected = item.route == currentRoute
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
