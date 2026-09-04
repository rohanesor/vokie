package com.vokie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.screens.chat.ChatScreen
import com.vokie.ui.screens.emergency.EmergencySheet
import com.vokie.ui.screens.locate.LocateScreen
import com.vokie.ui.screens.more.MoreScreen
import com.vokie.ui.screens.onboarding.OnboardingScreen
import com.vokie.ui.screens.rescue.RescuePeerListScreen
import com.vokie.ui.theme.VokieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            VokieTheme {
                VokieApp()
            }
        }
    }
}

enum class Screen(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    RESCUE("Rescue", Icons.Default.People),
    LOCATE("Locate", Icons.Default.NearMe),
    MORE("More", Icons.Default.Settings),
}

@Composable
fun VokieApp(vm: CommunicationViewModel = viewModel()) {
    val preferredLanguage by vm.preferredLanguage.collectAsState()
    var currentScreen by rememberSaveable { mutableStateOf(Screen.CHAT) }
    var showEmergencySheet by rememberSaveable { mutableStateOf(false) }
    var showOnboardingModal by rememberSaveable { mutableStateOf(false) }

    if (preferredLanguage == null || showOnboardingModal) {
        OnboardingScreen(
            initialProfile = preferredLanguage,
            onCompleted = { profile ->
                vm.selectPreferredLanguage(profile)
                showOnboardingModal = false
            },
        )
        return
    }

    Scaffold(
        containerColor = VokieTheme.colors.background,
        bottomBar = {
            NavigationBar(
                containerColor = VokieTheme.colors.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Screen.entries.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                style = VokieTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = VokieTheme.colors.textPrimary,
                            indicatorColor = VokieTheme.colors.accent.copy(alpha = 0.25f),
                            unselectedIconColor = VokieTheme.colors.textSecondary,
                            unselectedTextColor = VokieTheme.colors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (currentScreen) {
                Screen.CHAT -> ChatScreen(
                    vm = vm,
                    onOpenEmergency = { showEmergencySheet = true },
                    onOpenLanguages = { showOnboardingModal = true },
                )
                Screen.RESCUE -> RescuePeerListScreen(
                    vm = vm,
                    onPeerSelected = { peerId ->
                        vm.selectPeer(peerId)
                        currentScreen = Screen.CHAT
                    },
                )
                Screen.LOCATE -> LocateScreen(
                    vm = vm,
                    onOpenEmergency = { showEmergencySheet = true },
                )
                Screen.MORE -> MoreScreen(
                    vm = vm,
                    onOpenLanguages = { showOnboardingModal = true },
                )
            }
        }
    }

    if (showEmergencySheet) {
        EmergencySheet(
            vm = vm,
            onSpeak = {
                currentScreen = Screen.CHAT
                vm.startVoice()
            },
            onLocate = {
                currentScreen = Screen.LOCATE
            },
            onMessages = {
                currentScreen = Screen.CHAT
            },
            onDismiss = {
                showEmergencySheet = false
            },
        )
    }
}
