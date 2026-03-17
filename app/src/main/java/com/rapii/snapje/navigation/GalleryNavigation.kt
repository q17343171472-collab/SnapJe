// Gallery Navigation Graph for PhotoX app
package com.rapii.snapje.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.rapii.snapje.ui.PhotoXHomeScreen
import com.rapii.snapje.ui.CategoryDetailScreen
import com.rapii.snapje.ui.RecentlyDeletedScreen
import com.rapii.snapje.ui.SearchScreen
import com.rapii.snapje.ui.SettingsScreen
import com.rapii.snapje.ui.CategoryDetailViewModel
import com.rapii.snapje.data.TrashedPhoto
import com.rapii.snapje.util.L

object Routes {
    const val HOME = "home"
    const val CATEGORY_DETAIL = "category/{categoryId}"
    const val RECENTLY_DELETED = "recently_deleted"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun categoryDetail(categoryId: Long) = "category/$categoryId"
}

/**
 * Simple event manager for photo restore events.
 * This allows Trash screen to notify Category screens when a photo is restored.
 */
class PhotoRestoreEventManager {
    private var onPhotoRestored: ((TrashedPhoto) -> Unit)? = null
    
    fun setCallback(callback: (TrashedPhoto) -> Unit) {
        onPhotoRestored = callback
    }
    
    fun clearCallback() {
        onPhotoRestored = null
    }
    
    fun notifyRestored(photo: TrashedPhoto) {
        onPhotoRestored?.invoke(photo)
    }
}

// Global instance - in production, use proper DI or StateFlow
val photoRestoreEventManager = PhotoRestoreEventManager()

@Composable
fun GalleryNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // CRITICAL: Add smooth transition animations for navigation
        // Use lighter animations for faster transitions
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(150))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(150))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
        }
    ) {
        composable(Routes.HOME) {
            PhotoXHomeScreen(
                onCategoryClick = { category ->
                    navController.navigate(Routes.categoryDetail(category.id))
                },
                onNavigateToCamera = { },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH)
                },
                onNavigateToTrash = {
                    navController.navigate(Routes.RECENTLY_DELETED)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.CATEGORY_DETAIL,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: return@composable
            
            L.d("GalleryNavigation", "Navigating to CategoryDetail with categoryId: $categoryId")

            // CRITICAL: Set categoryId in SavedStateHandle BEFORE creating ViewModel
            // This ensures Hilt's SavedStateHandle has the value when ViewModel is created
            backStackEntry.savedStateHandle["categoryId"] = categoryId

            // Create ViewModel - it will read categoryId from SavedStateHandle
            val viewModel: CategoryDetailViewModel = hiltViewModel(
                viewModelStoreOwner = backStackEntry
            )

            CategoryDetailScreen(
                categoryId = categoryId,
                allCategories = emptyList(), // Categories loaded from ViewModel cache
                onBack = { navController.navigateUp() },
                onPhotoClick = { photo, photos ->
                    // Photo gallery is shown internally in CategoryDetailScreen
                    // No navigation needed
                },
                onPhotoRestored = { photo ->
                    // Notify the category ViewModel that a photo was restored
                    // The ViewModel will check if the restored photo belongs to this category
                    photoRestoreEventManager.notifyRestored(photo)
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.navigateUp() },
                onPhotoClick = { photo, photos ->
                    // Navigate to category detail screen to show photo in fullscreen
                    // Find the category this photo belongs to
                    val bucketId = photo.bucketId
                    if (bucketId != null) {
                        navController.navigate(Routes.categoryDetail(bucketId))
                    } else {
                        // If no bucket ID, navigate back and show a message
                        navController.navigateUp()
                    }
                }
            )
        }

        // Photo gallery is handled internally in CategoryDetailScreen
        // No separate route needed

        composable(Routes.RECENTLY_DELETED) {
            RecentlyDeletedScreen(
                onBack = { navController.navigateUp() },
                onPhotoRestored = { photo ->
                    // Notify the category screen that a photo was restored
                    photoRestoreEventManager.notifyRestored(photo)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.navigateUp() }
            )
        }
    }
}
