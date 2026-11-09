package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Locale

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppLanguage(val localeTag: String, val displayLabel: String) {
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    BENGALI("bn", "বাংলা")
}

enum class ReadingMode {
    CONTINUOUS,
    SINGLE_PAGE,
    TWO_PAGE_SPREAD
}

enum class HomeSortOrder {
    DATE,
    NAME,
    SIZE
}

data class DeviceMemoryInfo(
    val isLowRamDevice: Boolean,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int
) {
    val shouldAutoEnableLowMemoryMode: Boolean
        get() = isLowRamDevice || memoryClassMb <= 192
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val defaultReadingMode: ReadingMode = ReadingMode.CONTINUOUS,
    val keepScreenOn: Boolean = false,
    val defaultTtsSpeed: Float = 1.0f,
    val userFullName: String = "",
    val userInitials: String = "",
    val userEmail: String = "",
    val userPhone: String = "",
    val userAddress: String = "",
    // Cloud AI Settings & Daily Limits
    val aiEnabled: Boolean = true,
    val aiConsentGranted: Boolean = false,
    val aiDailyLimit: Int = 5,
    val aiRequestsUsedToday: Int = 0,
    val aiUsageDateEpochDay: Long = 0L,
    // Polish: Onboarding, Low-Memory Mode, Privacy-First Analytics Opt-In, Folders & Sort
    val onboardingCompleted: Boolean = false,
    val lowMemoryModeEnabled: Boolean = false,
    val isLowRamHardwareDetected: Boolean = false,
    val deviceMemoryClassMb: Int = 256,
    val analyticsOptIn: Boolean = false,
    val customFolders: List<String> = listOf("Work", "Personal"),
    val homeSortOrder: HomeSortOrder = HomeSortOrder.DATE
) {
    val remainingAiRequestsToday: Int
        get() = (aiDailyLimit - aiRequestsUsedToday).coerceAtLeast(0)

    val isAiLimitReached: Boolean
        get() = aiRequestsUsedToday >= aiDailyLimit
}

