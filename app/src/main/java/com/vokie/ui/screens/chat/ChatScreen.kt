package com.vokie.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vokie.R
import com.vokie.communication.VokieProtocol
import com.vokie.stt.SttLanguage
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsState
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.CompactLanguagePair
import com.vokie.ui.components.ConnectionStatusBar
import com.vokie.ui.components.MessageBubble
import com.vokie.ui.components.PushToTalkButton
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

@Composable
fun ChatScreen(
    vm: CommunicationViewModel,
    onOpenEmergency: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connectionState by vm.connectionState.collectAsState()
    val wifiDirectState by vm.wifiDirectState.collectAsState()
    val connectedPeerId by vm.connectedPeerId.collectAsState()
    val peers by vm.peers.collectAsState()
    val messages by vm.selectedPeerMessages.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val selectedPeer by vm.selectedPeerId.collectAsState()
    val effectivePeer by vm.effectivePeerId.collectAsState()
    val sttStatus by vm.sttStatus.collectAsState()
    val preferredLanguage by vm.preferredLanguage.collectAsState()
    val ttsStates by vm.messageTtsStates.collectAsState()
    val installedTtsLanguages by vm.installedTtsLanguages.collectAsState()
    val ttsStatus by vm.ttsStatus.collectAsState()
    val presentations by vm.receiverPresentations.collectAsState()

    var showConnectionSheet by rememberSaveable { mutableStateOf(false) }
    var composerText by rememberSaveable { mutableStateOf("") }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        if (!granted) {
            vm.reportError("Microphone permission is required for voice messaging.")
        }
    }

    LaunchedEffect(Unit) {
        vm.initializeStt()
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.stopVoice()
        }
    }

    // Voice transcripts are enqueued by ContinuousTurnManager events in the ViewModel.
    // Do not observe raw STT RESULT here: that would create a second STT/message owner.

    val selectedInputLang = preferredLanguage?.inputSttLanguage ?: SttLanguage.ENGLISH
    val selectedOutputTtsLang = preferredLanguage?.preferredOutputLanguage?.let {
        TtsLanguage.fromMessageCode(it.code)
    } ?: TtsLanguage.ENGLISH
    // Assets alone are not a promise of playback: the active engine must be ready.
    val isTtsInstalled = selectedOutputTtsLang in installedTtsLanguages && ttsStatus.state == TtsState.READY

    val peerSessions by vm.peerSessions.collectAsState()
    val selectedSession = effectivePeer?.let { peerSessions[it] }
    val connectedPeerName = selectedSession?.displayName
        ?: peers.firstOrNull { it.id == connectedPeerId }?.name

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VokieTheme.colors.background)
            .imePadding(),
    ) {
        // TOP APP BAR: Branding + Connection + Language Summary + SOS shortcut
        Surface(
            color = VokieTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = VokieTheme.typography.header,
                            color = VokieTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Offline nearby communication",
                            style = VokieTheme.typography.caption,
                            color = VokieTheme.colors.textSecondary,
                        )
                    }

                    // Emergency SOS Shortcut
                    TextButton(
                        onClick = onOpenEmergency,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = VokieTheme.colors.alert,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency Mode",
                            tint = VokieTheme.colors.alert,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "SOS",
                            style = VokieTheme.typography.label,
                            color = VokieTheme.colors.alert,
                        )
                    }
                }

                HorizontalDivider(
                    color = VokieTheme.colors.border.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // Sub-row: Connection Indicator & Language Flow Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ConnectionStatusBar(
                        connectionState = connectionState,
                        connectedPeerName = connectedPeerName,
                        transportLabel = if (wifiDirectState == com.vokie.communication.PacketTransportState.CONNECTED) "Wi-Fi Direct" else null,
                        onClick = { showConnectionSheet = true },
                    )

                    CompactLanguagePair(
                        profile = preferredLanguage,
                        onClick = onOpenLanguages,
                    )
                }
            }
        }

        // MIDDLE AREA: Message Stream (Fills available height)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (messages.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .background(VokieTheme.colors.surface, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = VokieTheme.colors.textSecondary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = "No messages yet",
                        style = VokieTheme.typography.label,
                        color = VokieTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "Hold the button below to speak, or type a message. All communication is 100% offline.",
                        style = VokieTheme.typography.body,
                        color = VokieTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { message ->
                        val incoming = vm.isIncoming(message)
                        val presentation = presentations.values.firstOrNull { it.messageId == message.id }
                        MessageBubble(
                            message = message,
                            incoming = incoming,
                            presentation = presentation,
                            ttsState = ttsStates[message.id],
                            isTtsInstalledForLanguage = isTtsInstalled,
                            onRetry = { vm.retry(message.id) },
                            onPlayTts = { vm.playMessage(message) },
                            onStopTts = { vm.stopMessage(message.id) },
                            onAcknowledgeSos = { vm.acknowledgeSos(message.id) },
                        )
                    }
                }
            }
        }

        // BOTTOM AREA: FIXED Voice PTT & Text Input (Never scrolls!)
        Surface(
            color = VokieTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, VokieTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Large Push-To-Talk Button (Primary Action)
                PushToTalkButton(
                    status = sttStatus,
                    language = selectedInputLang,
                    microphoneGranted = microphoneGranted,
                    onRequestMicrophone = {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStartVoice = vm::startVoice,
                    onStopVoice = vm::stopVoice,
                )

                Spacer(Modifier.height(10.dp))

                // Compact Secondary Text Composer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = composerText,
                        onValueChange = {
                            if (it.length <= VokieProtocol.MAX_TEXT_CHARS) composerText = it
                        },
                        placeholder = {
                            Text(
                                text = "Type a message…",
                                style = VokieTheme.typography.body,
                                color = VokieTheme.colors.textMuted,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(VokieDimens.buttonCorner),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VokieTheme.colors.accent,
                            unfocusedBorderColor = VokieTheme.colors.border,
                            cursorColor = VokieTheme.colors.accent,
                            focusedTextColor = VokieTheme.colors.textPrimary,
                            unfocusedTextColor = VokieTheme.colors.textPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (composerText.isNotBlank()) {
                                vm.send(composerText, onQueued = { composerText = "" })
                            }
                        },
                        enabled = composerText.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (composerText.isNotBlank()) VokieTheme.colors.accent else VokieTheme.colors.border.copy(alpha = 0.5f),
                            contentColor = Color.White,
                            disabledContainerColor = VokieTheme.colors.border.copy(alpha = 0.3f),
                            disabledContentColor = VokieTheme.colors.textMuted,
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (composerText.isNotBlank()) VokieTheme.colors.accent else VokieTheme.colors.surface,
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (composerText.isNotBlank()) Color.White else VokieTheme.colors.textMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    if (showConnectionSheet) {
        ConnectionSheet(
            vm = vm,
            onDismiss = { showConnectionSheet = false },
        )
    }
}
