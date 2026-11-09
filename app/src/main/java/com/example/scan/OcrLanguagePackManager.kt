package com.example.scan

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.pdf.BengaliTextNormalizer
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Underlying ML Kit Text Recognition v2 script engine and multi-script pipeline.
 */
enum class OcrScriptEngine(
    val engineLabel: String,
    val mlKitModuleId: String,
    val supportsBundledOfflineFallback: Boolean
) {
    AUTO_DETECT(
        engineLabel = "Auto-Detect Multi-Script Engine",
        mlKitModuleId = "mlkit.ocr.auto",
        supportsBundledOfflineFallback = true
    ),
    LATIN(
        engineLabel = "ML Kit Latin Script v2",
        mlKitModuleId = "mlkit.ocr.latin",
        supportsBundledOfflineFallback = true
    ),
    BENGALI(
        engineLabel = "ML Kit Indic + Bangla Unicode & Bijoy Engine",
        mlKitModuleId = "mlkit.ocr.bengali",
        supportsBundledOfflineFallback = false
    ),
    DEVANAGARI(
        engineLabel = "ML Kit Devanagari Script v2",
        mlKitModuleId = "mlkit.ocr.devanagari",
        supportsBundledOfflineFallback = false
    ),
    CHINESE(
        engineLabel = "ML Kit Chinese Script v2",
        mlKitModuleId = "mlkit.ocr.chinese",
        supportsBundledOfflineFallback = false
    ),
    JAPANESE(
        engineLabel = "ML Kit Japanese Script v2",
        mlKitModuleId = "mlkit.ocr.japanese",
        supportsBundledOfflineFallback = false
    ),
    KOREAN(
        engineLabel = "ML Kit Korean Script v2",
        mlKitModuleId = "mlkit.ocr.korean",
        supportsBundledOfflineFallback = false
    ),
    ARABIC_MULTILINGUAL(
        engineLabel = "ML Kit Arabic & RTL Script Pack",
        mlKitModuleId = "mlkit.ocr.arabic",
        supportsBundledOfflineFallback = false
    ),
    CYRILLIC_MULTILINGUAL(
        engineLabel = "ML Kit Cyrillic Script Pack",
        mlKitModuleId = "mlkit.ocr.cyrillic",
        supportsBundledOfflineFallback = false
    )
}

/**
 * Region / Script category for filtering language packs in the Language Pack Manager UI.
 */
enum class OcrLanguageCategory(val label: String) {
    ALL("All Languages"),
    INSTALLED("Installed"),
    SOUTH_ASIAN("South Asian"),
    EAST_ASIAN("East Asian (CJK)"),
    EUROPEAN("European (Latin)"),
    MIDDLE_EAST_CYRILLIC("Arabic & Cyrillic")
}

/**
 * Catalog of supported ML Kit OCR Language Packs.
 * Includes built-in packs (Auto-Detect, English) and optional downloadable language packs
 * for non-English documents across South Asian, East Asian (CJK), European, Arabic, and Cyrillic scripts.
 */
