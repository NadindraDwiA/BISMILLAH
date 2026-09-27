package com.example.bismillah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bismillah.core.navigation.FeatureRegistry
import com.example.bismillah.core.navigation.Routes
import com.example.bismillah.feature.screentranslator.TranslatorSetupScreen
import com.example.bismillah.ui.home.HomeScreen
import com.example.bismillah.ui.model.ModelManagerScreen
import com.example.bismillah.ui.settings.SettingsScreen
import com.example.bismillah.ui.setup.SetupScreen
import com.example.bismillah.ui.splash.SplashScreen
import com.example.bismillah.ui.theme.BISMILLAHTheme
import com.example.bismillah.util.ModelDownloader
import com.example.bismillah.util.PermissionUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Deep-link dari radial menu (long-press > Setup): langsung ke Pengaturan.
        val deepRoute = intent?.getStringExtra("route")?.takeIf { it == Routes.SETTINGS }
        setContent {
            BISMILLAHTheme {
                BismillahAppMainScreen(startRoute = deepRoute)
            }
        }
    }
}

private data class NavTabItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BismillahAppMainScreen(startRoute: String? = null) {
    val navController = rememberNavController()
    val ctx = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.SPLASH

    val bottomTabs = listOf(
        NavTabItem(
            route = Routes.HOME,
            title = "Beranda",
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home
        ),
        NavTabItem(
            route = Routes.TRANSLATOR_SETUP,
            title = "Translator",
            selectedIcon = Icons.Rounded.Translate,
            unselectedIcon = Icons.Outlined.Translate
        )
    )

    val isBottomBarVisible = bottomTabs.any { it.route == currentRoute }
    val isTopBarVisible = currentRoute != Routes.SPLASH && currentRoute != Routes.SETUP

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (isTopBarVisible) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                                Routes.HOME -> "BISMILLAH AI"
                                Routes.TRANSLATOR_SETUP -> "Screen Translator"
                                Routes.MODEL_MANAGER -> "Kelola Model AI"
                                Routes.SETTINGS -> "Pengaturan & Izin"
                                else -> "BISMILLAH"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.toDp()
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute ?: Routes.SPLASH,
            modifier = Modifier.padding(if (!isTopBarVisible && !isBottomBarVisible) androidx.compose.foundation.layout.PaddingValues(androidx.compose.ui.unit.Dp(0f)) else innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { this.inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.SETUP) {
                SetupScreen(onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { this.inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) {
                val features = FeatureRegistry.items(
                    hasOverlayPermission = PermissionUtils.canDrawOverlays(ctx),
                    translatorReady = ModelDownloader.hasPaddle(ctx) &&
                        com.example.bismillah.data.preference.ApiKeyStore.effectiveKey(ctx).isNotBlank()
                )
                HomeScreen(features) { feature ->
                    navController.navigate(feature.route)
                }
            }
            composable(Routes.TRANSLATOR_SETUP) {
                TranslatorSetupScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.MODEL_MANAGER) { ModelManagerScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenModels = { navController.navigate(Routes.MODEL_MANAGER) })
            }
        }
    }
}

private fun Int.toDp(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(this.toFloat())
