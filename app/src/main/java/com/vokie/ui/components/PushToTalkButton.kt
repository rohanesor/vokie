package com.vokie.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.stt.SttLanguage
import com.vokie.stt.SttState
import com.vokie.stt.SttStatus
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

@Composable
fun PushToTalkButton(
    status: SttStatus,
    language: SttLanguage,
    microphoneGranted: Boolean,
    onRequestMicrophone: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMicGranted by rememberUpdatedState(microphoneGranted)
    val currentStatus by rememberUpdatedState(status)
    val isListening = currentStatus.state == SttState.LISTENING
    val isProcessing = currentStatus.state == SttState.PROCESSING
    val isInitializing = currentStatus.state in setOf(
        SttState.UNINITIALIZED,
        SttState.IMPORTING,
        SttState.VALIDATING,
        SttState.INITIALIZING,
    )
    val isMissing = currentStatus.state == SttState.MODEL_MISSING

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_scale",
        )
    } else {
        rememberUpdatedState(1.0f)
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isListening -> VokieTheme.colors.alert
            isProcessing -> VokieTheme.colors.accent.copy(alpha = 0.8f)
            !currentMicGranted -> VokieTheme.colors.warning.copy(alpha = 0.25f)
            else -> VokieTheme.colors.accent
        },
        label = "ptt_bg_color",
    )

    val contentColor = when {
        isListening || isProcessing || currentMicGranted -> Color.White
        else -> VokieTheme.colors.warning
    }

    // Do not key this recognizer on STT state: startVoice changes READY→LISTENING
    // while the finger is still down, which would cancel tryAwaitRelease and strand PTT.
    val pttModifier = Modifier.pointerInput(currentMicGranted) {
        detectTapGestures(
            onPress = {
                if (!currentMicGranted) {
                    onRequestMicrophone()
                    return@detectTapGestures
                }
                if (currentStatus.state in setOf(SttState.READY, SttState.RESULT, SttState.ERROR)) {
                    onStartVoice()
                    tryAwaitRelease()
                    onStopVoice()
                }
            },
            onTap = {
                if (!currentMicGranted) {
                    onRequestMicrophone()
                }
            },
        )
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(VokieDimens.pttCorner),
        border = BorderStroke(
            1.dp,
            if (isListening) VokieTheme.colors.alert else VokieTheme.colors.accent.copy(alpha = 0.6f),
        ),
        shadowElevation = if (isListening) 4.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(VokieDimens.pttHeight)
            .scale(if (isListening) pulseScale else 1.0f)
            .then(pttModifier)
            .semantics {
                contentDescription = when {
                    !currentMicGranted -> "Grant microphone permission to speak"
                    isListening -> "Listening… Release to send in ${language.nativeName}"
                    isProcessing -> "Understanding speech…"
                    else -> "Hold to speak in ${language.nativeName}"
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            when {
                !currentMicGranted -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "GRANT MICROPHONE PERMISSION",
                            style = VokieTheme.typography.label,
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Tap here to enable offline voice",
                            style = VokieTheme.typography.caption,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "UNDERSTANDING…",
                        style = VokieTheme.typography.label,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                isListening -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "LISTENING…",
                            style = VokieTheme.typography.label,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Release to send",
                            style = VokieTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                isInitializing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "PREPARING OFFLINE ENGINE…",
                        style = VokieTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                isMissing -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "SPEECH MODEL PREPARING…",
                        style = VokieTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "HOLD TO SPEAK",
                            style = VokieTheme.typography.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = language.nativeName,
                            style = VokieTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