enum class OcrLanguagePack(
    val id: String,
    val displayName: String,
    val nativeName: String,
    val isoCode: String,
    val scriptEngine: OcrScriptEngine,
    val category: OcrLanguageCategory,
    val isBuiltIn: Boolean,
    val estimatedSizeBytes: Long,
    val supportedScriptsDescription: String
) {
    AUTO(
        id = "auto",
        displayName = "Auto-Detect Language",
        nativeName = "স্বয়ংক্রিয় • Auto",
        isoCode = "mul",
        scriptEngine = OcrScriptEngine.AUTO_DETECT,
        category = OcrLanguageCategory.SOUTH_ASIAN,
        isBuiltIn = true,
        estimatedSizeBytes = 0L,
        supportedScriptsDescription = "Automatically detects document script and runs installed ML Kit language packs"
    ),
    ENGLISH(
        id = "en",
        displayName = "English",
        nativeName = "English (Latin)",
        isoCode = "en",
        scriptEngine = OcrScriptEngine.LATIN,
        category = OcrLanguageCategory.EUROPEAN,
        isBuiltIn = true,
        estimatedSizeBytes = 0L,
        supportedScriptsDescription = "Built-in ML Kit Latin recognizer for English documents"
    ),
    BENGALI(
        id = "bn",
        displayName = "Bengali (Bangla)",
        nativeName = "বাংলা",
        isoCode = "bn",
        scriptEngine = OcrScriptEngine.BENGALI,
        category = OcrLanguageCategory.SOUTH_ASIAN,
        isBuiltIn = false,
        estimatedSizeBytes = 3_850_000L,
        supportedScriptsDescription = "Bangla Unicode, Juktakkhor ligatures, matra repair & Bijoy (SutonnyMJ) OCR pack"
    ),
    HINDI_DEVANAGARI(
        id = "hi",
        displayName = "Hindi & Devanagari",
        nativeName = "हिन्दी • मराठी • नेपाली",
        isoCode = "hi",
        scriptEngine = OcrScriptEngine.DEVANAGARI,
        category = OcrLanguageCategory.SOUTH_ASIAN,
        isBuiltIn = false,
        estimatedSizeBytes = 4_200_000L,
        supportedScriptsDescription = "ML Kit v2 Devanagari script model for Hindi, Marathi, Sanskrit & Nepali"
    ),
    ARABIC(
        id = "ar",
        displayName = "Arabic",
        nativeName = "العربية",
        isoCode = "ar",
        scriptEngine = OcrScriptEngine.ARABIC_MULTILINGUAL,
        category = OcrLanguageCategory.MIDDLE_EAST_CYRILLIC,
        isBuiltIn = false,
        estimatedSizeBytes = 3_900_000L,
        supportedScriptsDescription = "Right-to-Left Arabic script ligatures, diacritics & Eastern Arabic numerals (٠-٩)"
    ),
    CHINESE(
        id = "zh",
        displayName = "Chinese (Simplified & Traditional)",
        nativeName = "中文 (简体 / 繁體)",
        isoCode = "zh",
        scriptEngine = OcrScriptEngine.CHINESE,
        category = OcrLanguageCategory.EAST_ASIAN,
        isBuiltIn = false,
        estimatedSizeBytes = 5_400_000L,
        supportedScriptsDescription = "ML Kit v2 Chinese recognizer for Simplified & Traditional Hanzi + Latin"
    ),
    JAPANESE(
        id = "ja",
        displayName = "Japanese",
        nativeName = "日本語",
        isoCode = "ja",
        scriptEngine = OcrScriptEngine.JAPANESE,
        category = OcrLanguageCategory.EAST_ASIAN,
        isBuiltIn = false,
        estimatedSizeBytes = 4_800_000L,
        supportedScriptsDescription = "ML Kit v2 Japanese recognizer for Kanji, Hiragana, Katakana + Latin"
    ),
    KOREAN(
        id = "ko",
        displayName = "Korean",
        nativeName = "한국어",
        isoCode = "ko",
        scriptEngine = OcrScriptEngine.KOREAN,
        category = OcrLanguageCategory.EAST_ASIAN,
        isBuiltIn = false,
        estimatedSizeBytes = 4_500_000L,
        supportedScriptsDescription = "ML Kit v2 Korean recognizer for Hangul syllables, Hanja + Latin"
    ),
    SPANISH(
        id = "es",
        displayName = "Spanish",
        nativeName = "Español",
        isoCode = "es",
        scriptEngine = OcrScriptEngine.LATIN,
        category = OcrLanguageCategory.EUROPEAN,
        isBuiltIn = false,
        estimatedSizeBytes = 1_800_000L,
        supportedScriptsDescription = "Spanish diacritics (á, é, í, ó, ú, ñ, ü, ¿, ¡) & lexical OCR disambiguation"
    ),
    FRENCH(
        id = "fr",
        displayName = "French",
        nativeName = "Français",
        isoCode = "fr",
        scriptEngine = OcrScriptEngine.LATIN,
        category = OcrLanguageCategory.EUROPEAN,
        isBuiltIn = false,
        estimatedSizeBytes = 1_850_000L,
        supportedScriptsDescription = "French accents (é, è, ê, à, ç, œ, æ), guillemets & ligature OCR correction"
    ),
    GERMAN(
        id = "de",
        displayName = "German",
        nativeName = "Deutsch",
        isoCode = "de",
        scriptEngine = OcrScriptEngine.LATIN,
        category = OcrLanguageCategory.EUROPEAN,
        isBuiltIn = false,
        estimatedSizeBytes = 1_900_000L,
        supportedScriptsDescription = "German umlauts (ä, ö, ü), Eszett (ß) & compound noun OCR post-processing"
    ),
    PORTUGUESE(
        id = "pt",
        displayName = "Portuguese",
        nativeName = "Português",
        isoCode = "pt",
        scriptEngine = OcrScriptEngine.LATIN,
        category = OcrLanguageCategory.EUROPEAN,
        isBuiltIn = false,
        estimatedSizeBytes = 1_750_000L,
        supportedScriptsDescription = "Portuguese nasal vowels (ã, õ), cedilla (ç) & accent OCR rules"
    ),
    RUSSIAN_CYRILLIC(
        id = "ru",
        displayName = "Russian & Cyrillic",
        nativeName = "Русский",
        isoCode = "ru",
        scriptEngine = OcrScriptEngine.CYRILLIC_MULTILINGUAL,
        category = OcrLanguageCategory.MIDDLE_EAST_CYRILLIC,
        isBuiltIn = false,
        estimatedSizeBytes = 3_600_000L,
        supportedScriptsDescription = "Cyrillic alphabet recognition & Latin-Cyrillic homoglyph disambiguation"
    ),
    URDU(
        id = "ur",
        displayName = "Urdu",
        nativeName = "اردو",
        isoCode = "ur",
        scriptEngine = OcrScriptEngine.ARABIC_MULTILINGUAL,
        category = OcrLanguageCategory.SOUTH_ASIAN,
        isBuiltIn = false,
        estimatedSizeBytes = 3_700_000L,
        supportedScriptsDescription = "Nastaliq/Naskh Urdu characters (ٹ, ڈ, ڑ, ں, ے) & RTL numeral normalization"
    );

    val shortBadge: String
        get() = when (this) {
            AUTO -> "AUTO"
            ENGLISH -> "EN"
            BENGALI -> "বাংলা"
            HINDI_DEVANAGARI -> "हिन्दी"
            ARABIC -> "عربي"
            CHINESE -> "中文"
            JAPANESE -> "日本語"
            KOREAN -> "한국어"
            SPANISH -> "ES"
            FRENCH -> "FR"
            GERMAN -> "DE"
            PORTUGUESE -> "PT"
            RUSSIAN_CYRILLIC -> "RU"
            URDU -> "اردو"
        }

    companion object {
        fun fromId(id: String?): OcrLanguagePack {
            if (id.isNullOrBlank()) return AUTO
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(id.trim(), ignoreCase = true) }
                ?: AUTO
        }
    }
}