class SettingsManager(private val appContext: Context) {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)

    val deviceMemoryInfo: DeviceMemoryInfo = detectDeviceMemoryInfo(appContext)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _recordedAnalyticsEvents = MutableStateFlow<List<String>>(emptyList())
    val recordedAnalyticsEvents: StateFlow<List<String>> = _recordedAnalyticsEvents.asStateFlow()

    private fun currentEpochDay(): Long {
        return try {
            LocalDate.now().toEpochDay()
        } catch (_: Exception) {
            System.currentTimeMillis() / (1000L * 60L * 60L * 24L)
        }
    }

    private fun loadSettings(): AppSettings {
        val themeStr = prefs.getString(KEY_THEME_MODE, AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name
        val langStr = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        val readingStr = prefs.getString(KEY_READING_MODE, ReadingMode.CONTINUOUS.name) ?: ReadingMode.CONTINUOUS.name
        val sortStr = prefs.getString(KEY_HOME_SORT_ORDER, HomeSortOrder.DATE.name) ?: HomeSortOrder.DATE.name
        val keepOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        val ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f)

        val fullName = prefs.getString(KEY_FULL_NAME, "") ?: ""
        val initials = prefs.getString(KEY_INITIALS, "") ?: ""
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val address = prefs.getString(KEY_ADDRESS, "") ?: ""

        val aiEnabled = prefs.getBoolean(KEY_AI_ENABLED, true)
        val aiConsentGranted = prefs.getBoolean(KEY_AI_CONSENT_GRANTED, false)
        val aiDailyLimit = prefs.getInt(KEY_AI_DAILY_LIMIT, 5).coerceIn(1, 500)
        val savedEpochDay = prefs.getLong(KEY_AI_USAGE_EPOCH_DAY, -1L)
        val todayEpoch = currentEpochDay()
        val usedToday = if (savedEpochDay == todayEpoch) {
            prefs.getInt(KEY_AI_REQUESTS_USED_TODAY, 0).coerceAtLeast(0)
        } else {
            prefs.edit()
                .putLong(KEY_AI_USAGE_EPOCH_DAY, todayEpoch)
                .putInt(KEY_AI_REQUESTS_USED_TODAY, 0)
                .apply()
            0
        }

        val onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, true)
        val lowMemoryDefault = deviceMemoryInfo.shouldAutoEnableLowMemoryMode
        val lowMemoryEnabled = if (prefs.contains(KEY_LOW_MEMORY_MODE)) {
            prefs.getBoolean(KEY_LOW_MEMORY_MODE, lowMemoryDefault)
        } else {
            lowMemoryDefault
        }

        // Strictly no analytics unless user explicitly opts in (default false)
        val analyticsOptIn = prefs.getBoolean(KEY_ANALYTICS_OPT_IN, false)

        val foldersRaw = prefs.getString(KEY_CUSTOM_FOLDERS, "Work|Personal") ?: "Work|Personal"
        val folders = foldersRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        return AppSettings(
            themeMode = try { AppThemeMode.valueOf(themeStr) } catch (_: Exception) { AppThemeMode.LIGHT },
            appLanguage = try { AppLanguage.valueOf(langStr) } catch (_: Exception) { AppLanguage.SYSTEM },
            defaultReadingMode = try { ReadingMode.valueOf(readingStr) } catch (_: Exception) { ReadingMode.CONTINUOUS },
            keepScreenOn = keepOn,
            defaultTtsSpeed = ttsSpeed,
            userFullName = fullName,
            userInitials = initials,
            userEmail = email,
            userPhone = phone,
            userAddress = address,
            aiEnabled = aiEnabled,
            aiConsentGranted = aiConsentGranted,
            aiDailyLimit = aiDailyLimit,
            aiRequestsUsedToday = usedToday,
            aiUsageDateEpochDay = todayEpoch,
            onboardingCompleted = onboardingCompleted,
            lowMemoryModeEnabled = lowMemoryEnabled,
            isLowRamHardwareDetected = deviceMemoryInfo.isLowRamDevice,
            deviceMemoryClassMb = deviceMemoryInfo.memoryClassMb,
            analyticsOptIn = analyticsOptIn,
            customFolders = folders,
            homeSortOrder = try { HomeSortOrder.valueOf(sortStr) } catch (_: Exception) { HomeSortOrder.DATE }
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    /**
     * Switches the application language between English, Bengali, and System
     * using AppCompatDelegate.setApplicationLocales so it works on Android 8–12 (API 26–32)
     * as well as Android 13+, and updates the StateFlow for immediate Compose recomposition.
     */
    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
        _settings.value = _settings.value.copy(appLanguage = language)
        applyApplicationLocale(language)
    }

    fun applySavedLocaleOnStartup() {
        applyApplicationLocale(_settings.value.appLanguage)
    }

    private fun applyApplicationLocale(language: AppLanguage) {
        try {
            val localeList = if (language == AppLanguage.SYSTEM || language.localeTag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.localeTag)
            }
            if (AppCompatDelegate.getApplicationLocales() != localeList) {
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        } catch (_: Exception) {
            // Safe fallback in headless unit test environments without an active Activity
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        prefs.edit().putString(KEY_READING_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(defaultReadingMode = mode)
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply()
        _settings.value = _settings.value.copy(keepScreenOn = keepOn)
    }

    fun setTtsSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_TTS_SPEED, speed).apply()
        _settings.value = _settings.value.copy(defaultTtsSpeed = speed)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _settings.value = _settings.value.copy(onboardingCompleted = completed)
    }

    fun setLowMemoryMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_MEMORY_MODE, enabled).apply()
        _settings.value = _settings.value.copy(lowMemoryModeEnabled = enabled)
    }

    /**
     * Privacy-first analytics opt-in control.
     * When false (the default), zero analytics events are recorded or stored.
     */
    fun setAnalyticsOptIn(optIn: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS_OPT_IN, optIn).apply()
        _settings.value = _settings.value.copy(analyticsOptIn = optIn)
        if (!optIn) {
            _recordedAnalyticsEvents.value = emptyList()
        }
    }

    fun trackAnalyticsEvent(eventName: String): Boolean {
        if (!_settings.value.analyticsOptIn) {
            return false // Strictly no analytics unless user opts in
        }
        _recordedAnalyticsEvents.value = _recordedAnalyticsEvents.value + eventName
        return true
    }

    fun setHomeSortOrder(order: HomeSortOrder) {
        prefs.edit().putString(KEY_HOME_SORT_ORDER, order.name).apply()
        _settings.value = _settings.value.copy(homeSortOrder = order)
    }

    fun addCustomFolder(folderName: String): Boolean {
        val trimmed = folderName.trim().replace("|", "")
        if (trimmed.isEmpty()) return false
        val current = _settings.value.customFolders
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        val updated = current + trimmed
        prefs.edit().putString(KEY_CUSTOM_FOLDERS, updated.joinToString("|")).apply()
        _settings.value = _settings.value.copy(customFolders = updated)
        return true
    }

    fun removeCustomFolder(folderName: String) {
        val updated = _settings.value.customFolders.filterNot { it.equals(folderName, ignoreCase = true) }
        prefs.edit().putString(KEY_CUSTOM_FOLDERS, updated.joinToString("|")).apply()
        _settings.value = _settings.value.copy(customFolders = updated)
    }

    fun updateProfileSnippets(
        fullName: String,
        initials: String,
        email: String,
        phone: String,
        address: String
    ) {
        prefs.edit()
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_INITIALS, initials)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ADDRESS, address)
            .apply()
        _settings.value = _settings.value.copy(
            userFullName = fullName,
            userInitials = initials,
            userEmail = email,
            userPhone = phone,
            userAddress = address
        )
    }

    // ==========================================
    // AI Consent, Toggle & Daily Limit Management
    // ==========================================
    fun setAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(aiEnabled = enabled)
    }

    fun setAiConsentGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_AI_CONSENT_GRANTED, granted).apply()
        _settings.value = _settings.value.copy(
            aiConsentGranted = granted,
            aiEnabled = if (granted) true else _settings.value.aiEnabled
        )
    }

    fun setAiDailyLimit(limit: Int) {
        val safeLimit = limit.coerceIn(1, 500)
        prefs.edit().putInt(KEY_AI_DAILY_LIMIT, safeLimit).apply()
        _settings.value = _settings.value.copy(aiDailyLimit = safeLimit)
    }

    fun refreshDailyCounterIfNeeded() {
        val today = currentEpochDay()
        if (_settings.value.aiUsageDateEpochDay != today) {
            prefs.edit()
                .putLong(KEY_AI_USAGE_EPOCH_DAY, today)
                .putInt(KEY_AI_REQUESTS_USED_TODAY, 0)
                .apply()
            _settings.value = _settings.value.copy(
                aiRequestsUsedToday = 0,
                aiUsageDateEpochDay = today
            )
        }
    }

    fun canMakeAiRequest(): Boolean {
        refreshDailyCounterIfNeeded()
        val current = _settings.value
        return current.aiEnabled && current.aiConsentGranted && current.aiRequestsUsedToday < current.aiDailyLimit
    }

    fun recordAiRequestUsed(): Boolean {
        refreshDailyCounterIfNeeded()
        val current = _settings.value
        if (!current.aiEnabled || !current.aiConsentGranted) return false
        if (current.aiRequestsUsedToday >= current.aiDailyLimit) return false
        val newUsed = current.aiRequestsUsedToday + 1
        val today = currentEpochDay()
        prefs.edit()
            .putLong(KEY_AI_USAGE_EPOCH_DAY, today)
            .putInt(KEY_AI_REQUESTS_USED_TODAY, newUsed)
            .apply()
        _settings.value = current.copy(
            aiRequestsUsedToday = newUsed,
            aiUsageDateEpochDay = today
        )
        return true
    }

    fun resetAiDailyUsageCounter() {
        val today = currentEpochDay()
        prefs.edit()
            .putLong(KEY_AI_USAGE_EPOCH_DAY, today)
            .putInt(KEY_AI_REQUESTS_USED_TODAY, 0)
            .apply()
        _settings.value = _settings.value.copy(
            aiRequestsUsedToday = 0,
            aiUsageDateEpochDay = today
        )
    }

    companion object {
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_APP_LANGUAGE = "pref_app_language"
        private const val KEY_READING_MODE = "pref_reading_mode"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        private const val KEY_TTS_SPEED = "pref_tts_speed"
        private const val KEY_FULL_NAME = "pref_full_name"
        private const val KEY_INITIALS = "pref_initials"
        private const val KEY_EMAIL = "pref_email"
        private const val KEY_PHONE = "pref_phone"
        private const val KEY_ADDRESS = "pref_address"

        private const val KEY_AI_ENABLED = "pref_ai_enabled"
        private const val KEY_AI_CONSENT_GRANTED = "pref_ai_consent_granted"
        private const val KEY_AI_DAILY_LIMIT = "pref_ai_daily_limit"
        private const val KEY_AI_REQUESTS_USED_TODAY = "pref_ai_requests_used_today"
        private const val KEY_AI_USAGE_EPOCH_DAY = "pref_ai_usage_epoch_day"

        private const val KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed"
        private const val KEY_LOW_MEMORY_MODE = "pref_low_memory_mode"
        private const val KEY_ANALYTICS_OPT_IN = "pref_analytics_opt_in"
        private const val KEY_CUSTOM_FOLDERS = "pref_custom_folders"
        private const val KEY_HOME_SORT_ORDER = "pref_home_sort_order"

        /**
         * Detects low-RAM devices via ActivityManager.isLowRamDevice and memoryClass.
         */
        fun detectDeviceMemoryInfo(context: Context): DeviceMemoryInfo {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val isLowRam = am?.isLowRamDevice ?: false
                val memClass = am?.memoryClass ?: 256
                val largeMemClass = am?.largeMemoryClass ?: memClass
                DeviceMemoryInfo(
                    isLowRamDevice = isLowRam,
                    memoryClassMb = memClass,
                    largeMemoryClassMb = largeMemClass
                )
            } catch (_: Exception) {
                DeviceMemoryInfo(
                    isLowRamDevice = false,
                    memoryClassMb = 256,
                    largeMemoryClassMb = 512
                )
            }
        }

        /**
         * Creates a localized Context for a given AppLanguage so string resources
         * resolve in English, Bengali, or System locale on Android 8.0+ (API 26+).
         */
        fun createLocalizedContext(baseContext: Context, language: AppLanguage): Context {
            if (language == AppLanguage.SYSTEM || language.localeTag.isBlank()) {
                return baseContext
            }
            val locale = Locale.forLanguageTag(language.localeTag)
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return baseContext.createConfigurationContext(config)
        }
    }
}
