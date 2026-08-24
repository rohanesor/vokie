package com.vokie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.data.demoMessage
import com.vokie.domain.model.*
import com.vokie.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VokieTheme { VokieApp() } }
    }
}

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), COMMUNICATE("Communicate", Icons.Default.Mic), MAP("Map", Icons.Default.Map), ALERTS("Alerts", Icons.Default.Warning), MORE("More", Icons.Default.Menu)
}

@Composable
fun VokieApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showSos by remember { mutableStateOf(false) }
    Scaffold(containerColor = VokieTheme.colors.background, bottomBar = {
        NavigationBar(containerColor = VokieTheme.colors.surface, tonalElevation = 0.dp) {
            Screen.entries.forEach { item ->
                NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = { Icon(item.icon, item.title) }, label = { Text(item.title) })
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(onSpeak = { screen = Screen.COMMUNICATE }, onSos = { showSos = true })
                Screen.COMMUNICATE -> CommunicateScreen()
                Screen.MAP -> MapScreen()
                Screen.ALERTS -> AlertsScreen()
                Screen.MORE -> MoreScreen()
            }
        }
    }
    if (showSos) SosSheet(onDismiss = { showSos = false })
}

@Composable
fun AppHeader(title: String, eyebrow: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("VOKIE", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary, letterSpacing = 2.sp)
        if (eyebrow != null) Text(eyebrow, style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted)
        Text(title, style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary)
    }
}

@Composable
fun StatusStrip() {
    Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("COMMUNICATION STATUS", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusDot("Bluetooth", "READY", VokieTheme.colors.success)
                StatusDot("Wi-Fi Direct", "READY", VokieTheme.colors.success)
                StatusDot("Internet", "OFFLINE", VokieTheme.colors.textSecondary)
            }
        }
    }
}

@Composable fun StatusDot(name: String, state: String, color: androidx.compose.ui.graphics.Color) {
    Column { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)); Text(name, style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary) }; Text(state, style = VokieTheme.typography.labelSmall, color = color, modifier = Modifier.padding(start = 14.dp, top = 3.dp)) }
}

@Composable
fun HomeScreen(onSpeak: () -> Unit, onSos: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), modifier = Modifier.fillMaxSize()) {
        item { AppHeader("Offline Emergency Communication", "Voice when networks fail.") }
        item { Column(Modifier.padding(horizontal = 20.dp)) { StatusStrip(); Spacer(Modifier.height(16.dp)) } }
        item { Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("CURRENT STATUS", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary); Text("You are safe", style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.success); Spacer(Modifier.height(8.dp)); Text("Vokie is operating in offline mode. Nearby communication is available.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } } }
        item { Spacer(Modifier.height(22.dp)); PushToTalkButton(onClick = onSpeak); Text("Speak in your language. Vokie converts your speech to text and transmits it locally.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 36.dp, vertical = 14.dp)) }
        item { Text("Language  •  Tamil · தமிழ்", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item { Spacer(Modifier.height(18.dp)); SosButton(onClick = onSos) }
        item { Text("DEMO MODE  •  Simulated local communication", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
fun PushToTalkButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, shape = CircleShape, modifier = Modifier.size(VokieDimens.pushToTalkSize).wrapContentWidth().fillMaxWidth().semantics { contentDescription = "Hold to speak. Activates local speech recognition." }, colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.surface, contentColor = VokieTheme.colors.textPrimary), border = androidx.compose.foundation.BorderStroke(2.dp, VokieTheme.colors.border), contentPadding = PaddingValues(12.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Mic, null, Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text("HOLD TO SPEAK", style = VokieTheme.typography.label) } }
}

@Composable
fun SosButton(onClick: () -> Unit) { Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.alert), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(VokieDimens.sosButtonHeight).padding(horizontal = 20.dp).semantics { contentDescription = "SOS emergency broadcast. Requires hold confirmation." }) { Icon(Icons.Default.Warning, null); Spacer(Modifier.width(10.dp)); Column { Text("SOS", style = VokieTheme.typography.label); Text("Emergency Broadcast", style = VokieTheme.typography.caption) } } }

@Composable
fun SosSheet(onDismiss: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VokieTheme.colors.surface) {
        Column(Modifier.padding(24.dp).navigationBarsPadding()) { Text("SEND EMERGENCY SOS", style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary); Spacer(Modifier.height(12.dp)); Text("Your phone will broadcast your emergency message and location to nearby Vokie devices.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary); Spacer(Modifier.height(18.dp)); Text("LOCATION    AVAILABLE", style = VokieTheme.typography.label, color = VokieTheme.colors.success); Text("TRANSPORT   Bluetooth  ·  Wi-Fi Direct", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 10.dp)); Spacer(Modifier.height(18.dp)); OutlinedTextField(value = "I need emergency assistance.", onValueChange = {}, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 2); Spacer(Modifier.height(20.dp)); Button(onClick = { confirmed = true }, colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.alert), modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(if (confirmed) "BROADCASTING…" else "HOLD TO BROADCAST", style = VokieTheme.typography.label) }; if (confirmed) { Spacer(Modifier.height(14.dp)); Text("Searching for nearby Vokie devices…\nBroadcasting through Bluetooth…", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) }; Spacer(Modifier.height(16.dp)); TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") } }
    }
}

@Composable
fun CommunicateScreen() {
    var stage by remember { mutableStateOf("READY") }
    var message by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize()) {
        AppHeader("Communicate", "Local speech → text → nearby device → voice")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("WALKIE-TALKIE MODE", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary); Switch(checked = true, onCheckedChange = {}) }
            Spacer(Modifier.height(12.dp)); StatusStrip(); Spacer(Modifier.height(24.dp))
            Text(stage, style = VokieTheme.typography.labelSmall, color = if (stage == "FAILED") VokieTheme.colors.alert else VokieTheme.colors.success); Spacer(Modifier.height(14.dp))
            PushToTalkButton { scope.launch { haptic.performHapticFeedback(HapticFeedbackType.LongPress); stage = "LISTENING…"; kotlinx.coroutines.delay(500); stage = "TRANSCRIBING…"; kotlinx.coroutines.delay(500); message = demoMessage("அம்மா, நான் பாதுகாப்பாக இருக்கிறேன்."); stage = "TRANSMITTING…"; kotlinx.coroutines.delay(500); stage = "SENT TO NEARBY DEVICE"; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
            Spacer(Modifier.height(22.dp)); message?.let { MessageBubble(it) }; Spacer(Modifier.height(20.dp)); Text("The demo pipeline uses simulated on-device STT and Bluetooth. No internet required.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary)
        }
    }
}

@Composable
fun MessageBubble(message: Message) { Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("YOU", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary); Text("“${message.text}”", style = VokieTheme.typography.bodyLarge, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 10.dp)); Text("Tamil  •  Bluetooth  •  ${message.hopCount} hop", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = VokieTheme.colors.success); Spacer(Modifier.width(6.dp)); Text("Received by peer", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.success) } } } }

