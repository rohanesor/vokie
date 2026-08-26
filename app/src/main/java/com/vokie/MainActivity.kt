package com.vokie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vokie.communication.BluetoothPermission
import com.vokie.communication.VokieProtocol
import com.vokie.domain.model.*
import com.vokie.map.*
import com.vokie.stt.*
import com.vokie.tts.*
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.map.MapViewModel
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
    val stt by vm.sttStatus.collectAsState()
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
        item { VoiceControl(onCommunicate, stt) }
        item { SosButton(onSos) }
    }
}

@Composable
private fun VoiceControl(onCommunicate: () -> Unit, stt: SttStatus) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("VOICE COMMUNICATION")
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(172.dp).border(2.dp, VokieTheme.colors.accent.copy(alpha = .38f), CircleShape).padding(10.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = VokieTheme.colors.surface, border = BorderStroke(1.dp, VokieTheme.colors.border), modifier = Modifier.fillMaxSize().semantics { contentDescription = "Open offline voice communication. STT state ${stt.state.name}." }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(if (stt.state == SttState.MODEL_MISSING) Icons.Default.MicOff else Icons.Default.Mic, null, Modifier.size(38.dp), tint = if (stt.state == SttState.READY) VokieTheme.colors.accent else VokieTheme.colors.textSecondary)
                    Spacer(Modifier.height(8.dp)); Text("VOICE INPUT", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                    Text(stt.state.name.replace('_', ' '), style = VokieTheme.typography.labelSmall, color = if (stt.state == SttState.READY) VokieTheme.colors.success else VokieTheme.colors.warning)
                }
            }
        }
        Text(if (stt.state == SttState.MODEL_MISSING) "Preparing the verified offline speech engine…" else "Speech is captured and transcribed locally. Audio never leaves this phone.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp).widthIn(max = 420.dp))
        PrimaryAction("OPEN VOICE COMMUNICATION", Icons.AutoMirrored.Filled.Chat, onCommunicate, Modifier.fillMaxWidth().padding(top = 16.dp))
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
    val stt by vm.sttStatus.collectAsState()
    val selectedLanguage by vm.selectedSttLanguage.collectAsState()
    val ttsStatus by vm.ttsStatus.collectAsState()
    val ttsStates by vm.messageTtsStates.collectAsState()
    val installedTtsLanguages by vm.installedTtsLanguages.collectAsState()
    val ttsSpeed by vm.ttsSpeed.collectAsState()
    val selectedTtsLanguage = TtsLanguage.fromMessageCode(selectedLanguage.messageLanguage.code) ?: TtsLanguage.ENGLISH
    val pushToTalk by vm.pushToTalkEnabled.collectAsState()
    val bt = bluetoothUi(state)
    LaunchedEffect(vm) { vm.initializeStt() }
    DisposableEffect(vm) { onDispose { vm.stopDiscovery(); vm.stopVoice() } }
    var composer by rememberSaveable { mutableStateOf("") }
    var microphoneGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var microphoneDenied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(stt.result?.timestamp) { stt.result?.let { composer = it.text } }
    var pendingPeer by remember { mutableStateOf<String?>(null) }
    var pendingVisibility by remember { mutableStateOf(false) }
    val discoverability = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_CANCELED) vm.reportError("This phone was not made visible. It can still connect to known peers.")
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
        microphoneDenied = !granted
        if (!granted) vm.reportError("Microphone permission required for voice messaging.")
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
        item { ScreenHeader("Communicate", "Offline voice-to-text and real nearby communication.") }
        item {
            SttPanel(
                status = stt,
                language = selectedLanguage,
                microphoneGranted = microphoneGranted,
                microphoneDenied = microphoneDenied,
                pushToTalk = pushToTalk,
                onPushToTalkChanged = vm::setPushToTalk,
                onLanguageSelected = vm::selectSttLanguage,
                onRequestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
                onStart = vm::startVoice,
                onStop = vm::stopVoice,
            )
        }
        item {
            TtsPanel(
                status = ttsStatus,
                language = selectedTtsLanguage,
                installed = selectedTtsLanguage in installedTtsLanguages,
                speed = ttsSpeed,
                onSpeedChanged = vm::setTtsSpeed,
                onStop = vm::stopTts,
            )
        }
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
        else items(messages, key = { it.id }) { message ->
            MessageCard(
                message = message,
                ttsState = ttsStates[message.id],
                incoming = vm.isIncoming(message),
                onRetry = { vm.retry(message.id) },
                onPlay = { vm.playMessage(message) },
                onStop = { vm.stopMessage(message.id) },
                onAcknowledgeSos = { vm.acknowledgeSos(message.id) },
            )
        }
    }
}

