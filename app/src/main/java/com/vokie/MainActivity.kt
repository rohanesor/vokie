package com.vokie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.communication.BluetoothPermission
import com.vokie.domain.model.*
import com.vokie.ui.theme.*
import com.vokie.ui.communication.CommunicationViewModel

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
fun StatusStrip(vm: CommunicationViewModel = viewModel()) {
    val bluetooth by vm.connectionState.collectAsState()
    val bluetoothColor = if (bluetooth == TransportConnectionState.CONNECTED || bluetooth == TransportConnectionState.IDLE) VokieTheme.colors.success else VokieTheme.colors.textSecondary
    Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("COMMUNICATION STATUS", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusDot("Bluetooth", bluetooth.name.replace('_', ' '), bluetoothColor)
                StatusDot("Wi-Fi Direct", "NOT IMPLEMENTED", VokieTheme.colors.textSecondary)
                StatusDot("Internet", "NOT REQUIRED", VokieTheme.colors.textSecondary)
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
        item { Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("CURRENT STATUS", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary); Text("You are safe", style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.success); Spacer(Modifier.height(8.dp)); Text("Vokie operates without a backend. Open Communicate to discover and connect to a nearby Vokie peer.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } } }
        item { Spacer(Modifier.height(22.dp)); PushToTalkButton(onClick = onSpeak); Text("Speak in your language. Vokie converts your speech to text and transmits it locally.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 36.dp, vertical = 14.dp)) }
        item { Text("Language  •  Tamil · தமிழ்", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item { Spacer(Modifier.height(18.dp)); SosButton(onClick = onSos) }
        item { Text("BLUETOOTH ONLY  •  Real peer communication", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
fun PushToTalkButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, shape = CircleShape, modifier = Modifier.size(VokieDimens.pushToTalkSize).wrapContentWidth().fillMaxWidth().semantics { contentDescription = "Hold to speak. Activates local speech recognition." }, colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.surface, contentColor = VokieTheme.colors.textPrimary), border = androidx.compose.foundation.BorderStroke(2.dp, VokieTheme.colors.border), contentPadding = PaddingValues(12.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Mic, null, Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text("HOLD TO SPEAK", style = VokieTheme.typography.label) } }
}

@Composable
fun SosButton(onClick: () -> Unit) { Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.alert), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(VokieDimens.sosButtonHeight).padding(horizontal = 20.dp).semantics { contentDescription = "SOS emergency broadcast. Requires hold confirmation." }) { Icon(Icons.Default.Warning, null); Spacer(Modifier.width(10.dp)); Column { Text("SOS", style = VokieTheme.typography.label); Text("Emergency Broadcast", style = VokieTheme.typography.caption) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(onDismiss: () -> Unit, vm: CommunicationViewModel = viewModel()) {
    var message by rememberSaveable { mutableStateOf("I need emergency assistance.") }
    val connectedPeer by vm.connectedPeerId.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VokieTheme.colors.surface) {
        Column(Modifier.padding(24.dp).navigationBarsPadding()) {
            Text("SEND EMERGENCY SOS", style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp)); Text("This creates a real local SOS message. It is transmitted to the connected Vokie peer, or safely queued until a peer connects.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary)
            Spacer(Modifier.height(18.dp)); Text("TRANSPORT   ${if (connectedPeer == null) "NO PEER — WILL QUEUE" else "BLUETOOTH CONNECTED"}", style = VokieTheme.typography.label, color = if (connectedPeer == null) VokieTheme.colors.textSecondary else VokieTheme.colors.success)
            Spacer(Modifier.height(18.dp)); OutlinedTextField(value = message, onValueChange = { if (it.length <= com.vokie.communication.VokieProtocol.MAX_TEXT_CHARS) message = it }, label = { Text("Emergency message") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(20.dp)); Button(onClick = { vm.send(message, type = MessageType.SOS, onQueued = onDismiss) }, enabled = message.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.alert), modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(if (connectedPeer == null) "QUEUE SOS" else "SEND SOS", style = VokieTheme.typography.label) }
            Spacer(Modifier.height(16.dp)); TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
fun CommunicateScreen(vm: CommunicationViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val peers by vm.peers.collectAsState()
    val state by vm.connectionState.collectAsState()
    val connectedPeer by vm.connectedPeerId.collectAsState()
    val messages by vm.messages.collectAsState()
    val error by vm.error.collectAsState()
    var composer by rememberSaveable { mutableStateOf("") }
    var pendingPeer by remember { mutableStateOf<String?>(null) }
    var pendingVisibility by remember { mutableStateOf(false) }
    val discoverability = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_CANCELED) vm.reportError("This phone was not made visible. It can still connect to already discovered peers.")
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) when { pendingVisibility -> { pendingVisibility = false; vm.startListening(); discoverability.launch(vm.discoverabilityRequest()) }; pendingPeer != null -> pendingPeer?.let(vm::connect); else -> vm.discover() }
        else { vm.reportError("Nearby Devices permission is required to discover and communicate with nearby Vokie phones."); pendingPeer = null; pendingVisibility = false }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { AppHeader("Communicate", "Bluetooth text communication") }
        item { Column(Modifier.padding(horizontal = 20.dp)) {
            Text("BLUETOOTH  •  ${state.name.replace('_', ' ')}", style = VokieTheme.typography.labelSmall, color = if (state == TransportConnectionState.FAILED || state == TransportConnectionState.BLUETOOTH_DISABLED || state == TransportConnectionState.PERMISSION_REQUIRED) VokieTheme.colors.alert else VokieTheme.colors.textSecondary)
            connectedPeer?.let { Text("CONNECTED PEER  •  $it", style = VokieTheme.typography.caption, color = VokieTheme.colors.success, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { pendingVisibility = true; pendingPeer = null; if (BluetoothPermission.hasDiscoverability(context)) { vm.startListening(); discoverability.launch(vm.discoverabilityRequest()) } else permissions.launch(BluetoothPermission.discoverabilityPermissions()) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.surface)) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(8.dp)); Text("MAKE THIS PHONE VISIBLE") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { pendingVisibility = false; pendingPeer = null; if (BluetoothPermission.hasDiscovery(context)) vm.discover() else permissions.launch(BluetoothPermission.discoveryPermissions()) }, enabled = state != TransportConnectionState.SEARCHING, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.surface)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (state == TransportConnectionState.SEARCHING) "SEARCHING…" else "FIND NEARBY VOKIE DEVICES") }
            if (state == TransportConnectionState.SEARCHING) TextButton(onClick = vm::stopDiscovery, modifier = Modifier.fillMaxWidth()) { Text("STOP DISCOVERY") }
            if (peers.isEmpty()) Text(if (state == TransportConnectionState.SEARCHING) "Searching for protocol-compatible devices…" else "No nearby Vokie device detected.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
        } }
        items(peers, key = { it.id }) { peer -> Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bluetooth, null, tint = VokieTheme.colors.textSecondary); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(peer.name, style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary); Text("${if (peer.bonded) "PAIRED  •  " else ""}${peer.address}${peer.rssi?.let { "  •  $it dBm" } ?: ""}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary) }; Button(onClick = { pendingPeer = peer.id; if (BluetoothPermission.hasConnection(context)) vm.connect(peer.id) else permissions.launch(BluetoothPermission.connectionPermissions()) }, enabled = connectedPeer != peer.id, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (connectedPeer == peer.id) "CONNECTED" else "CONNECT") } } } }
        item { Column(Modifier.padding(20.dp)) {
            error?.let { Text(it, style = VokieTheme.typography.body, color = VokieTheme.colors.alert, modifier = Modifier.padding(bottom = 10.dp)) }
            Text("MESSAGE", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary)
            OutlinedTextField(value = composer, onValueChange = { if (it.length <= com.vokie.communication.VokieProtocol.MAX_TEXT_CHARS) composer = it }, label = { Text("Message text") }, supportingText = { Text("${composer.length}/${com.vokie.communication.VokieProtocol.MAX_TEXT_CHARS}") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = { vm.send(composer, onQueued = { composer = "" }) }, enabled = composer.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text(if (connectedPeer == null) "QUEUE MESSAGE" else "SEND MESSAGE") }
            Text(if (connectedPeer == null) "No peer is connected. Messages are persisted and remain queued." else "Messages are persisted before transmission and marked received only after peer ACK.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(20.dp)); Text("MESSAGES", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary)
        } }
        items(messages, key = { it.id }) { message -> Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) { MessageBubble(message, onRetry = { vm.retry(message.id) }) } }
    }
}

@Composable
fun MessageBubble(message: Message, onRetry: () -> Unit) {
    val statusColor = if (message.deliveryState == DeliveryState.RECEIVED_BY_PEER) VokieTheme.colors.success else if (message.deliveryState == DeliveryState.FAILED) VokieTheme.colors.alert else VokieTheme.colors.textSecondary
    Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("FROM  •  ${message.senderId.take(16)}", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary); Text(message.text, style = VokieTheme.typography.bodyLarge, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 10.dp)); Text("${message.language}  •  ${message.transport?.name ?: "WAITING FOR TRANSPORT"}  •  ${message.hopCount} hop", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) { Icon(if (message.deliveryState == DeliveryState.FAILED) Icons.Default.Error else Icons.Default.Info, null, Modifier.size(18.dp), tint = statusColor); Spacer(Modifier.width(6.dp)); Text(message.deliveryState.name.replace('_', ' '), style = VokieTheme.typography.labelSmall, color = statusColor); if (message.retryCount > 0) Text("  •  Retry ${message.retryCount}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary) }; message.lastError?.let { Text(it, style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 6.dp)) }; if (message.deliveryState == DeliveryState.FAILED) TextButton(onClick = onRetry) { Text("RETRY") } } }
}

@Composable
fun MapScreen() { Column(Modifier.fillMaxSize()) { AppHeader("Map", "Offline-first safety map"); Column(Modifier.padding(horizontal = 20.dp)) { Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.fillMaxWidth().height(310.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Map, null, Modifier.size(48.dp), tint = VokieTheme.colors.textSecondary); Text("MAP AVAILABLE OFFLINE", style = VokieTheme.typography.label, color = VokieTheme.colors.success, modifier = Modifier.padding(top = 12.dp)); Text("Tamil Nadu region  •  Updated locally", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } } }; Spacer(Modifier.height(18.dp)); Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.surface)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download Region") }; Spacer(Modifier.height(20.dp)); Text("MAP LEGEND", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary); Text("●  Current location    +  Shelters    ✚  Hospitals    !  Hazard zones", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp)) } } }

@Composable
fun AlertsScreen() { val alerts = listOf("Flood Warning" to "Water level rising near your area.", "Shelter Open" to "Community shelter open at Government School.", "Road Closure" to "Avoid the east bridge. Use marked safe routes."); LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) { item { AppHeader("Alerts", "Chronological emergency feed") }; items(alerts) { (title, body) -> Card(colors = CardDefaults.cardColors(containerColor = VokieTheme.colors.surface), border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("CRITICAL", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.alert); Text(title, style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.textPrimary); Text(body, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp)); Text("12 min ago  •  Local authority  •  ACTIVE", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted) } } } } }

@Composable
fun MoreScreen() { LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { item { AppHeader("More", "Tools for staying safe offline") }; item { MoreSection("CONTACTS / CHECK-IN", listOf("Emergency Contacts", "MARK ME SAFE", "Broadcast Safe Status")) }; item { MoreSection("OFFLINE RESOURCES", listOf("First Aid", "Flood", "Fire", "Earthquake", "Evacuation", "Emergency Communication")) }; item { MoreSection("SETTINGS", listOf("Language", "Communication Mode", "Bluetooth", "Wi-Fi Direct", "Ultrasonic", "Offline Map Storage", "Accessibility", "Dark / Light Mode", "Haptic Feedback", "Voice Settings", "About Vokie")) }; item { Text("Offline Mode\nAll communication processing occurs on this device.\n\nVoice when networks fail.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(20.dp)) } } }

@Composable
fun MoreSection(title: String, items: List<String>) { Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(title, style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp)); items.forEach { item -> Surface(color = VokieTheme.colors.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(item, style = VokieTheme.typography.body, color = VokieTheme.colors.textPrimary); Icon(Icons.Default.ChevronRight, null, tint = VokieTheme.colors.textMuted) } } } } }