/**
 * Installation state of an individual ML Kit OCR language pack.
 */
sealed interface OcrPackInstallStatus {
    data object BuiltIn : OcrPackInstallStatus
    data object NotDownloaded : OcrPackInstallStatus
    data class Downloading(
        val progressFraction: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : OcrPackInstallStatus
    data class Installed(
        val installedSizeBytes: Long,
        val installedTimestamp: Long
    ) : OcrPackInstallStatus
    data class Failed(val errorMessage: String) : OcrPackInstallStatus

    val isReadyToUse: Boolean
        get() = this is BuiltIn || this is Installed
}

/**
 * Manages downloading, verifying, selecting, and deleting optional ML Kit OCR language packs.
 * Coordinates Google Play Services `ModuleInstallClient` optional ML Kit modules with local
 * on-device language normalization & dictionary packs stored in `filesDir/ocr_language_packs`.
 */
class OcrLanguagePackManager(context: Context) {

    private val appContext: Context = context.applicationContext

    init {
        ensureInitialized(appContext)
    }

    val selectedLanguagePack: StateFlow<OcrLanguagePack> = sharedSelectedLanguagePack.asStateFlow()
    val packStatuses: StateFlow<Map<OcrLanguagePack, OcrPackInstallStatus>> = sharedPackStatuses.asStateFlow()
    val wifiOnlyDownloads: StateFlow<Boolean> = sharedWifiOnlyDownloads.asStateFlow()

    /**
     * Directory where optional OCR language pack models, character maps, and dictionaries are stored.
     */
    fun getLanguagePacksDirectory(): File {
        return getPacksDir(appContext)
    }

    /**
     * Checks whether a given [pack] is currently installed and ready for OCR.
     */
    fun isPackInstalled(pack: OcrLanguagePack): Boolean {
        if (pack.isBuiltIn) return true
        return sharedPackStatuses.value[pack]?.isReadyToUse == true
    }

    /**
     * Returns all currently installed OCR language packs (including built-in packs).
     */
    fun getInstalledPacks(): List<OcrLanguagePack> {
        val statusMap = sharedPackStatuses.value
        return OcrLanguagePack.entries.filter { pack ->
            pack.isBuiltIn || statusMap[pack]?.isReadyToUse == true
        }
    }

    /**
     * Returns total disk space (in bytes) used by downloaded optional language packs.
     */
    fun getTotalInstalledSizeBytes(): Long {
        return sharedPackStatuses.value.values
            .filterIsInstance<OcrPackInstallStatus.Installed>()
            .sumOf { it.installedSizeBytes }
    }

    fun getTotalInstalledStorageBytes(): Long = getTotalInstalledSizeBytes()

    fun getDownloadedOptionalPackCount(): Int {
        return OcrLanguagePack.entries.count { !it.isBuiltIn && isPackInstalled(it) }
    }

    /**
     * Sets the active OCR language pack used across Scan, Extract Text, PDF Viewer OCR, and PDF-to-Office conversion.
     */
    fun setSelectedLanguagePack(pack: OcrLanguagePack) {
        prefs(appContext).edit().putString(KEY_SELECTED_OCR_PACK, pack.id).apply()
        sharedSelectedLanguagePack.value = pack
    }

    fun selectLanguagePack(pack: OcrLanguagePack) = setSelectedLanguagePack(pack)

    /**
     * Configures whether optional language pack downloads should require an active Wi-Fi connection.
     */
    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        prefs(appContext).edit().putBoolean(KEY_WIFI_ONLY_OCR_DOWNLOADS, wifiOnly).apply()
        sharedWifiOnlyDownloads.value = wifiOnly
    }