@Composable
fun MapScreen() { Column(Modifier.fillMaxSize()) { AppHeader("Map", "Offline-first safety map"); Column(Modifier.padding(horizontal = 20.dp)) { Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.fillMaxWidth().height(310.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Map, null, Modifier.size(48.dp), tint = VokieTheme.colors.textSecondary); Text("MAP AVAILABLE OFFLINE", style = VokieTheme.typography.label, color = VokieTheme.colors.success, modifier = Modifier.padding(top = 12.dp)); Text("Tamil Nadu region  •  Updated locally", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } } }; Spacer(Modifier.height(18.dp)); Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.surface)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download Region") }; Spacer(Modifier.height(20.dp)); Text("MAP LEGEND", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary); Text("●  Current location    +  Shelters    ✚  Hospitals    !  Hazard zones", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp)) } } }

@Composable
fun AlertsScreen() { val alerts = listOf("Flood Warning" to "Water level rising near your area.", "Shelter Open" to "Community shelter open at Government School.", "Road Closure" to "Avoid the east bridge. Use marked safe routes."); LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) { item { AppHeader("Alerts", "Chronological emergency feed") }; items(alerts) { (title, body) -> Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("CRITICAL", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.alert); Text(title, style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.textPrimary); Text(body, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp)); Text("12 min ago  •  Local authority  •  ACTIVE", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted) } } } } }

@Composable
fun MoreScreen() { LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { item { AppHeader("More", "Tools for staying safe offline") }; item { MoreSection("CONTACTS / CHECK-IN", listOf("Emergency Contacts", "MARK ME SAFE", "Broadcast Safe Status")) }; item { MoreSection("OFFLINE RESOURCES", listOf("First Aid", "Flood", "Fire", "Earthquake", "Evacuation", "Emergency Communication")) }; item { MoreSection("SETTINGS", listOf("Language", "Communication Mode", "Bluetooth", "Wi-Fi Direct", "Ultrasonic", "Offline Map Storage", "Accessibility", "Dark / Light Mode", "Haptic Feedback", "Voice Settings", "About Vokie")) }; item { Text("Offline Mode\nAll communication processing occurs on this device.\n\nVoice when networks fail.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(20.dp)) } } }

@Composable
fun MoreSection(title: String, items: List<String>) { Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(title, style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp)); items.forEach { item -> Surface(color = VokieTheme.colors.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(item, style = VokieTheme.typography.body, color = VokieTheme.colors.textPrimary); Icon(Icons.Default.ChevronRight, null, tint = VokieTheme.colors.textMuted) } } } } }
