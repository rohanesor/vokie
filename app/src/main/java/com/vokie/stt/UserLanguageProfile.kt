package com.vokie.stt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userLanguageProfileStore by preferencesDataStore(name = "user_language_profile")

/** Local input/output choice. AUTO is a recognition mode, not a profile language. */
data class UserLanguageProfile(
    val preferredInputLanguage: VokieLanguage,
    val preferredOutputLanguage: VokieLanguage = preferredInputLanguage,
) {
    val inputSttLanguage: SttLanguage get() = requireNotNull(SttLanguage.fromMessageCode(preferredInputLanguage.code))
    val outputSttLanguage: SttLanguage get() = requireNotNull(SttLanguage.fromMessageCode(preferredOutputLanguage.code))

    companion object {
        val onboardingOptions: List<UserLanguageProfile> = VokieLanguage.entries.map(::UserLanguageProfile)
        fun same(language: VokieLanguage) = UserLanguageProfile(language, language)
        fun fromCodes(input: String?, output: String?): UserLanguageProfile? {
            val inputLanguage = input?.let(VokieLanguage::fromCode) ?: return null
            val outputLanguage = output?.let(VokieLanguage::fromCode) ?: return null
            return UserLanguageProfile(inputLanguage, outputLanguage)
        }
    }
}

class UserLanguageProfilePreferences(private val context: Context) {
    val profile: Flow<UserLanguageProfile?> = context.userLanguageProfileStore.data.map { values ->
        UserLanguageProfile.fromCodes(values[INPUT_LANGUAGE], values[OUTPUT_LANGUAGE])
            // Migration from the single profile key never invents English.
            ?: values[LEGACY_PREFERRED_LANGUAGE]?.let(VokieLanguage::fromCode)?.let(UserLanguageProfile::same)
    }

    suspend fun select(profile: UserLanguageProfile) {
        context.userLanguageProfileStore.edit {
            it[INPUT_LANGUAGE] = profile.preferredInputLanguage.code
            it[OUTPUT_LANGUAGE] = profile.preferredOutputLanguage.code
            it.remove(LEGACY_PREFERRED_LANGUAGE)
        }
    }

    private companion object {
        val INPUT_LANGUAGE = stringPreferencesKey("preferred_input_language")
        val OUTPUT_LANGUAGE = stringPreferencesKey("preferred_output_language")
        val LEGACY_PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
    }
}