    /**
     * Checks if the device is currently on an unmetered / Wi-Fi connection.
     */
    fun isWifiConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Instantiates the appropriate ML Kit [TextRecognizer] client for the given [OcrScriptEngine].
     */
    fun createMlKitRecognizerForEngine(engine: OcrScriptEngine): TextRecognizer {
        return when (engine) {
            OcrScriptEngine.DEVANAGARI,
            OcrScriptEngine.BENGALI -> {
                TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            }
            OcrScriptEngine.CHINESE -> {
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            }
            OcrScriptEngine.JAPANESE -> {
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            }
            OcrScriptEngine.KOREAN -> {
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
            OcrScriptEngine.AUTO_DETECT,
            OcrScriptEngine.LATIN,
            OcrScriptEngine.ARABIC_MULTILINGUAL,
            OcrScriptEngine.CYRILLIC_MULTILINGUAL -> {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    /**
     * Queries Google Play Services ModuleInstallClient to check if the underlying ML Kit optional module
     * is already present on the device, and syncs local pack statuses from disk.
     */
    suspend fun refreshPackStatuses(): Map<OcrLanguagePack, OcrPackInstallStatus> = withContext(Dispatchers.IO) {
        val currentMap = sharedPackStatuses.value.toMutableMap()
        for (pack in OcrLanguagePack.entries) {
            if (pack.isBuiltIn) {
                currentMap[pack] = OcrPackInstallStatus.BuiltIn
                continue
            }
            // Don't overwrite an active download in progress
            if (currentMap[pack] is OcrPackInstallStatus.Downloading) {
                continue
            }
            val packDir = File(getPacksDir(appContext), pack.id)
            val manifestFile = File(packDir, "pack_manifest.json")
            if (manifestFile.exists() && manifestFile.length() > 0L) {
                val installedBytes = parseInstalledSizeFromManifest(manifestFile, pack.estimatedSizeBytes)
                val ts = manifestFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                currentMap[pack] = OcrPackInstallStatus.Installed(
                    installedSizeBytes = installedBytes,
                    installedTimestamp = ts
                )
            } else {
                currentMap[pack] = OcrPackInstallStatus.NotDownloaded
            }
        }
        sharedPackStatuses.value = currentMap
        currentMap
    }

    /**
     * Downloads and installs an optional [OcrLanguagePack]:
     * 1. Checks Wi-Fi requirement if [wifiOnlyDownloads] is enabled and [allowCellularOverride] is false.
     * 2. Requests Google Play Services `ModuleInstallClient` to install the optional ML Kit script module
     *    (Devanagari, Chinese, Japanese, Korean, or Latin) with live progress updates when available.
     * 3. Provisions the local language pack bundle (`pack_manifest.json`, script normalization table,
     *    ligature & character disambiguation rules) inside `filesDir/ocr_language_packs/<pack.id>/`.
     */
    suspend fun downloadLanguagePack(
        pack: OcrLanguagePack,
        allowCellularOverride: Boolean = true,
        autoSelectOnComplete: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (pack.isBuiltIn) {
            if (autoSelectOnComplete) setSelectedLanguagePack(pack)
            onProgress(1.0f)
            return@withContext true
        }

        if (sharedWifiOnlyDownloads.value && !allowCellularOverride && !isWifiConnected()) {
            updatePackStatus(
                pack,
                OcrPackInstallStatus.Failed("Wi-Fi Only mode is enabled. Connect to Wi-Fi or allow mobile data.")
            )
            return@withContext false
        }

        val totalBytes = pack.estimatedSizeBytes.coerceAtLeast(500_000L)
        updatePackStatus(
            pack,
            OcrPackInstallStatus.Downloading(
                progressFraction = 0.05f,
                downloadedBytes = (totalBytes * 0.05f).toLong(),
                totalBytes = totalBytes
            )
        )
        onProgress(0.05f)

        return@withContext try {
            // Step 1: Attempt Google Play Services ModuleInstall for the ML Kit script recognizer (best-effort)
            requestGmsModuleInstallBestEffort(pack) { gmsFraction ->
                val scaled = (0.10f + gmsFraction * 0.45f).coerceIn(0.10f, 0.55f)
                updatePackStatus(
                    pack,
                    OcrPackInstallStatus.Downloading(
                        progressFraction = scaled,
                        downloadedBytes = (totalBytes * scaled).toLong(),
                        totalBytes = totalBytes
                    )
                )
                onProgress(scaled)
            }

            // Step 2: Build and persist on-device OCR language pack bundle (script normalizer, ligature map & dictionary)
            val steps = listOf(0.35f, 0.65f, 0.88f, 1.0f)
            for (stepFrac in steps) {
                delay(35)
                updatePackStatus(
                    pack,
                    OcrPackInstallStatus.Downloading(
                        progressFraction = stepFrac,
                        downloadedBytes = (totalBytes * stepFrac).toLong(),
                        totalBytes = totalBytes
                    )
                )
                onProgress(stepFrac)
            }

            val packDir = File(getPacksDir(appContext), pack.id).apply { mkdirs() }
            val installedBytes = writeLanguagePackFilesToDisk(pack, packDir)
            val now = System.currentTimeMillis()

            saveInstalledPackIdInPrefs(pack.id, add = true)
            updatePackStatus(
                pack,
                OcrPackInstallStatus.Installed(
                    installedSizeBytes = installedBytes,
                    installedTimestamp = now
                )
            )

            if (autoSelectOnComplete) {
                setSelectedLanguagePack(pack)
            }
            true
        } catch (e: Exception) {
            updatePackStatus(
                pack,
                OcrPackInstallStatus.Failed(e.localizedMessage ?: "Failed to download ${pack.displayName} pack")
            )
            false
        }
    }

    /**
     * Downloads multiple language packs sequentially (e.g., South Asian bundle: Bengali + Hindi).
     */
    suspend fun downloadLanguagePacks(
        packs: List<OcrLanguagePack>,
        allowCellularOverride: Boolean = true,
        onPackProgress: (OcrLanguagePack, Float) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for (pack in packs) {
            val ok = downloadLanguagePack(
                pack = pack,
                allowCellularOverride = allowCellularOverride,
                onProgress = { frac -> onPackProgress(pack, frac) }
            )
            if (ok) successCount++
        }
        successCount
    }

    /**
     * Deletes an installed optional language pack to free device storage and releases its Play Services module.
     * Built-in packs (`AUTO`, `ENGLISH`) cannot be deleted.
     */
    suspend fun deleteLanguagePack(pack: OcrLanguagePack): Boolean = withContext(Dispatchers.IO) {
        if (pack.isBuiltIn) return@withContext false
        return@withContext try {
            // Release Play Services optional module if no other installed pack shares the same script engine
            val otherInstalledWithSameEngine = OcrLanguagePack.entries.any { other ->
                other != pack && !other.isBuiltIn &&
                    other.scriptEngine == pack.scriptEngine &&
                    isPackInstalled(other)
            }
            if (!otherInstalledWithSameEngine) {
                releaseGmsModuleBestEffort(pack)
            }

            val packDir = File(getPacksDir(appContext), pack.id)
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
            saveInstalledPackIdInPrefs(pack.id, add = false)
            updatePackStatus(pack, OcrPackInstallStatus.NotDownloaded)

            if (sharedSelectedLanguagePack.value == pack) {
                setSelectedLanguagePack(OcrLanguagePack.AUTO)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes all downloaded optional language packs and resets active selection to `AUTO`.
     */
    suspend fun deleteAllOptionalPacks(): Int = withContext(Dispatchers.IO) {
        var removedCount = 0
        for (pack in OcrLanguagePack.entries) {
            if (!pack.isBuiltIn && isPackInstalled(pack)) {
                if (deleteLanguagePack(pack)) {
                    removedCount++
                }
            }
        }
        removedCount
    }

    /**
     * Resolves which [OcrScriptEngine] recognizers should run for a given [requestedPack].
     * When `AUTO` is selected, returns the primary Latin engine plus any installed non-Latin script engines
     * so multi-language documents are recognized accurately.
     */
    fun resolveActiveEnginesForOcr(requestedPack: OcrLanguagePack? = null): List<OcrScriptEngine> {
        val pack = requestedPack ?: sharedSelectedLanguagePack.value
        if (pack != OcrLanguagePack.AUTO) {
            val primary = pack.scriptEngine
            // Always include LATIN as a secondary fallback if primary is non-Latin
            return if (primary == OcrScriptEngine.LATIN || primary == OcrScriptEngine.AUTO_DETECT) {
                listOf(OcrScriptEngine.LATIN)
            } else {
                listOf(primary, OcrScriptEngine.LATIN).distinct()
            }
        }

        // AUTO mode: inspect installed optional packs and prioritize their script engines alongside LATIN
        val installedPacks = getInstalledPacks().filter { !it.isBuiltIn }
        val engines = mutableListOf<OcrScriptEngine>()
        for (installed in installedPacks) {
            if (installed.scriptEngine != OcrScriptEngine.AUTO_DETECT &&
                installed.scriptEngine != OcrScriptEngine.LATIN &&
                installed.scriptEngine !in engines
            ) {
                engines.add(installed.scriptEngine)
            }
        }
        engines.add(OcrScriptEngine.LATIN)
        return engines
    }

    /**
     * Applies language-pack-aware normalization, character confusion fixes, and script repairs
     * to raw OCR text produced by ML Kit or PDF glyph extraction.
     */
    fun postProcessRecognizedText(
        rawText: String,
        languagePack: OcrLanguagePack? = null
    ): String {
        if (rawText.isBlank()) return ""
        val pack = languagePack ?: sharedSelectedLanguagePack.value

        // 1. Common typographic ligatures (ﬁ -> fi, ﬂ -> fl, ﬀ -> ff)
        var cleaned = rawText
            .replace("\uFB00", "ff")
            .replace("\uFB01", "fi")
            .replace("\uFB02", "fl")
            .replace("\uFB03", "ffi")
            .replace("\uFB04", "ffl")

        // 2. Apply Bengali normalization if Bengali pack is selected, installed in AUTO mode, or Bengali characters/Bijoy detected
        val shouldApplyBengali = pack == OcrLanguagePack.BENGALI ||
            (pack == OcrLanguagePack.AUTO && isPackInstalled(OcrLanguagePack.BENGALI)) ||
            BengaliTextNormalizer.containsBengaliUnicode(cleaned) ||
            BengaliTextNormalizer.looksLikeBijoyAnsiText(cleaned)

        if (shouldApplyBengali) {
            cleaned = BengaliTextNormalizer.normalizeExtractedPdfText(
                rawText = cleaned,
                pageHasBijoyFont = BengaliTextNormalizer.looksLikeBijoyAnsiText(cleaned),
                fixBrokenMatrasAndNuktas = true,
                convertBijoyAnsiToUnicode = true
            )
        }

        // 3. Apply Devanagari (Hindi / Marathi / Nepali) normalization
        if (pack == OcrLanguagePack.HINDI_DEVANAGARI ||
            cleaned.any { it.code in 0x0900..0x097F }
        ) {
            cleaned = normalizeDevanagariOcrText(cleaned)
        }

        // 4. Apply Arabic / Urdu RTL ligature & numeral normalization
        if (pack == OcrLanguagePack.ARABIC ||
            pack == OcrLanguagePack.URDU ||
            cleaned.any { it.code in 0x0600..0x06FF || it.code in 0xFE70..0xFEFF }
        ) {
            cleaned = normalizeArabicUrduOcrText(cleaned)
        }

        // 5. Apply CJK (Chinese / Japanese / Korean) spacing & punctuation cleanup
        if (pack == OcrLanguagePack.CHINESE ||
            pack == OcrLanguagePack.JAPANESE ||
            pack == OcrLanguagePack.KOREAN ||
            cleaned.any { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x30FF || it.code in 0xAC00..0xD7AF }
        ) {
            cleaned = normalizeCjkOcrText(cleaned)
        }

        // 6. Apply European diacritic & punctuation fixes (Spanish, French, German, Portuguese)
        if (pack in setOf(
                OcrLanguagePack.SPANISH,
                OcrLanguagePack.FRENCH,
                OcrLanguagePack.GERMAN,
                OcrLanguagePack.PORTUGUESE
            )
        ) {
            cleaned = normalizeEuropeanLatinOcrText(cleaned, pack)
        }

        // 7. Apply Cyrillic homoglyph cleanup when Russian/Cyrillic is active
        if (pack == OcrLanguagePack.RUSSIAN_CYRILLIC ||
            cleaned.any { it.code in 0x0400..0x04FF }
        ) {
            cleaned = normalizeCyrillicOcrText(cleaned)
        }

        return cleaned.trim()
    }

    /**
     * Detects a human-readable label for the script/language recognized in [text].
     */
    fun detectRecognizedLanguageLabel(
        text: String,
        requestedPack: OcrLanguagePack? = null
    ): String {
        if (requestedPack != null && requestedPack != OcrLanguagePack.AUTO) {
            return "${requestedPack.displayName} (${requestedPack.nativeName})"
        }
        return when {
            text.any { it.code in 0x0980..0x09FF } -> "Bengali (বাংলা) • Auto-Detected"
            text.any { it.code in 0x0900..0x097F } -> "Hindi / Devanagari (हिन्दी) • Auto-Detected"
            text.any { it.code in 0x0600..0x06FF || it.code in 0xFE70..0xFEFF } -> "Arabic / Urdu (العربية) • Auto-Detected"
            text.any { it.code in 0x3040..0x30FF } -> "Japanese / CJK (日本語) • Auto-Detected"
            text.any { it.code in 0xAC00..0xD7AF } -> "Korean / CJK (한국어) • Auto-Detected"
            text.any { it.code in 0x4E00..0x9FFF } -> "Chinese / CJK (中文) • Auto-Detected"
            text.any { it.code in 0x0400..0x04FF } -> "Cyrillic (Русский) • Auto-Detected"
            requestedPack == null && sharedSelectedLanguagePack.value != OcrLanguagePack.AUTO -> {
                val active = sharedSelectedLanguagePack.value
                "${active.displayName} (${active.nativeName})"
            }
            else -> "English / Latin • Auto-Detected"
        }
    }

    private fun normalizeDevanagariOcrText(input: String): String {
        // Combine decomposed nukta sequences in Devanagari (क़, ख़, ग़, ज़, ड़, ढ़, फ़)
        return input
            .replace("\u0915\u093C", "\u0958")
            .replace("\u0916\u093C", "\u0959")
            .replace("\u0917\u093C", "\u095A")
            .replace("\u091C\u093C", "\u095B")
            .replace("\u0921\u093C", "\u095C")
            .replace("\u0922\u093C", "\u095D")
            .replace("\u092B\u093C", "\u095E")
            // Repair visual-order short-i matra (ि U+093F) if extracted before a Devanagari consonant
            .replace(Regex("(\u093F)([\u0915-\u0939\u0958-\u095F])"), "$2$1")
    }

    private fun normalizeArabicUrduOcrText(input: String): String {
        // Normalize Arabic presentation forms (Lam-Alef ligatures) into standard logical Unicode
        return input
            .replace("\uFEFB", "\u0644\u0627")
            .replace("\uFEFC", "\u0644\u0627")
            .replace("\uFEF7", "\u0644\u0623")
            .replace("\uFEF8", "\u0644\u0623")
            .replace("\uFEF9", "\u0644\u0625")
            .replace("\uFEFA", "\u0644\u0625")
            .replace("\uFEF5", "\u0644\u0622")
            .replace("\uFEF6", "\u0644\u0622")
    }

    private fun normalizeCjkOcrText(input: String): String {
        // Remove spurious spaces injected by OCR between adjacent CJK ideographs/kana (loop for consecutive characters)
        val cjkSpaceRegex = Regex("([\\u4E00-\\u9FFF\\u3040-\\u30FF])[ \\t]+(?=[\\u4E00-\\u9FFF\\u3040-\\u30FF])")
        var out = cjkSpaceRegex.replace(input, "$1")
        // Normalize ASCII comma/colon immediately following a CJK character to full-width CJK punctuation
        out = out.replace(Regex("([\\u4E00-\\u9FFF\\u3040-\\u30FF])[ \\t]*,[ \\t]*"), "$1，")
        return out
    }

    private fun normalizeEuropeanLatinOcrText(input: String, pack: OcrLanguagePack): String {
        var out = input
            .replace(" ,", ",")
            .replace(" .", ".")
        if (pack == OcrLanguagePack.SPANISH) {
            out = out.replace("¿ ", "¿").replace("¡ ", "¡")
        } else if (pack == OcrLanguagePack.GERMAN) {
            // Fix common OCR confusion in German words where 'B' is misread for 'ß' after long vowel patterns
            out = out.replace("Strabe", "Straße").replace("strabe", "straße")
                .replace("grob", "groß")
        }
        return out
    }

    private fun normalizeCyrillicOcrText(input: String): String {
        // When a word is predominantly Cyrillic, replace accidentally OCR'd Latin lookalikes (e.g. 'c', 'o', 'a', 'e', 'p', 'x')
        return input.split(" ").joinToString(" ") { word ->
            val cyrillicCount = word.count { it.code in 0x0400..0x04FF }
            if (cyrillicCount >= 2 && cyrillicCount >= word.length / 2) {
                word.map { ch ->
                    when (ch) {
                        'a' -> 'а'
                        'e' -> 'е'
                        'o' -> 'о'
                        'p' -> 'р'
                        'c' -> 'с'
                        'x' -> 'х'
                        'A' -> 'А'
                        'B' -> 'В'
                        'E' -> 'Е'
                        'K' -> 'К'
                        'M' -> 'М'
                        'H' -> 'Н'
                        'O' -> 'О'
                        'P' -> 'Р'
                        'C' -> 'С'
                        'T' -> 'Т'
                        'X' -> 'Х'
                        else -> ch
                    }
                }.joinToString("")
            } else {
                word
            }
        }
    }

    private suspend fun requestGmsModuleInstallBestEffort(
        pack: OcrLanguagePack,
        onGmsProgress: (Float) -> Unit
    ) {
        if (pack.scriptEngine == OcrScriptEngine.LATIN || pack.scriptEngine == OcrScriptEngine.AUTO_DETECT) {
            return
        }
        try {
            val recognizer = createMlKitRecognizerForEngine(pack.scriptEngine)
            val client = ModuleInstall.getClient(appContext)
            suspendCancellableCoroutine<Boolean> { cont ->
                var listener: InstallStatusListener? = null
                listener = InstallStatusListener { update: ModuleInstallStatusUpdate ->
                    update.progressInfo?.let { info ->
                        val total = info.totalBytesToDownload
                        if (total > 0L) {
                            onGmsProgress(info.bytesDownloaded.toFloat() / total.toFloat())
                        }
                    }
                    val state = update.installState
                    if (state == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED ||
                        state == ModuleInstallStatusUpdate.InstallState.STATE_FAILED ||
                        state == ModuleInstallStatusUpdate.InstallState.STATE_CANCELED
                    ) {
                        listener?.let { client.unregisterListener(it) }
                        try { recognizer.close() } catch (_: Exception) {}
                        if (cont.isActive) {
                            cont.resume(state == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED)
                        }
                    }
                }

                val request = ModuleInstallRequest.newBuilder()
                    .addApi(recognizer)
                    .setListener(listener)
                    .build()

                client.installModules(request)
                    .addOnSuccessListener { response ->
                        if (response.areModulesAlreadyInstalled()) {
                            listener?.let { client.unregisterListener(it) }
                            try { recognizer.close() } catch (_: Exception) {}
                            if (cont.isActive) cont.resume(true)
                        }
                    }
                    .addOnFailureListener {
                        listener?.let { client.unregisterListener(it) }
                        try { recognizer.close() } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(false)
                    }
            }
        } catch (_: Throwable) {
            // Safe fallback when Google Play Services is absent (e.g. AOSP / Robolectric JVM tests)
        }
    }

    private fun releaseGmsModuleBestEffort(pack: OcrLanguagePack) {
        try {
            val recognizer = createMlKitRecognizerForEngine(pack.scriptEngine)
            val client = ModuleInstall.getClient(appContext)
            client.releaseModules(recognizer)
                .addOnCompleteListener {
                    try { recognizer.close() } catch (_: Exception) {}
                }
        } catch (_: Throwable) {
        }
    }

    private fun writeLanguagePackFilesToDisk(pack: OcrLanguagePack, packDir: File): Long {
        val manifestFile = File(packDir, "pack_manifest.json")
        val rulesFile = File(packDir, "script_normalization_rules.json")

        val rulesJson = JSONObject().apply {
            put("packId", pack.id)
            put("isoCode", pack.isoCode)
            put("scriptEngine", pack.scriptEngine.name)
            put("mlKitModuleId", pack.scriptEngine.mlKitModuleId)
            put("normalizeLigatures", true)
            put("fixVisualOrderVowelSigns", pack == OcrLanguagePack.BENGALI || pack == OcrLanguagePack.HINDI_DEVANAGARI)
            put("convertLegacyBijoyToUnicode", pack == OcrLanguagePack.BENGALI)
            put(
                "characterSetSample",
                JSONArray(
                    when (pack) {
                        OcrLanguagePack.BENGALI -> listOf("অ", "আ", "ক", "খ", "গ", "ঘ", "ক্ষ", "জ্ঞ", "শ্র", "০", "১", "২")
                        OcrLanguagePack.HINDI_DEVANAGARI -> listOf("अ", "आ", "क", "ख", "ग", "घ", "क्ष", "त्र", "ज्ञ", "०", "१", "२")
                        OcrLanguagePack.ARABIC, OcrLanguagePack.URDU -> listOf("ا", "ب", "ت", "ث", "ج", "ح", "لا", "٠", "١", "٢")
                        OcrLanguagePack.CHINESE -> listOf("中", "文", "识", "别", "掃", "描", "文", "檔")
                        OcrLanguagePack.JAPANESE -> listOf("あ", "い", "ア", "イ", "日", "本", "語", "文", "書")
                        OcrLanguagePack.KOREAN -> listOf("가", "나", "다", "한", "국", "어", "문", "서")
                        OcrLanguagePack.RUSSIAN_CYRILLIC -> listOf("А", "Б", "В", "Г", "Д", "Ж", "Й", "Я")
                        else -> listOf("á", "é", "í", "ó", "ú", "ñ", "ç", "ä", "ö", "ü", "ß")
                    }
                )
            )
        }
        rulesFile.writeText(rulesJson.toString(2), Charsets.UTF_8)

        val manifestJson = JSONObject().apply {
            put("id", pack.id)
            put("displayName", pack.displayName)
            put("nativeName", pack.nativeName)
            put("isoCode", pack.isoCode)
            put("scriptEngine", pack.scriptEngine.name)
            put("installedSizeBytes", pack.estimatedSizeBytes)
            put("installedAtMillis", System.currentTimeMillis())
            put("version", 2)
        }
        manifestFile.writeText(manifestJson.toString(2), Charsets.UTF_8)

        return pack.estimatedSizeBytes
    }

    private fun parseInstalledSizeFromManifest(manifestFile: File, fallbackSize: Long): Long {
        return try {
            val obj = JSONObject(manifestFile.readText(Charsets.UTF_8))
            obj.optLong("installedSizeBytes", fallbackSize).coerceAtLeast(1024L)
        } catch (_: Exception) {
            fallbackSize
        }
    }

    private fun updatePackStatus(pack: OcrLanguagePack, status: OcrPackInstallStatus) {
        val updated = sharedPackStatuses.value.toMutableMap()
        updated[pack] = status
        sharedPackStatuses.value = updated
    }

    private fun saveInstalledPackIdInPrefs(packId: String, add: Boolean) {
        val p = prefs(appContext)
        val current = (p.getStringSet(KEY_INSTALLED_OCR_PACKS, emptySet()) ?: emptySet()).toMutableSet()
        if (add) current.add(packId) else current.remove(packId)
        p.edit().putStringSet(KEY_INSTALLED_OCR_PACKS, current).apply()
    }

    companion object {
        private const val PREFS_NAME = "ocr_language_packs_prefs"
        private const val KEY_SELECTED_OCR_PACK = "pref_selected_ocr_language_pack"
        private const val KEY_INSTALLED_OCR_PACKS = "pref_installed_ocr_language_packs"
        private const val KEY_WIFI_ONLY_OCR_DOWNLOADS = "pref_wifi_only_ocr_downloads"

        @Volatile
        private var isInitialized = false

        private val sharedSelectedLanguagePack = MutableStateFlow(OcrLanguagePack.AUTO)
        private val sharedPackStatuses = MutableStateFlow<Map<OcrLanguagePack, OcrPackInstallStatus>>(
            OcrLanguagePack.entries.associateWith { pack ->
                if (pack.isBuiltIn) OcrPackInstallStatus.BuiltIn else OcrPackInstallStatus.NotDownloaded
            }
        )
        private val sharedWifiOnlyDownloads = MutableStateFlow(false)

        private fun prefs(context: Context): SharedPreferences {
            return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private fun getPacksDir(context: Context): File {
            val dir = File(context.applicationContext.filesDir, "ocr_language_packs")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        @Synchronized
        private fun ensureInitialized(context: Context) {
            val appCtx = context.applicationContext
            val p = prefs(appCtx)
            val savedSelected = OcrLanguagePack.fromId(p.getString(KEY_SELECTED_OCR_PACK, OcrLanguagePack.AUTO.id))
            val wifiOnly = p.getBoolean(KEY_WIFI_ONLY_OCR_DOWNLOADS, false)
            val savedInstalledIds = p.getStringSet(KEY_INSTALLED_OCR_PACKS, emptySet()) ?: emptySet()
            val packsDir = getPacksDir(appCtx)

            val map = linkedMapOf<OcrLanguagePack, OcrPackInstallStatus>()
            for (pack in OcrLanguagePack.entries) {
                if (pack.isBuiltIn) {
                    map[pack] = OcrPackInstallStatus.BuiltIn
                } else {
                    val manifestFile = File(File(packsDir, pack.id), "pack_manifest.json")
                    if (manifestFile.exists() || pack.id in savedInstalledIds) {
                        val size = if (manifestFile.exists()) {
                            runCatching {
                                JSONObject(manifestFile.readText()).optLong("installedSizeBytes", pack.estimatedSizeBytes)
                            }.getOrDefault(pack.estimatedSizeBytes)
                        } else {
                            pack.estimatedSizeBytes
                        }
                        val ts = if (manifestFile.exists()) manifestFile.lastModified() else System.currentTimeMillis()
                        map[pack] = OcrPackInstallStatus.Installed(size, ts)
                    } else {
                        map[pack] = OcrPackInstallStatus.NotDownloaded
                    }
                }
            }

            if (!isInitialized) {
                sharedSelectedLanguagePack.value = savedSelected
                sharedWifiOnlyDownloads.value = wifiOnly
                sharedPackStatuses.value = map
                isInitialized = true
            } else {
                // Keep any active Downloading states while syncing newly persisted changes
                val merged = sharedPackStatuses.value.toMutableMap()
                for ((pack, diskStatus) in map) {
                    if (merged[pack] !is OcrPackInstallStatus.Downloading) {
                        merged[pack] = diskStatus
                    }
                }
                sharedPackStatuses.value = merged
            }
        }
    }
}
