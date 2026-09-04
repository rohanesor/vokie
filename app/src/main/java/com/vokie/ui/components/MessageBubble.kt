package com.vokie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vokie.domain.model.DeliveryState
import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.translation.ReceiverPresentation
import com.vokie.tts.MessageTtsState
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

data class DeliveryUiInfo(
    val label: String,
    val color: Color,
    val icon: ImageVector,
    val isLoading: Boolean = false,
)

@Composable
fun deliveryUiInfo(state: DeliveryState): DeliveryUiInfo {
    return when (state) {
        DeliveryState.QUEUED -> DeliveryUiInfo(
            label = "Waiting to send",
            color = VokieTheme.colors.textSecondary,
            icon = Icons.Default.Schedule,
        )
        DeliveryState.TRANSMITTING -> DeliveryUiInfo(
            label = "Sending…",
            color = VokieTheme.colors.accent,
            icon = Icons.Default.Send,
            isLoading = true,
        )
        DeliveryState.DELIVERED -> DeliveryUiInfo(
            label = "Delivered",
            color = VokieTheme.colors.success,
            icon = Icons.Default.Check,
        )
        DeliveryState.RECEIVED_BY_PEER, DeliveryState.RELAYED -> DeliveryUiInfo(
            label = "Received",
            color = VokieTheme.colors.success,
            icon = Icons.Default.DoneAll,
        )
        DeliveryState.RETRYING -> DeliveryUiInfo(
            label = "Retrying…",
            color = VokieTheme.colors.warning,
            icon = Icons.Default.Refresh,
            isLoading = true,
        )
        DeliveryState.FAILED -> DeliveryUiInfo(
            label = "Not sent",
            color = VokieTheme.colors.alert,
            icon = Icons.Default.ErrorOutline,
        )
    }
}

@Composable
fun MessageBubble(
    message: Message,
    incoming: Boolean,
    presentation: ReceiverPresentation?,
    ttsState: MessageTtsState?,
    isTtsInstalledForLanguage: Boolean,
    onRetry: () -> Unit,
    onPlayTts: () -> Unit,
    onStopTts: () -> Unit,
    onAcknowledgeSos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSos = message.messageType == MessageType.SOS
    val delivery = deliveryUiInfo(message.deliveryState)

    val borderColor = when {
        isSos -> VokieTheme.colors.alert
        incoming -> VokieTheme.colors.border
        else -> VokieTheme.colors.accent.copy(alpha = 0.4f)
    }

    val backgroundColor = when {
        isSos -> VokieTheme.colors.alert.copy(alpha = 0.08f)
        incoming -> VokieTheme.colors.surface
        else -> VokieTheme.colors.surface
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(VokieDimens.cardCorner),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header Row: Party (YOU / NEARBY) + Delivery / Emergency Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSos) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency SOS",
                            tint = VokieTheme.colors.alert,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isSos) "EMERGENCY SOS" else if (incoming) "NEARBY" else "YOU",
                        style = VokieTheme.typography.labelSmall,
                        color = if (isSos) VokieTheme.colors.alert else if (incoming) VokieTheme.colors.accent else VokieTheme.colors.textSecondary,
                    )
                }

                if (!incoming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (delivery.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = delivery.color,
                            )
                        } else {
                            Icon(
                                imageVector = delivery.icon,
                                contentDescription = delivery.label,
                                tint = delivery.color,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = delivery.label,
                            style = VokieTheme.typography.caption,
                            color = delivery.color,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Body: Original + Translated + Voice
            TranslationCard(
                message = message,
                incoming = incoming,
                presentation = presentation,
                ttsState = ttsState,
                isTtsInstalledForLanguage = isTtsInstalledForLanguage,
                onPlayTts = onPlayTts,
                onStopTts = onStopTts,
            )

            // Retry button for failed messages
            if (!incoming && message.deliveryState == DeliveryState.FAILED) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VokieTheme.colors.alert.copy(alpha = 0.2f),
                        contentColor = VokieTheme.colors.alert,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry sending",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("RETRY SENDING", style = VokieTheme.typography.labelSmall)
                }
            }

            // Acknowledge SOS alert action
            if (incoming && isSos) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAcknowledgeSos,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VokieTheme.colors.alert,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Acknowledge alert",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("ACKNOWLEDGE & STOP ALERT", style = VokieTheme.typography.labelSmall)
                }
            }
        }
    }
}
