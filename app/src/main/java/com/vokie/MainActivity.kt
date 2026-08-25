package com.vokie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vokie.communication.BluetoothPermission
import com.vokie.communication.VokieProtocol
import com.vokie.domain.model.*
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { VokieTheme { VokieApp() } }
    }
}

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    COMMUNICATE("Communicate", Icons.Default.Mic),
    MAP("Map", Icons.Default.Map),
    ALERTS("Alerts", Icons.Default.Notifications),
    MORE("More", Icons.Default.Menu),
}

@Composable
fun VokieApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var showSos by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = VokieTheme.colors.background,
        bottomBar = {
            NavigationBar(
                containerColor = VokieTheme.colors.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Screen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = VokieTheme.colors.textPrimary,
                            indicatorColor = VokieTheme.colors.accent.copy(alpha = .24f),
                            unselectedIconColor = VokieTheme.colors.textSecondary,
                            unselectedTextColor = VokieTheme.colors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(onCommunicate = { screen = Screen.COMMUNICATE }, onSos = { showSos = true })
                Screen.COMMUNICATE -> CommunicateScreen()
                Screen.MAP -> MapScreen()
                Screen.ALERTS -> AlertsScreen()
                Screen.MORE -> MoreScreen()
            }
        }
    }
    if (showSos) SosSheet(onDismiss = { showSos = false })
}

private data class BluetoothUi(val label: String, val detail: String, val color: Color, val actionable: Boolean)

@Composable
private fun bluetoothUi(state: TransportConnectionState): BluetoothUi = when (state) {
    TransportConnectionState.UNAVAILABLE -> BluetoothUi("UNAVAILABLE", "Bluetooth hardware is not available on this phone.", VokieTheme.colors.alert, false)
    TransportConnectionState.PERMISSION_REQUIRED -> BluetoothUi("PERMISSION REQUIRED", "Nearby Devices permission is required to communicate.", VokieTheme.colors.warning, true)
    TransportConnectionState.BLUETOOTH_DISABLED -> BluetoothUi("TURNED OFF", "Turn on Bluetooth to discover nearby Vokie phones.", VokieTheme.colors.warning, false)
    TransportConnectionState.SEARCHING -> BluetoothUi("SCANNING", "Searching for protocol-compatible nearby devices.", VokieTheme.colors.accent, true)
    TransportConnectionState.CONNECTING -> BluetoothUi("CONNECTING", "Establishing a secure nearby connection.", VokieTheme.colors.accent, true)
    TransportConnectionState.CONNECTED -> BluetoothUi("CONNECTED", "A Vokie peer is connected.", VokieTheme.colors.success, true)
    TransportConnectionState.FAILED -> BluetoothUi("FAILED", "The last Bluetooth operation failed. You can try again.", VokieTheme.colors.alert, true)
    TransportConnectionState.IDLE, TransportConnectionState.DISCONNECTED -> BluetoothUi("READY", "Bluetooth is available. No Vokie peer is connected.", VokieTheme.colors.success, true)
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Text("VOKIE", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.accent, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary)
        Text(subtitle, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary, letterSpacing = 1.1.sp, modifier = modifier)
}

@Composable
private fun VokiePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = VokieTheme.colors.surface,
        shape = RoundedCornerShape(VokieDimens.cardCorner),
        border = BorderStroke(1.dp, VokieTheme.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier,
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun StatusBadge(label: String, color: Color, modifier: Modifier = Modifier, loading: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = .13f))
            .border(1.dp, color.copy(alpha = .38f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = color)
        else Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, style = VokieTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun PrimaryAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(VokieDimens.buttonCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = VokieTheme.colors.accent,
            contentColor = Color.White,
            disabledContainerColor = VokieTheme.colors.border.copy(alpha = .72f),
            disabledContentColor = VokieTheme.colors.textSecondary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp, disabledElevation = 0.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = VokieTheme.typography.label)
    }
}

@Composable
private fun SecondaryAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(VokieDimens.buttonCorner),
        border = BorderStroke(1.dp, if (enabled) VokieTheme.colors.accent.copy(alpha = .72f) else VokieTheme.colors.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VokieTheme.colors.textPrimary, disabledContentColor = VokieTheme.colors.textSecondary),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, style = VokieTheme.typography.label)
    }
}