@Composable
private fun TtsPanel(
    status: TtsStatus,
    language: TtsLanguage,
    installed: Boolean,
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    onStop: () -> Unit,
) {
    var pendingSpeed by remember(speed) { mutableFloatStateOf(speed) }
    VokiePanel(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("OFFLINE TEXT TO SPEECH")
                Text("MMS-TTS • sherpa-onnx $SHERPA_ONNX_VERSION • local PCM", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 5.dp))
            }
            StatusBadge(if (installed) "MMS-TTS READY" else "PREPARING OFFLINE TTS", if (installed) VokieTheme.colors.success else VokieTheme.colors.warning, loading = !installed || status.state == TtsState.INITIALIZING)
        }
        Text("Message language selects the voice model. Current language: ${language.nativeName} (${language.iso6393}).", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
        if (status.state in setOf(TtsState.IMPORTING, TtsState.VALIDATING, TtsState.INITIALIZING)) {
            Text("Model ${status.state.name.lowercase().replace('_', ' ')}...", style = VokieTheme.typography.body, color = VokieTheme.colors.accent, modifier = Modifier.padding(top = 8.dp))
        }
        Text(if (installed) "✓ MMS-TTS ready • bundled for all 10 offline languages" else "Preparing verified bundled TTS assets…", style = VokieTheme.typography.caption, color = if (installed) VokieTheme.colors.success else VokieTheme.colors.warning, modifier = Modifier.padding(top = 10.dp))
        Text("Speech speed • ${String.format(java.util.Locale.US, "%.2f", pendingSpeed)}x", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 14.dp))
        Slider(value = pendingSpeed, onValueChange = { pendingSpeed = it }, onValueChangeFinished = { onSpeedChanged(pendingSpeed) }, valueRange = MIN_TTS_SPEED..MAX_TTS_SPEED, steps = 2, enabled = status.state !in setOf(TtsState.SYNTHESIZING, TtsState.PLAYING))
        if (status.state in setOf(TtsState.SYNTHESIZING, TtsState.PLAYING)) SecondaryAction(if (status.state == TtsState.PLAYING) "STOP PLAYBACK" else "STOP AFTER SYNTHESIS", Icons.Default.Stop, onStop, Modifier.fillMaxWidth())
        status.failure?.let { Text(it.userMessage, style = VokieTheme.typography.body, color = VokieTheme.colors.alert, modifier = Modifier.padding(top = 10.dp)) }
        status.result?.let { result ->
            Text("${result.textLength} chars • synthesis ${result.synthesisTimeMs} ms • audio ${result.audioDurationMs} ms • RTF ${result.realTimeFactor?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "unavailable"}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        status.modelLoadTimeMs?.let { Text("Model load ${it} ms", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun SttPanel(
    status: SttStatus,
    language: SttLanguage,
    microphoneGranted: Boolean,
    microphoneDenied: Boolean,
    pushToTalk: Boolean,
    onPushToTalkChanged: (Boolean) -> Unit,
    onLanguageSelected: (SttLanguage) -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var languageMenu by remember { mutableStateOf(false) }
    val canSpeak = microphoneGranted && status.state in setOf(SttState.READY, SttState.RESULT, SttState.ERROR)
    val voiceModifier = when {
        !canSpeak -> Modifier
        pushToTalk -> Modifier.pointerInput(language, status.state) {
            detectTapGestures(onPress = {
                onStart()
                tryAwaitRelease()
                onStop()
            })
        }
        else -> Modifier.pointerInput(language, status.state) {
            detectTapGestures(onTap = {
                if (status.state == SttState.LISTENING) onStop() else onStart()
            })
        }
    }
    VokiePanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("OFFLINE SPEECH TO TEXT")
                Text("whisper.cpp • tiny multilingual Q5_1 • local only", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 5.dp))
            }
            StatusBadge(status.state.name.replace('_', ' '), when (status.state) {
                SttState.READY, SttState.RESULT -> VokieTheme.colors.success
                SttState.LISTENING, SttState.PROCESSING, SttState.INITIALIZING -> VokieTheme.colors.accent
                SttState.ERROR -> VokieTheme.colors.alert
                else -> VokieTheme.colors.warning
            }, loading = status.state in setOf(SttState.IMPORTING, SttState.VALIDATING, SttState.INITIALIZING, SttState.PROCESSING))
        }
        Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            SecondaryAction(language.nativeName, Icons.Default.Language, { languageMenu = true }, enabled = status.state !in setOf(SttState.LISTENING, SttState.PROCESSING), modifier = Modifier.fillMaxWidth())
            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                SttLanguage.entries.forEach { item ->
                    DropdownMenuItem(text = { Text("${item.nativeName}  •  ${item.whisperCode}") }, onClick = { onLanguageSelected(item); languageMenu = false }, trailingIcon = { if (item == language) Icon(Icons.Default.Check, null) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Push-to-talk", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary); Text(if (pushToTalk) "Hold the button to speak" else "Tap to start, silence stops", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary) }
            Switch(checked = pushToTalk, onCheckedChange = onPushToTalkChanged, enabled = status.state !in setOf(SttState.LISTENING, SttState.PROCESSING))
        }
        if (status.state == SttState.MODEL_MISSING || status.failure?.code == SttErrorCode.MODEL_LOAD_FAILED) {
            Text("Preparing bundled offline speech recognition…", style = VokieTheme.typography.body, color = VokieTheme.colors.warning, modifier = Modifier.padding(top = 14.dp))
        } else {
            if (status.installedModelBytes > 0) Text("Model installed • ${formatBytes(status.installedModelBytes)}", style = VokieTheme.typography.caption, color = VokieTheme.colors.success, modifier = Modifier.padding(top = 10.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (status.state == SttState.LISTENING) VokieTheme.colors.alert.copy(alpha = .18f) else VokieTheme.colors.accent.copy(alpha = .12f),
                border = BorderStroke(1.dp, if (status.state == SttState.LISTENING) VokieTheme.colors.alert else VokieTheme.colors.accent.copy(alpha = .55f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(top = 14.dp).then(voiceModifier).semantics { contentDescription = if (canSpeak) if (pushToTalk) "Hold to speak in ${language.nativeName}" else "Tap to speak in ${language.nativeName}" else "Voice input unavailable: ${status.state.name}" },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(16.dp)) {
                    Icon(if (status.state == SttState.LISTENING) Icons.Default.GraphicEq else Icons.Default.Mic, null, Modifier.size(34.dp), tint = if (status.state == SttState.LISTENING) VokieTheme.colors.alert else VokieTheme.colors.accent)
                    Text(when (status.state) {
                        SttState.LISTENING -> status.vadState.name.replace('_', ' ')
                        SttState.PROCESSING -> "TRANSCRIBING LOCALLY"
                        SttState.IMPORTING -> "IMPORTING MODEL"
                        SttState.VALIDATING -> "VALIDATING MODEL"
                        SttState.INITIALIZING -> "INITIALIZING MODEL"
                        else -> if (microphoneGranted) if (pushToTalk) "HOLD TO SPEAK" else "TAP TO SPEAK" else "MICROPHONE PERMISSION REQUIRED"
                    }, style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 8.dp))
                    Text(if (pushToTalk) "Release to transcribe • silence finalizes after 1.2 seconds" else "Tap once to speak • silence finalizes after 1.2 seconds", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center)
                }
            }
            if (!microphoneGranted) {
                Text("Microphone permission required for voice messaging. Audio is processed only on this phone.", style = VokieTheme.typography.body, color = VokieTheme.colors.warning, modifier = Modifier.padding(top = 12.dp))
                PrimaryAction("GRANT MICROPHONE PERMISSION", Icons.Default.Mic, onRequestMicrophone, Modifier.fillMaxWidth().padding(top = 10.dp))
                if (microphoneDenied) TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("OPEN APP SETTINGS") }
            }
        }
        status.failure?.let { Text(it.userMessage, style = VokieTheme.typography.body, color = VokieTheme.colors.alert, modifier = Modifier.padding(top = 12.dp)) }
        status.result?.let { result ->
            Text("You said:", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 14.dp))
            Text("“${result.text}”", style = VokieTheme.typography.bodyLarge, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 5.dp))
            Text("Audio ${result.audioDurationMs} ms • STT ${result.processingTimeMs} ms • RTF ${result.realTimeFactor?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "unavailable"}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 7.dp))
        }
        status.modelLoadTimeMs?.let { Text("Model load ${it} ms", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 7.dp)) }
    }
}

