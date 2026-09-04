package com.vokie.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.R
import com.vokie.domain.model.VokieLanguage
import com.vokie.stt.UserLanguageProfile
import com.vokie.ui.components.VokiePanel
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

private val supportedLanguages = listOf(
    VokieLanguage.EN to "English",
    VokieLanguage.HI to "हिन्दी",
    VokieLanguage.TA to "தமிழ்",
)

@Composable
fun OnboardingScreen(
    onCompleted: (UserLanguageProfile) -> Unit,
    initialProfile: UserLanguageProfile? = null,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(if (initialProfile == null) 1 else 2) }
    var spokenLanguage by rememberSaveable { mutableStateOf(initialProfile?.preferredInputLanguage) }
    var understandLanguage by rememberSaveable { mutableStateOf(initialProfile?.preferredOutputLanguage) }

    val scrollState = rememberScrollState()

    Surface(
        color = VokieTheme.colors.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Spacer(Modifier.height(32.dp))

                // App Branding Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.vokie_logo),
                        contentDescription = "iTantra Logo",
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = VokieTheme.typography.header,
                            color = VokieTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Offline Emergency Communication",
                            style = VokieTheme.typography.caption,
                            color = VokieTheme.colors.textSecondary,
                        )
                    }
                }

                HorizontalDivider(
                    color = VokieTheme.colors.border,
                    modifier = Modifier.padding(bottom = 24.dp),
                )

                if (step == 1) {
                    // STEP 1: What language do you speak?
                    Text(
                        text = "STEP 1 OF 2",
                        style = VokieTheme.typography.labelSmall,
                        color = VokieTheme.colors.accent,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "What language do you speak?",
                        style = VokieTheme.typography.header,
                        color = VokieTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Text(
                        text = "This sets the offline speech recognition language when you speak.",
                        style = VokieTheme.typography.body,
                        color = VokieTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )

                    supportedLanguages.forEach { (lang, nativeName) ->
                        val isSelected = spokenLanguage == lang
                        LanguageOptionCard(
                            language = lang,
                            nativeName = nativeName,
                            isSelected = isSelected,
                            onSelect = { spokenLanguage = lang },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                } else {
                    // Independent editor: both selectors remain editable and may be equal.
                    Text(
                        text = if (initialProfile == null) "STEP 2 OF 2" else "LANGUAGE CONFIGURATION", 
                        style = VokieTheme.typography.labelSmall,
                        color = VokieTheme.colors.accent,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "Choose what you speak and what you understand", 
                        style = VokieTheme.typography.header,
                        color = VokieTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Text(
                        text = "These settings are independent. Your speech uses You Speak; incoming speech uses You Understand.",
                        style = VokieTheme.typography.body,
                        color = VokieTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )

                    Text("YOU SPEAK", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.accent, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    supportedLanguages.forEach { (lang, nativeName) ->
                        val isSelected = spokenLanguage == lang
                        LanguageOptionCard(
                            language = lang, nativeName = nativeName, isSelected = isSelected, onSelect = { spokenLanguage = lang }, modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Text("YOU UNDERSTAND", style = VokieTheme.typography.labelSmall, color = VokieTheme.colors.accent, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    supportedLanguages.forEach { (lang, nativeName) ->
                        val isSelected = understandLanguage == lang
                        LanguageOptionCard(
                            language = lang,
                            nativeName = nativeName,
                            isSelected = isSelected,
                            onSelect = { understandLanguage = lang },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }

                    if (spokenLanguage != null && understandLanguage != null) {
                        IconButton(onClick = { val old = spokenLanguage; spokenLanguage = understandLanguage; understandLanguage = old }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Swap You Speak and You Understand", tint = VokieTheme.colors.accent)
                        }
                    }
                    // Summary Box
                    if (spokenLanguage != null && understandLanguage != null) {
                        Spacer(Modifier.height(16.dp))
                        VokiePanel(
                            borderColor = VokieTheme.colors.accent.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "LANGUAGE PIPELINE",
                                style = VokieTheme.typography.labelSmall,
                                color = VokieTheme.colors.accent,
                                letterSpacing = 1.sp,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            ) {
                                Column {
                                    Text(
                                        text = "You speak",
                                        style = VokieTheme.typography.caption,
                                        color = VokieTheme.colors.textSecondary,
                                    )
                                    Text(
                                        text = spokenLanguage?.displayName ?: "",
                                        style = VokieTheme.typography.label,
                                        color = VokieTheme.colors.textPrimary,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "translates to",
                                    tint = VokieTheme.colors.accent,
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "You understand",
                                        style = VokieTheme.typography.caption,
                                        color = VokieTheme.colors.textSecondary,
                                    )
                                    Text(
                                        text = understandLanguage?.displayName ?: "",
                                        style = VokieTheme.typography.label,
                                        color = VokieTheme.colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                if (step == 1) {
                    Button(
                        onClick = { step = 2 },
                        enabled = spokenLanguage != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VokieTheme.colors.accent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(VokieDimens.buttonCorner),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = "NEXT: UNDERSTANDING LANGUAGE",
                            style = VokieTheme.typography.label,
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val input = spokenLanguage ?: VokieLanguage.EN
                            val output = understandLanguage ?: VokieLanguage.EN
                            onCompleted(UserLanguageProfile(input, output))
                        },
                        enabled = spokenLanguage != null && understandLanguage != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VokieTheme.colors.accent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(VokieDimens.buttonCorner),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = "CONTINUE TO iTANTRA",
                            style = VokieTheme.typography.label,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { step = 1 },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = VokieTheme.colors.textSecondary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Back to spoken language")
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionCard(
    language: VokieLanguage,
    nativeName: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) VokieTheme.colors.accent else VokieTheme.colors.border
    val backgroundColor = if (isSelected) VokieTheme.colors.accent.copy(alpha = 0.12f) else VokieTheme.colors.surface

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(VokieDimens.cardCorner),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(VokieDimens.cardCorner))
            .clickable(onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column {
                Text(
                    text = nativeName,
                    style = VokieTheme.typography.headerSmall,
                    color = VokieTheme.colors.textPrimary,
                )
                Text(
                    text = language.displayName,
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.textSecondary,
                )
            }

            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(VokieTheme.colors.accent),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