@Composable
fun HomeScreen(onCommunicate: () -> Unit, onSos: () -> Unit, vm: CommunicationViewModel = viewModel()) {
    val bluetooth by vm.connectionState.collectAsState()
    val messages by vm.messages.collectAsState()
    val bt = bluetoothUi(bluetooth)
    val queued = messages.count { it.deliveryState == DeliveryState.QUEUED || it.deliveryState == DeliveryState.RETRYING }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.vokie_logo), "Official Vokie logo", Modifier.size(64.dp))
                Spacer(Modifier.width(14.dp))
                Column { Text("VOKIE", style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary); Text("Voice when networks fail.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) }
            }
        }
        item {
            VokiePanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("SAFETY STATUS")
                    StatusBadge("STATUS NOT SET", VokieTheme.colors.warning)
                }
                Spacer(Modifier.height(16.dp))
                Text("No safety status selected", style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.textPrimary)
                Text("Vokie will never assume you are safe. Use Check-in when you are ready to broadcast a real status.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = VokieTheme.colors.border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Bluetooth", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary); Text(bt.detail, style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, maxLines = 2) }
                    Spacer(Modifier.width(12.dp)); StatusBadge(bt.label, bt.color, loading = bluetooth == TransportConnectionState.SEARCHING || bluetooth == TransportConnectionState.CONNECTING)
                }
                if (queued > 0) Text("$queued message${if (queued == 1) "" else "s"} securely queued", style = VokieTheme.typography.caption, color = VokieTheme.colors.warning, modifier = Modifier.padding(top = 12.dp))
            }
        }
        item { VoiceControl(onCommunicate) }
        item { SosButton(onSos) }
    }
}

