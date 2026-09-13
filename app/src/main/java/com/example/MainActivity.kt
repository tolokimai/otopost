package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.contentplan.ContentPlanScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.persona.PersonaScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.studio.StudioScreen
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import com.example.viewmodel.MainTab
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: AutoPostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()
            val isDarkTheme = settings.themeMode == "DARK"

            AutoPostStudioTheme(darkTheme = isDarkTheme) {
                val currentTab by viewModel.currentTab.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.snackbarMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .testTag("bottom_nav_bar")
                        ) {
                            val tabs = listOf(
                                MainTab.Dashboard to (Icons.Default.Dashboard to "Home"),
                                MainTab.Persona to (Icons.Default.Person to "Persona"),
                                MainTab.ContentPlan to (Icons.Default.CalendarToday to "Plan"),
                                MainTab.Studio to (Icons.Default.MovieFilter to "Studio"),
                                MainTab.Settings to (Icons.Default.Settings to "Settings")
                            )

                            tabs.forEach { (tab, iconAndLabel) ->
                                val (icon, label) = iconAndLabel
                                val isSelected = currentTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectTab(tab)
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = UtilityBlue700,
                                        selectedTextColor = UtilityBlue700,
                                        indicatorColor = UtilityBlue100,
                                        unselectedIconColor = Slate400,
                                        unselectedTextColor = Slate400
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "TabTransition"
                        ) { targetTab ->
                            when (targetTab) {
                                MainTab.Dashboard -> DashboardScreen(viewModel)
                                MainTab.Persona -> PersonaScreen(viewModel)
                                MainTab.ContentPlan -> ContentPlanScreen(viewModel)
                                MainTab.Studio -> StudioScreen(viewModel)
                                MainTab.Settings -> SettingsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