@Composable
private fun InlineError(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).background(VokieTheme.colors.alert.copy(alpha = .11f)).border(1.dp, VokieTheme.colors.alert.copy(alpha = .34f), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ErrorOutline, null, tint = VokieTheme.colors.alert); Spacer(Modifier.width(9.dp)); Text(message, style = VokieTheme.typography.body, color = VokieTheme.colors.textPrimary, modifier = Modifier.weight(1f)); IconButton(onDismiss, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Dismiss error", tint = VokieTheme.colors.textSecondary) }
    }
}

@Composable
private fun MessageCard(
    message: Message,
    ttsState: MessageTtsState?,
    incoming: Boolean,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onAcknowledgeSos: () -> Unit,
) {
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
        if (incoming && message.messageType == MessageType.SOS) Text("NEARBY SOS ALERT", style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.alert, modifier = Modifier.padding(top = 12.dp))
        Text(message.text, style = VokieTheme.typography.bodyLarge, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 12.dp))
        Text("${message.language}  •  ${message.transport?.name ?: "WAITING FOR TRANSPORT"}  •  ${message.hopCount} hop", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
        if (message.retryCount > 0) Text("Retry ${message.retryCount} of 3", style = VokieTheme.typography.caption, color = VokieTheme.colors.warning, modifier = Modifier.padding(top = 5.dp))
        message.lastError?.let { Text(it, style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 5.dp)) }
        if (message.deliveryState == DeliveryState.FAILED) SecondaryAction("RETRY MESSAGE", Icons.Default.Refresh, onRetry, Modifier.fillMaxWidth().padding(top = 12.dp))
        if (incoming) {
            ttsState?.let { Text("Speech • ${it.name.replace('_', ' ')}", style = VokieTheme.typography.caption, color = if (it == MessageTtsState.FAILED) VokieTheme.colors.alert else VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
            when {
                message.messageType == MessageType.SOS && ttsState in setOf(MessageTtsState.QUEUED, MessageTtsState.SYNTHESIZING, MessageTtsState.PLAYING) -> SecondaryAction("ACKNOWLEDGED — STOP ALERT", Icons.Default.Stop, onAcknowledgeSos, Modifier.fillMaxWidth().padding(top = 12.dp))
                ttsState in setOf(MessageTtsState.SYNTHESIZING, MessageTtsState.PLAYING) -> SecondaryAction("STOP SPEECH", Icons.Default.Stop, onStop, Modifier.fillMaxWidth().padding(top = 12.dp))
                else -> SecondaryAction("PLAY MESSAGE", Icons.AutoMirrored.Filled.VolumeUp, onPlay, Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
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
fun MapScreen(vm: MapViewModel = viewModel()) {
    val status by vm.status.collectAsState()
    val points by vm.points.collectAsState()
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importRegion) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Offline map", "Local safety information without an internet dependency.") }
        item {
            VokiePanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("MAP STORAGE")
                    StatusBadge(status.state.name.replace('_', ' '), when (status.state) {
                        MapRegionState.READY -> VokieTheme.colors.success
                        MapRegionState.DOWNLOADING -> VokieTheme.colors.accent
                        MapRegionState.FAILED -> VokieTheme.colors.alert
                        else -> VokieTheme.colors.warning
                    }, loading = status.state == MapRegionState.DOWNLOADING)
                }
                Text(status.region.description, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                if (status.state == MapRegionState.READY && points.isNotEmpty()) {
                    OfflineMapCanvas(points, Modifier.fillMaxWidth().height(260.dp).padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(VokieTheme.colors.background).border(1.dp, VokieTheme.colors.border, RoundedCornerShape(12.dp)))
                    Text("${points.size} local points • last updated ${status.lastUpdated?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "unknown"}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(210.dp).padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(VokieTheme.colors.background).border(1.dp, VokieTheme.colors.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Map, null, Modifier.size(42.dp), tint = VokieTheme.colors.textSecondary); Text(if (status.state == MapRegionState.DOWNLOADING) "Downloading region..." else "No offline region", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 10.dp)); Text("Shelters, hospitals, and hazard zones require downloaded local map data.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
                    }
                }
                when (status.state) {
                    MapRegionState.NOT_DOWNLOADED, MapRegionState.FAILED, MapRegionState.UPDATE_AVAILABLE -> {
                        PrimaryAction("DOWNLOAD BASELINE REGION", Icons.Default.Download, onClick = vm::downloadDefault, enabled = status.state != MapRegionState.DOWNLOADING, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                        SecondaryAction("IMPORT REGION PACK (ZIP)", Icons.Default.FolderOpen, onClick = { mapPicker.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = status.state != MapRegionState.DOWNLOADING, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                    MapRegionState.READY -> {
                        SecondaryAction("IMPORT NEW REGION PACK", Icons.Default.FolderOpen, onClick = { mapPicker.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                        TextButton(onClick = vm::deleteRegion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("DELETE OFFLINE REGION", color = VokieTheme.colors.alert) }
                    }
                    MapRegionState.DOWNLOADING -> Unit
                }
                status.failure?.let { Text(it.userMessage, style = VokieTheme.typography.body, color = VokieTheme.colors.alert, modifier = Modifier.padding(top = 10.dp)) }
                if (status.installedBytes > 0) Text("Installed size • ${formatBytes(status.installedBytes)}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Column(Modifier.padding(20.dp)) { SectionLabel("MAP LEGEND"); LegendRow(VokieTheme.colors.accent, "Current location"); LegendRow(VokieTheme.colors.success, "Shelters and hospitals"); LegendRow(VokieTheme.colors.alert, "Hazard zones"); LegendRow(VokieTheme.colors.info, "Water / relief") }
        }
    }
}

@Composable
private fun OfflineMapCanvas(points: List<com.vokie.map.MapPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }
    val maxLon = points.maxOf { it.lon }
    val padding = 0.1
    val latRange = (maxLat - minLat).coerceAtLeast(0.001)
    val lonRange = (maxLon - minLon).coerceAtLeast(0.001)
    val background = VokieTheme.colors.background
    val border = VokieTheme.colors.border
    val textPrimary = VokieTheme.colors.textPrimary
    androidx.compose.foundation.Canvas(modifier = modifier.semantics { contentDescription = "Offline map showing ${points.size} local points of interest" }) {
        val w = size.width
        val h = size.height
        drawRect(background)
        val gridColor = border.copy(alpha = .5f)
        for (i in 0..4) {
            val x = w * i / 4f
            val y = h * i / 4f
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1f)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
        }
        points.forEach { p ->
            val x = (((p.lon - minLon) / lonRange) * (1 - 2 * padding) + padding).toFloat() * w
            val y = (1f - (((p.lat - minLat) / latRange) * (1 - 2 * padding) + padding).toFloat()) * h
            val color = android.graphics.Color.parseColor(p.type.colorHex()).let { Color(it) }
            drawCircle(color, radius = 10f, center = androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(textPrimary.copy(alpha = .6f), radius = 10f, center = androidx.compose.ui.geometry.Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) { Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(10.dp)); Text(label, style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary) } }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

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
fun MoreScreen(vm: CommunicationViewModel = viewModel(), mapVm: MapViewModel = viewModel()) {
    val bluetooth by vm.connectionState.collectAsState()
    val stt by vm.sttStatus.collectAsState()
    val tts by vm.ttsStatus.collectAsState()
    val ttsSpeed by vm.ttsSpeed.collectAsState()
    val language by vm.selectedSttLanguage.collectAsState()
    val mapStatus by mapVm.status.collectAsState()
    val mapPoints by mapVm.points.collectAsState()
    val bt = bluetoothUi(bluetooth)
    val mapValue = when (mapStatus.state) {
        com.vokie.map.MapRegionState.READY -> "READY"
        com.vokie.map.MapRegionState.DOWNLOADING -> "DOWNLOADING"
        com.vokie.map.MapRegionState.FAILED -> "FAILED"
        com.vokie.map.MapRegionState.UPDATE_AVAILABLE -> "UPDATE"
        com.vokie.map.MapRegionState.NOT_DOWNLOADED -> "NOT INSTALLED"
    }
    val mapDetail = if (mapStatus.state == com.vokie.map.MapRegionState.READY) "${mapPoints.size} points • ${formatBytes(mapStatus.installedBytes)}" else mapStatus.region.description
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Settings and resources", "Communication capability, offline storage, and accessibility.") }
        item { SettingsGroup("COMMUNICATION", listOf(
            SettingInfo(Icons.Default.Emergency, "Emergency communication", "SOS messages use the persistent outbound queue", "ACTIVE"),
            SettingInfo(Icons.AutoMirrored.Filled.Message, "Communication mode", "Local speech becomes text before using the existing queue", "VOICE + TEXT"),
            SettingInfo(Icons.Default.Bluetooth, "Bluetooth", bt.detail, bt.label),
            SettingInfo(Icons.Default.Wifi, "Wi-Fi Direct", "Secondary peer transport", "PLANNED"),
            SettingInfo(Icons.Default.GraphicEq, "Ultrasonic", "Experimental audio transport", "PLANNED"),
        )) }
        item { SettingsGroup("OFFLINE", listOf(
            SettingInfo(Icons.Default.Map, "Offline map storage", mapDetail, mapValue),
            SettingInfo(Icons.Default.Folder, "Downloaded regions", if (mapStatus.state == com.vokie.map.MapRegionState.READY) "${mapPoints.size} local POIs" else "No region installed", if (mapStatus.state == com.vokie.map.MapRegionState.READY) "READY" else "EMPTY"),
        )) }
        item { SettingsGroup("PERSONALISATION", listOf(
            SettingInfo(Icons.Default.Language, "Language", "Current STT and message language", language.nativeName),
            SettingInfo(Icons.Default.RecordVoiceOver, "Speech recognition", "Offline whisper.cpp multilingual recognition", stt.state.name.replace('_', ' ')),
            SettingInfo(Icons.AutoMirrored.Filled.VolumeUp, "Speech playback", "Offline MMS-TTS via sherpa-onnx • ${String.format(java.util.Locale.US, "%.2f", ttsSpeed)}x", tts.state.name.replace('_', ' ')),
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
        Text("Bluetooth RFCOMM peer transport — implemented\nWhisper.cpp STT — implemented\nMMS-TTS via sherpa-onnx — implemented\nOffline map regions — implemented\nWi-Fi Direct and ultrasonic — planned", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
    }
}