@Composable
private fun VoiceControl(onCommunicate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("VOICE COMMUNICATION")
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(172.dp).border(2.dp, VokieTheme.colors.accent.copy(alpha = .38f), CircleShape).padding(10.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = VokieTheme.colors.surface, border = BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.fillMaxSize().semantics { contentDescription = "Voice input unavailable. Local speech recognition is not installed." }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.MicOff, null, Modifier.size(38.dp), tint = VokieTheme.colors.textSecondary)
                    Spacer(Modifier.height(8.dp)); Text("VOICE INPUT", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                    Text("NOT INSTALLED", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.warning)
                }
            }
        }
        Text("Local speech recognition is not installed yet. Use the real text communication path below.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp).widthIn(max = 420.dp))
        PrimaryAction("OPEN COMMUNICATE", Icons.AutoMirrored.Filled.Chat, onCommunicate, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

@Composable
private fun SosButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.alert, contentColor = Color.White),
        shape = RoundedCornerShape(VokieDimens.cardCorner),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 20.dp).semantics { contentDescription = "SOS emergency message. Confirmation required." },
    ) {
        Icon(Icons.Default.Warning, null, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
        Column { Text("SOS", style = VokieTheme.typography.label); Text("Create an emergency message", style = VokieTheme.typography.caption, color = Color.White.copy(alpha = .86f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(onDismiss: () -> Unit, vm: CommunicationViewModel = viewModel()) {
    var message by rememberSaveable { mutableStateOf("I need emergency assistance.") }
    val connectedPeer by vm.connectedPeerId.collectAsState()
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VokieTheme.colors.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            StatusBadge("CRITICAL ACTION", VokieTheme.colors.alert)
            Text("Send emergency SOS", style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
            Text("The message is stored locally first, then sent to the connected Vokie peer or held safely in the offline queue.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            Text("Transport  •  ${if (connectedPeer == null) "No peer — will queue" else "Bluetooth connected"}", style = VokieTheme.typography.labelSmall, color = if (connectedPeer == null) VokieTheme.colors.warning else VokieTheme.colors.success, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= VokieProtocol.MAX_TEXT_CHARS) message = it },
                label = { Text("Emergency message") },
                supportingText = { Text("${message.length}/${VokieProtocol.MAX_TEXT_CHARS}") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Button(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.send(message, type = MessageType.SOS, onQueued = onDismiss) },
                enabled = message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VokieTheme.colors.alert, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(top = 14.dp),
            ) { Text(if (connectedPeer == null) "QUEUE SOS" else "SEND SOS", style = VokieTheme.typography.label) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel", color = VokieTheme.colors.textSecondary) }
        }
    }
}

@Composable
fun CommunicateScreen(vm: CommunicationViewModel = viewModel()) {
    val context = LocalContext.current
    val peers by vm.peers.collectAsState()
    val state by vm.connectionState.collectAsState()
    val connectedPeer by vm.connectedPeerId.collectAsState()
    val messages by vm.messages.collectAsState()
    val error by vm.error.collectAsState()
    val bt = bluetoothUi(state)
    DisposableEffect(vm) { onDispose { vm.stopDiscovery() } }
    var composer by rememberSaveable { mutableStateOf("") }
    var pendingPeer by remember { mutableStateOf<String?>(null) }
    var pendingVisibility by remember { mutableStateOf(false) }
    val discoverability = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_CANCELED) vm.reportError("This phone was not made visible. It can still connect to known peers.")
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) when {
            pendingVisibility -> { pendingVisibility = false; vm.startListening(); discoverability.launch(vm.discoverabilityRequest()) }
            pendingPeer != null -> pendingPeer?.let(vm::connect)
            else -> vm.discover()
        } else {
            vm.reportError("Nearby Devices permission is required to discover and communicate with nearby Vokie phones.")
            pendingPeer = null; pendingVisibility = false
        }
    }
    val actionsEnabled = state != TransportConnectionState.UNAVAILABLE && state != TransportConnectionState.BLUETOOTH_DISABLED
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { ScreenHeader("Communicate", "Real nearby text communication with persistent offline queueing.") }
        item {
            VokiePanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionLabel("BLUETOOTH"); Text(bt.detail, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 5.dp)) }
                    Spacer(Modifier.width(12.dp)); StatusBadge(bt.label, bt.color, loading = state == TransportConnectionState.SEARCHING || state == TransportConnectionState.CONNECTING)
                }
                connectedPeer?.let { Text("Connected peer  •  $it", style = VokieTheme.typography.caption, color = VokieTheme.colors.success, modifier = Modifier.padding(top = 12.dp)) }
                error?.let { InlineError(it, vm::clearError) }
                SecondaryAction(
                    "MAKE THIS PHONE VISIBLE", Icons.Default.Visibility,
                    onClick = { pendingVisibility = true; pendingPeer = null; if (BluetoothPermission.hasDiscoverability(context)) { vm.startListening(); discoverability.launch(vm.discoverabilityRequest()) } else permissions.launch(BluetoothPermission.discoverabilityPermissions()) },
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                PrimaryAction(
                    if (state == TransportConnectionState.SEARCHING) "SCANNING FOR DEVICES" else "FIND NEARBY VOKIE DEVICES",
                    Icons.Default.Search,
                    onClick = { pendingVisibility = false; pendingPeer = null; if (BluetoothPermission.hasDiscovery(context)) vm.discover() else permissions.launch(BluetoothPermission.discoveryPermissions()) },
                    enabled = actionsEnabled && state != TransportConnectionState.CONNECTING,
                    loading = state == TransportConnectionState.SEARCHING,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                if (state == TransportConnectionState.SEARCHING) TextButton(onClick = vm::stopDiscovery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Stop scanning") }
            }
        }
        item {
            if (peers.isEmpty()) EmptyState(Icons.AutoMirrored.Filled.BluetoothSearching, "No nearby Vokie devices", if (state == TransportConnectionState.SEARCHING) "Scanning is active. Compatible devices will appear here." else "Make one phone visible, then scan from the other phone.", Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            else SectionLabel("NEARBY VOKIE DEVICES", Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp))
        }
        items(peers, key = { it.id }) { peer ->
            VokiePanel(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(VokieTheme.colors.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PhoneAndroid, null, tint = VokieTheme.colors.accent) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(peer.name, style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                        Text("${if (peer.bonded) "Paired  •  " else ""}${peer.address}${peer.rssi?.let { "  •  $it dBm" } ?: ""}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
                    }
                    if (connectedPeer == peer.id) StatusBadge("CONNECTED", VokieTheme.colors.success)
                    else TextButton(onClick = { pendingPeer = peer.id; if (BluetoothPermission.hasConnection(context)) vm.connect(peer.id) else permissions.launch(BluetoothPermission.connectionPermissions()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CONNECT", color = VokieTheme.colors.accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
                SectionLabel("MESSAGE")
                OutlinedTextField(
                    value = composer,
                    onValueChange = { if (it.length <= VokieProtocol.MAX_TEXT_CHARS) composer = it },
                    label = { Text("Message text") },
                    placeholder = { Text("Type a message to send or queue") },
                    supportingText = { Text("${composer.length}/${VokieProtocol.MAX_TEXT_CHARS}") },
                    minLines = 3,
                    shape = RoundedCornerShape(VokieDimens.cardCorner),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VokieTheme.colors.accent, cursorColor = VokieTheme.colors.accent),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                PrimaryAction(
                    if (connectedPeer == null) "QUEUE MESSAGE" else "SEND MESSAGE",
                    Icons.AutoMirrored.Filled.Send,
                    onClick = { vm.send(composer, onQueued = { composer = "" }) },
                    enabled = composer.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lock, null, Modifier.size(17.dp), tint = VokieTheme.colors.textSecondary); Spacer(Modifier.width(8.dp))
                    Text(if (connectedPeer == null) "No peer connected. This message will remain securely queued until a Bluetooth connection is available." else "Stored locally before transmission. Peer receipt is shown only after a real acknowledgement.", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
                }
                SectionLabel("MESSAGES", Modifier.padding(top = 24.dp, bottom = 4.dp))
            }
        }
        if (messages.isEmpty()) item { EmptyState(Icons.Default.Forum, "No messages yet", "Messages created or received on this phone will appear here.", Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        else items(messages, key = { it.id }) { message -> MessageCard(message, onRetry = { vm.retry(message.id) }) }
    }
}

@Composable
private fun InlineError(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).background(VokieTheme.colors.alert.copy(alpha = .11f)).border(1.dp, VokieTheme.colors.alert.copy(alpha = .34f), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ErrorOutline, null, tint = VokieTheme.colors.alert); Spacer(Modifier.width(9.dp)); Text(message, style = VokieTheme.typography.body, color = VokieTheme.colors.textPrimary, modifier = Modifier.weight(1f)); IconButton(onDismiss, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Dismiss error", tint = VokieTheme.colors.textSecondary) }
    }
}

@Composable
private fun MessageCard(message: Message, onRetry: () -> Unit) {
    val statusColor = when (message.deliveryState) {
        DeliveryState.RECEIVED_BY_PEER -> VokieTheme.colors.success
        DeliveryState.FAILED -> VokieTheme.colors.alert
        DeliveryState.TRANSMITTING -> VokieTheme.colors.accent
        DeliveryState.RETRYING -> VokieTheme.colors.warning
        else -> VokieTheme.colors.textSecondary
    }
    VokiePanel(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(message.messageType.name.replace('_', ' '), style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary)
            StatusBadge(message.deliveryState.name.replace('_', ' '), statusColor, loading = message.deliveryState == DeliveryState.TRANSMITTING)
        }
        Text(message.text, style = VokieTheme.typography.bodyLarge, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 12.dp))
        Text("${message.language}  •  ${message.transport?.name ?: "WAITING FOR TRANSPORT"}  •  ${message.hopCount} hop", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
        if (message.retryCount > 0) Text("Retry ${message.retryCount} of 3", style = VokieTheme.typography.caption, color = VokieTheme.colors.warning, modifier = Modifier.padding(top = 5.dp))
        message.lastError?.let { Text(it, style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 5.dp)) }
        if (message.deliveryState == DeliveryState.FAILED) SecondaryAction("RETRY MESSAGE", Icons.Default.Refresh, onRetry, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(VokieTheme.colors.surface), contentAlignment = Alignment.Center) { Icon(icon, null, tint = VokieTheme.colors.textSecondary) }
        Text(title, style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text(detail, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp).widthIn(max = 440.dp))
    }
}

@Composable
fun MapScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Offline map", "Local safety information without an internet dependency.") }
        item {
            VokiePanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionLabel("MAP STORAGE"); StatusBadge("NOT DOWNLOADED", VokieTheme.colors.warning) }
                Box(Modifier.fillMaxWidth().height(210.dp).padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(VokieTheme.colors.background).border(1.dp, VokieTheme.colors.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Map, null, Modifier.size(42.dp), tint = VokieTheme.colors.textSecondary); Text("No offline region", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 10.dp)); Text("Shelters, hospitals, and hazard zones require downloaded map data.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
                }
                PrimaryAction("DOWNLOAD REGION", Icons.Default.Download, onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                Text("Offline map downloads are not implemented in this build.", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Column(Modifier.padding(20.dp)) { SectionLabel("MAP LEGEND"); LegendRow(VokieTheme.colors.accent, "Current location"); LegendRow(VokieTheme.colors.success, "Shelters and hospitals"); LegendRow(VokieTheme.colors.alert, "Hazard zones") }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) { Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(10.dp)); Text(label, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } }

@Composable
fun AlertsScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Emergency alerts", "Locally stored alerts remain readable without a network.") }
        item { EmptyState(Icons.Default.NotificationsNone, "No emergency alerts", "No alert records are stored on this device. Vokie will not fabricate an active warning.", Modifier.padding(horizontal = 20.dp)) }
        item {
            VokiePanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                SectionLabel("SEVERITY KEY")
                SeverityKey("CRITICAL", VokieTheme.colors.alert, "Immediate danger")
                SeverityKey("WARNING", VokieTheme.colors.warning, "Potential danger")
                SeverityKey("INFORMATION", VokieTheme.colors.accent, "Safety information")
                SeverityKey("RESOLVED", VokieTheme.colors.success, "No longer active")
            }
        }
    }
}

@Composable
private fun SeverityKey(label: String, color: Color, description: String) { Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { StatusBadge(label, color); Spacer(Modifier.width(12.dp)); Text(description, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } }

private data class SettingInfo(val icon: ImageVector, val title: String, val description: String, val value: String)

@Composable
fun MoreScreen(vm: CommunicationViewModel = viewModel()) {
    val bluetooth by vm.connectionState.collectAsState()
    val bt = bluetoothUi(bluetooth)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Settings and resources", "Communication capability, offline storage, and accessibility.") }
        item { SettingsGroup("COMMUNICATION", listOf(
            SettingInfo(Icons.Default.Emergency, "Emergency communication", "SOS messages use the persistent outbound queue", "ACTIVE"),
            SettingInfo(Icons.AutoMirrored.Filled.Message, "Communication mode", "Real text messaging while local STT is unavailable", "TEXT"),
            SettingInfo(Icons.Default.Bluetooth, "Bluetooth", bt.detail, bt.label),
            SettingInfo(Icons.Default.Wifi, "Wi-Fi Direct", "Secondary peer transport", "PLANNED"),
            SettingInfo(Icons.Default.GraphicEq, "Ultrasonic", "Experimental audio transport", "PLANNED"),
        )) }
        item { SettingsGroup("OFFLINE", listOf(
            SettingInfo(Icons.Default.Map, "Offline map storage", "No region downloaded", "EMPTY"),
            SettingInfo(Icons.Default.Folder, "Downloaded regions", "Offline region management", "UNAVAILABLE"),
        )) }
        item { SettingsGroup("PERSONALISATION", listOf(
            SettingInfo(Icons.Default.Language, "Language", "Current message language", "ENGLISH"),
            SettingInfo(Icons.Default.RecordVoiceOver, "Voice settings", "Local speech engines are not installed", "UNAVAILABLE"),
            SettingInfo(Icons.Default.AccessibilityNew, "Accessibility", "Uses Android text scaling and screen-reader semantics", "SYSTEM"),
            SettingInfo(Icons.Default.Vibration, "Haptic feedback", "Confirmation feedback for critical actions", "ENABLED"),
            SettingInfo(Icons.Default.DarkMode, "Appearance", "Dark emergency interface", "DARK"),
        )) }
        item { AboutCard() }
    }
}

@Composable
private fun SettingsGroup(title: String, rows: List<SettingInfo>) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        SectionLabel(title, Modifier.padding(bottom = 8.dp))
        Surface(color = VokieTheme.colors.surface, shape = RoundedCornerShape(VokieDimens.cardCorner), border = BorderStroke(1.dp, VokieTheme.colors.border)) {
            Column { rows.forEachIndexed { index, row -> SettingRow(row); if (index != rows.lastIndex) HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(start = 64.dp)) } }
        }
    }
}

@Composable
private fun SettingRow(info: SettingInfo) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(VokieTheme.colors.accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(info.icon, null, Modifier.size(20.dp), tint = VokieTheme.colors.accent) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(info.title, style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary); Text(info.description, style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary) }
        Text(info.value, style = VokieTheme.typography.labelSmall, color = if (info.value in setOf("ACTIVE", "READY", "CONNECTED", "ENABLED")) VokieTheme.colors.success else VokieTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 96.dp))
    }
}

@Composable
private fun AboutCard() {
    VokiePanel(Modifier.padding(20.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.vokie_logo), "Official Vokie logo", Modifier.size(84.dp))
            Column(Modifier.padding(start = 16.dp)) { Text("VOKIE", style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary); Text("Voice when networks fail.", style = VokieTheme.typography.label, color = VokieTheme.colors.textSecondary); Text("Version ${BuildConfig.VERSION_NAME}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 5.dp)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = VokieTheme.colors.border)
        Text("Offline multilingual emergency communication.", style = VokieTheme.typography.body, color = VokieTheme.colors.textPrimary)
        Text("Open source  •  Offline-first\nBluetooth — Implemented\nWi-Fi Direct — Planned", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
    }
}
