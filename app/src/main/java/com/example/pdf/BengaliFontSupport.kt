package com.example.pdf

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/**
 * Catalog of the 26 most popular Bengali (Bangla) fonts used across Bangladesh & India
 * for MS Word (.docx), Excel (.xlsx), PowerPoint (.pptx), RTF, HTML, and Searchable PDFs.
 */
enum class BengaliFontOption(
    val fontFamilyName: String,
    val displayName: String,
    val bengaliSample: String,
    val category: String,
    val fallbackFontName: String,
    @FontRes val bundledFontResId: Int,
    val isSerif: Boolean = false,
    val isLegacyBijoyFamily: Boolean = false
) {
    KALPURUSH(
        fontFamilyName = "Kalpurush",
        displayName = "Kalpurush (কালপুরুষ)",
        bengaliSample = "আমার সোনার বাংলা, আমি তোমায় ভালোবাসি",
        category = "Popular Unicode",
        fallbackFontName = "Noto Sans Bengali",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    SOLAIMAN_LIPI(
        fontFamilyName = "SolaimanLipi",
        displayName = "SolaimanLipi (সোলাইমানলিপি)",
        bengaliSample = "সোলাইমানলিপি — ছাপা ও অফিসের জনপ্রিয় ফন্ট",
        category = "Print & Office",
        fallbackFontName = "Noto Serif Bengali",
        bundledFontResId = R.font.noto_serif_bengali,
        isSerif = true
    ),
    NIKOSH(
        fontFamilyName = "Nikosh",
        displayName = "Nikosh (নিকোশ — সরকারি ফন্ট)",
        bengaliSample = "গণপ্রজাতন্ত্রী বাংলাদেশ সরকার — দাপ্তরিক ফন্ট",
        category = "Official & Gov",
        fallbackFontName = "Noto Serif Bengali",
        bundledFontResId = R.font.noto_serif_bengali,
        isSerif = true
    ),
    NIKOSH_BAN(
        fontFamilyName = "NikoshBAN",
        displayName = "NikoshBAN (নিকোশ ব্যান)",
        bengaliSample = "নিকোশ ব্যান — অফিস ও আদালতের নথিপত্র",
        category = "Official & Gov",
        fallbackFontName = "Nikosh",
        bundledFontResId = R.font.noto_serif_bengali,
        isSerif = true
    ),
    SIYAM_RUPALI(
        fontFamilyName = "Siyam Rupali",
        displayName = "Siyam Rupali (সিয়াম রূপালী)",
        bengaliSample = "সিয়াম রূপালী — স্পষ্ট ও পরিচ্ছন্ন বাংলা লেখা",
        category = "Popular Unicode",
        fallbackFontName = "Noto Sans Bengali",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    NOTO_SANS_BENGALI(
        fontFamilyName = "Noto Sans Bengali",
        displayName = "Noto Sans Bengali (নোটো স্যান্স)",
        bengaliSample = "আধুনিক ও সর্বজনীন ইউনিকোড বাংলা ফন্ট",
        category = "Google & Modern",
        fallbackFontName = "Nirmala UI",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    NOTO_SERIF_BENGALI(
        fontFamilyName = "Noto Serif Bengali",
        displayName = "Noto Serif Bengali (নোটো সেরিফ)",
        bengaliSample = "বই, প্রবন্ধ ও সাহিত্যের ক্লাসিক সেরিফ ফন্ট",
        category = "Print & Office",
        fallbackFontName = "SolaimanLipi",
        bundledFontResId = R.font.noto_serif_bengali,
        isSerif = true
    ),
    HIND_SILIGURI(
        fontFamilyName = "Hind Siliguri",
        displayName = "Hind Siliguri (হিন্দ শিলিগুড়ি)",
        bengaliSample = "হিন্দ শিলিগুড়ি — শিরোনাম ও আধুনিক নকশা",
        category = "Google & Modern",
        fallbackFontName = "Noto Sans Bengali",
        bundledFontResId = R.font.hind_siliguri
    ),
    SUTONNY_MJ(
        fontFamilyName = "SutonnyMJ",
        displayName = "SutonnyMJ (সুতন্বী এমজে — বিজয়)",
        bengaliSample = "সুতন্বী এমজে — প্রকাশনা ও বিজয় টাইপিং",
        category = "Bijoy & Classic",
        fallbackFontName = "SolaimanLipi",
        bundledFontResId = R.font.tiro_bangla,
        isSerif = true,
        isLegacyBijoyFamily = true
    ),
    SUTONNY_OMJ(
        fontFamilyName = "SutonnyOMJ",
        displayName = "SutonnyOMJ (সুতন্বী ওএমজে)",
        bengaliSample = "সুতন্বী ওএমজে — ওপেনটাইপ বাংলা সংস্করণ",
        category = "Bijoy & Classic",
        fallbackFontName = "Kalpurush",
        bundledFontResId = R.font.tiro_bangla,
        isSerif = true
    ),
    VRINDA(
        fontFamilyName = "Vrinda",
        displayName = "Vrinda (বৃন্দা — MS Windows)",
        bengaliSample = "বৃন্দা — মাইক্রোসফট উইন্ডোজ ও ওয়ার্ড ফন্ট",
        category = "MS Office",
        fallbackFontName = "Nirmala UI",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    NIRMALA_UI(
        fontFamilyName = "Nirmala UI",
        displayName = "Nirmala UI (নির্মলা ইউআই)",
        bengaliSample = "নির্মলা ইউআই — উইন্ডোজ ১০/১১ ডিফল্ট বাংলা",
        category = "MS Office",
        fallbackFontName = "Noto Sans Bengali",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    ADARSHA_LIPI(
        fontFamilyName = "AdorshoLipi",
        displayName = "AdorshoLipi (আদর্শলিপি)",
        bengaliSample = "আদর্শলিপি — পাঠ্যবই ও দলিলের ঐতিহ্যবাহী ফন্ট",
        category = "Print & Office",
        fallbackFontName = "SolaimanLipi",
        bundledFontResId = R.font.tiro_bangla,
        isSerif = true
    ),
    APONA_LOHIT(
        fontFamilyName = "AponaLohit",
        displayName = "AponaLohit (আপনা লোহিত)",
        bengaliSample = "আপনা লোহিত — ওপেন সোর্স ইউনিকোড বাংলা",
        category = "Popular Unicode",
        fallbackFontName = "Kalpurush",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    MUKTI(
        fontFamilyName = "Mukti",
        displayName = "Mukti (মুক্তি)",
        bengaliSample = "মুক্তি — ফ্রি বাংলা ফন্ট প্রজেক্টের জনপ্রিয় ফন্ট",
        category = "Popular Unicode",
        fallbackFontName = "Noto Sans Bengali",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    AKAASH(
        fontFamilyName = "Akaash",
        displayName = "Akaash (আকাশ)",
        bengaliSample = "আকাশ — সহজপাঠ্য বাংলা ইউনিকোড ফন্ট",
        category = "Popular Unicode",
        fallbackFontName = "Kalpurush",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    SAGAR(
        fontFamilyName = "Sagar",
        displayName = "Sagar (সাগর)",
        bengaliSample = "সাগর — ক্ল্যাসিক বাংলা টাইপফেস",
        category = "Print & Office",
        fallbackFontName = "Noto Serif Bengali",
        bundledFontResId = R.font.noto_serif_bengali,
        isSerif = true
    ),
    LIKHAN(
        fontFamilyName = "Likhan",
        displayName = "Likhan (লিখন)",
        bengaliSample = "লিখন — চিঠিপত্র ও সাহিত্য রচনার ফন্ট",
        category = "Print & Office",
        fallbackFontName = "SolaimanLipi",
        bundledFontResId = R.font.tiro_bangla,
        isSerif = true
    ),
    EKUSHEY_LOHIT(
        fontFamilyName = "Ekushey Lohit",
        displayName = "Ekushey Lohit (একুশে লোহিত)",
        bengaliSample = "একুশে লোহিত — একুশে ফন্ট কালেকশন",
        category = "Popular Unicode",
        fallbackFontName = "Kalpurush",
        bundledFontResId = R.font.noto_sans_bengali
    ),
    EKUSHEY_PUJA(
        fontFamilyName = "Ekushey Puja",
        displayName = "Ekushey Puja (একুশে পূজা)",
        bengaliSample = "একুশে পূজা — নান্দনিক বাংলা প্রকাশনা ফন্ট",
        category = "Stylish & Display",
        fallbackFontName = "Galada",
        bundledFontResId = R.font.galada
    ),
    TIRO_BANGLA(
        fontFamilyName = "Tiro Bangla",
        displayName = "Tiro Bangla (তিরো বাংলা)",
        bengaliSample = "তিরো বাংলা — মুদ্রণশিল্প ও সাহিত্যের আভিজাত্য",
        category = "Google & Modern",
        fallbackFontName = "Noto Serif Bengali",
        bundledFontResId = R.font.tiro_bangla,
        isSerif = true
    ),
    BALOO_DA_2(
        fontFamilyName = "Baloo Da 2",
        displayName = "Baloo Da 2 (বালু দা ২)",
        bengaliSample = "বালু দা ২ — আকর্ষণীয় শিরোনাম ও পোস্টার ফন্ট",
        category = "Google & Modern",
        fallbackFontName = "Hind Siliguri",
        bundledFontResId = R.font.baloo_da_2
    ),
    ANEK_BANGLA(
        fontFamilyName = "Anek Bangla",
        displayName = "Anek Bangla (অনেক বাংলা)",
        bengaliSample = "অনেক বাংলা — আধুনিক স্যান্স-সেরিফ টাইপফেস",
        category = "Google & Modern",
        fallbackFontName = "Noto Sans Bengali",
        bundledFontResId = R.font.anek_bangla
    ),
    MINA(
        fontFamilyName = "Mina",
        displayName = "Mina (মিনা)",
        bengaliSample = "মিনা — জ্যামিতিক ও আধুনিক বাংলা ফন্ট",
        category = "Google & Modern",
        fallbackFontName = "Hind Siliguri",
        bundledFontResId = R.font.mina
    ),
    GALADA(
        fontFamilyName = "Galada",
        displayName = "Galada (গলাদা — ক্যালিগ্রাফি)",
        bengaliSample = "গলাদা — শৈল্পিক ও অলঙ্কৃত বাংলা লিপি",
        category = "Stylish & Display",
        fallbackFontName = "Noto Serif Bengali",
        bundledFontResId = R.font.galada
    ),
    ATMA(
        fontFamilyName = "Atma",
        displayName = "Atma (আত্মা — হাতের লেখা)",
        bengaliSample = "আত্মা — প্রাণবন্ত হাতের লেখার বাংলা ফন্ট",
        category = "Stylish & Display",
        fallbackFontName = "Baloo Da 2",
        bundledFontResId = R.font.atma
    );

    fun toComposeFontFamily(): FontFamily {
        return try {
            FontFamily(Font(bundledFontResId))
        } catch (_: Exception) {
            FontFamily.Default
        }
    }

    fun loadAndroidTypeface(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, bundledFontResId) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    @android.annotation.SuppressLint("ResourceType")
    fun loadPdfBoxFont(context: Context, pdDocument: PDDocument): PDFont {
        return try {
            context.resources.openRawResource(bundledFontResId).use { stream ->
                PDType0Font.load(pdDocument, stream, true)
            }
        } catch (_: Exception) {
            try {
                context.resources.openRawResource(R.font.noto_sans_bengali).use { stream ->
                    PDType0Font.load(pdDocument, stream, true)
                }
            } catch (_: Exception) {
                PDType1Font.HELVETICA
            }
        }
    }

    companion object {
        fun categories(): List<String> = listOf(
            "All (26)",
            "Popular Unicode",
            "Print & Office",
            "Official & Gov",
            "Google & Modern",
            "MS Office",
            "Bijoy & Classic",
            "Stylish & Display"
        )

        fun detectFromPdfFontName(pdfFontName: String): BengaliFontOption? {
            val lower = pdfFontName.substringAfter('+').lowercase()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
            return when {
                lower.contains("kalpurush") -> KALPURUSH
                lower.contains("solaiman") -> SOLAIMAN_LIPI
                lower.contains("nikoshban") -> NIKOSH_BAN
                lower.contains("nikosh") -> NIKOSH
                lower.contains("siyam") || lower.contains("rupali") -> SIYAM_RUPALI
                lower.contains("notoserifbengali") -> NOTO_SERIF_BENGALI
                lower.contains("notosansbengali") || lower.contains("bengali") -> NOTO_SANS_BENGALI
                lower.contains("hindsiliguri") || lower.contains("siliguri") -> HIND_SILIGURI
                lower.contains("sutonnyomj") -> SUTONNY_OMJ
                lower.contains("sutonny") || lower.contains("sushree") || lower.contains("tonnybangla") -> SUTONNY_MJ
                lower.contains("vrinda") -> VRINDA
                lower.contains("nirmala") -> NIRMALA_UI
                lower.contains("adorsho") || lower.contains("adarsha") -> ADARSHA_LIPI
                lower.contains("aponalohit") -> APONA_LOHIT
                lower.contains("mukti") -> MUKTI
                lower.contains("akaash") || lower.contains("akash") -> AKAASH
                lower.contains("sagar") -> SAGAR
                lower.contains("likhan") -> LIKHAN
                lower.contains("ekusheypuja") -> EKUSHEY_PUJA
                lower.contains("ekushey") || lower.contains("lohit") -> EKUSHEY_LOHIT
                lower.contains("tirobangla") -> TIRO_BANGLA
                lower.contains("balooda") -> BALOO_DA_2
                lower.contains("anekbangla") -> ANEK_BANGLA
                lower.contains("mina") -> MINA
                lower.contains("galada") -> GALADA
                lower.contains("atma") -> ATMA
                else -> null
            }
        }
    }
}

/**
 * Inspects PDF fonts, normalizes broken Bengali vowel signs (matras / kars) extracted in visual order,
 * repairs decomposed nukta characters (ড়, ঢ়, য়), and converts legacy Bijoy / ANSI (SutonnyMJ)
 * encoded text into clean logical Unicode Bengali so Word (.docx), Excel, RTF, HTML, and OCR outputs
 * never display broken vowel circles (◌ি, ◌ে, ◌ৈ) or garbled ASCII characters.
 */
object BengaliTextNormalizer {

    private val legacyBijoyFontKeywords = listOf(
        "sutonnymj",
        "sushreemj",
        "tonnybanglamj",
        "rinkymj",
        "boishakhi",
        "dhanshirhimj",
        "chandrabatimj",
        "ulekhoni",
        "bijoy",
        "bangsee",
        "falgun",
        "kanchan",
        "moina"
    )

    fun containsBengaliUnicode(text: String): Boolean {
        return text.any { ch -> ch.code in 0x0980..0x09FF }
    }

    /**
     * Checks whether a PDF page declares legacy ANSI/Bijoy fonts in its resources.
     */
    fun hasLegacyBijoyFontsOnPage(page: PDPage?): Boolean {
        if (page == null) return false
        return try {
            val resources = page.resources ?: return false
            for (fontKey in resources.fontNames) {
                val font = try { resources.getFont(fontKey) } catch (_: Exception) { null }
                val rawName = (font?.name ?: fontKey.name).substringAfter('+').lowercase()
                    .replace("-", "")
                    .replace("_", "")
                    .replace(" ", "")
                if (legacyBijoyFontKeywords.any { rawName.contains(it) }) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Heuristic check for Bijoy/ANSI encoded Bengali text when font metadata is stripped or unavailable.
     * Bijoy text uses distinctive extended ASCII vowel signs (`†`, `‡`, `ˆ`, `‰`, `Š`, `‹`, `Œ`, `…`, `„`, `©`, `¨`, `Ö`)
     * or high frequency of `v` (া) and `w` (ি) attached to uppercase/lowercase consonant codes (`Av`, `gv`, `ev`, `mv`, `Kv`).
     */
    fun looksLikeBijoyAnsiText(text: String): Boolean {
        if (text.isBlank()) return false
        // If text already has substantial Unicode Bengali, check per-line instead
        val bijoyMarkers = charArrayOf('†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '…', '©', '¨', 'Ö', '³', '½', '¾', 'Ë', 'Í', 'Î', 'Ï', 'Ð', 'Ñ', 'Õ', '×', 'Ø', 'Ù', 'Ú', 'Ü', 'ß', 'ä', 'å', 'ê', 'ì', 'ï', 'ó', 'ô', 'õ')
        val markerHits = text.count { it in bijoyMarkers }
        if (markerHits >= 2) return true

        // Check classic Bijoy word patterns like "Avgvi" (আমার), "evsjv" (বাংলা), "†mvbvi" (সোনার), "Kwi" (করি)
        val bijoyWordRegex = Regex("""\b(Avgvi|evsjv|evsjv‡`k|†mvbvi|Avmvb|Kwi|K‡i|n‡e|Zvi|Zviv|cvwb|gvbyl|mv‡_|Rb¨)\b""")
        return bijoyWordRegex.containsMatchIn(text)
    }

    /**
     * Main entry point for cleaning and fixing Bengali text extracted from a PDF page or OCR result.
     */
    fun normalizeExtractedPdfText(
        rawText: String,
        pageHasBijoyFont: Boolean = false,
        fixBrokenMatrasAndNuktas: Boolean = true,
        convertBijoyAnsiToUnicode: Boolean = true
    ): String {
        if (rawText.isEmpty()) return rawText

        val lines = rawText.split('\n')
        val processedLines = lines.map { line ->
            var current = line
            if (convertBijoyAnsiToUnicode && (pageHasBijoyFont || looksLikeBijoyAnsiText(current))) {
                // Only convert segments/lines that don't already consist primarily of Unicode Bengali
                if (!containsBengaliUnicode(current) || looksLikeBijoyAnsiText(current)) {
                    current = convertBijoyToUnicode(current)
                }
            }
            if (fixBrokenMatrasAndNuktas && containsBengaliUnicode(current)) {
                current = fixUnicodeBengaliOrderAndNuktas(current)
            }
            current
        }
        return processedLines.joinToString("\n")
    }

    /**
     * Fixes broken Unicode Bengali text caused by PDF visual glyph ordering or OCR quirks:
     * 1. Pre-base vowel signs (ি \u09BF, ে \u09C7, ৈ \u09C8) placed BEFORE a consonant or conjunct cluster
     *    are moved AFTER the consonant/conjunct cluster.
     * 2. Composes Nukta combinations: ড+় -> ড়, ঢ+় -> ঢ়, য+় -> য়, ব+় -> র.
     * 3. Merges split two-part vowel signs: ে (\u09C7) + া (\u09BE) -> ো (\u09CB),
     *    and ে (\u09C7) + ৗ (\u09D7) -> ৌ (\u09CC).
     * 4. Cleans duplicate consecutive identical vowel signs (e.g. িি -> ি).
     */
    fun fixUnicodeBengaliOrderAndNuktas(input: String): String {
        if (input.isEmpty() || !containsBengaliUnicode(input)) return input

        // Step 1: Normalize decomposed nukta characters & legacy PDF র = ব + ়
        var text = input
            .replace("\u09A1\u09BC", "\u09DC") // ড + ় -> ড়
            .replace("\u09A2\u09BC", "\u09DD") // ঢ + ় -> ঢ়
            .replace("\u09AF\u09BC", "\u09DF") // য + ় -> য়
            .replace("\u09AC\u09BC", "\u09B0") // ব + ় -> র (common in PDF font subsets)
            .replace("\u0985\u09BE", "\u0986") // অ + া -> আ

        // Step 2: Reorder visual pre-base vowel signs (ি \u09BF, ে \u09C7, ৈ \u09C8)
        // when they appear at the start of a word or after a non-consonant/vowel/virama, followed by a consonant cluster.
        val chars = text.toCharArray()
        val sb = StringBuilder(chars.size)
        var i = 0
        while (i < chars.size) {
            val ch = chars[i]
            val prevChar = if (sb.isNotEmpty()) sb[sb.length - 1] else '\u0000'
            val isPreBaseVowel = ch == '\u09BF' || ch == '\u09C7' || ch == '\u09C8'

            // In valid logical Unicode, a vowel sign MUST immediately follow a consonant (or nukta/avagraha).
            // If it appears at the start of a token or after space/punctuation/another vowel sign/independent vowel,
            // AND is followed by a Bengali consonant, it was extracted in visual order!
            val prevCanTakeVowelSign = isBengaliConsonant(prevChar) || prevChar == '\u09BC'
            if (isPreBaseVowel && !prevCanTakeVowelSign && i + 1 < chars.size && isBengaliConsonant(chars[i + 1])) {
                // Consume the following consonant cluster: C (+ Nukta)? (+ Hasanta + C (+ Nukta)?)*
                var j = i + 1
                sb.append(chars[j])
                j++
                if (j < chars.size && chars[j] == '\u09BC') {
                    sb.append(chars[j])
                    j++
                }
                while (j + 1 < chars.size && chars[j] == '\u09CD' && isBengaliConsonant(chars[j + 1])) {
                    sb.append(chars[j]) // Hasanta ্
                    sb.append(chars[j + 1]) // Next consonant in conjunct
                    j += 2
                    if (j < chars.size && chars[j] == '\u09BC') {
                        sb.append(chars[j])
                        j++
                    }
                }
                // Now place the pre-base vowel sign in logical order AFTER the consonant cluster!
                sb.append(ch)
                i = j
            } else {
                sb.append(ch)
                i++
            }
        }

        text = sb.toString()

        // Step 3: Merge split two-part vowel signs (ে + া -> ো, ে + ৗ -> ৌ)
        text = text
            .replace("\u09C7\u09BE", "\u09CB") // ে + া -> ো
            .replace("\u09BE\u09C7", "\u09CB") // া + ে -> ো
            .replace("\u09C7\u09D7", "\u09CC") // ে + ৗ -> ৌ
            .replace("\u09D7\u09C7", "\u09CC") // ৗ + ে -> ৌ

        // Step 4: Remove accidental duplicate consecutive vowel signs from PDF double-strike bold rendering
        val dedup = StringBuilder(text.length)
        for (c in text) {
            if (dedup.isNotEmpty() && isBengaliVowelSign(c) && dedup[dedup.length - 1] == c) {
                continue
            }
            dedup.append(c)
        }
        return dedup.toString()
    }

    /**
     * Converts Bijoy / ANSI (SutonnyMJ) encoded Bengali strings into logical Unicode Bengali.
     */
    fun convertBijoyToUnicode(bijoyInput: String): String {
        if (bijoyInput.isEmpty()) return bijoyInput

        var s = bijoyInput

        // 1. Multi-character & special Bijoy conjuncts / ligatures first
        for ((pattern, unicode) in bijoyMultiCharMap) {
            if (s.contains(pattern)) {
                s = s.replace(pattern, unicode)
            }
        }

        // 2. Single-char Bijoy glyph mapping to intermediate Unicode (keeping visual pre-base kars & reph markers for step 3)
        val mapped = StringBuilder(s.length * 2)
        for (ch in s) {
            val replacement = bijoySingleCharMap[ch]
            if (replacement != null) {
                mapped.append(replacement)
            } else {
                mapped.append(ch)
            }
        }

        var unicodeStr = mapped.toString()

        // 3. Fix "অ + া" -> "আ"
        unicodeStr = unicodeStr.replace("\u0985\u09BE", "\u0986")

        // 4. Handle Bijoy Reph (© -> র্ which in Bijoy is placed AFTER the consonant/cluster,
        //    whereas in Unicode র্ (\u09B0\u09CD) must precede the consonant/cluster).
        unicodeStr = reorderPostBaseReph(unicodeStr)

        // 5. Reorder visual pre-base vowel signs (ি, ে, ৈ) and merge two-part vowels (ো, ৌ)
        return fixUnicodeBengaliOrderAndNuktas(unicodeStr)
    }

    private fun reorderPostBaseReph(input: String): String {
        // Marker \uF000 is used transiently if needed, or we look for \u09B0\u09CD\u200B from '©'
        if (!input.contains("\u09B0\u09CD\u200B")) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            if (i + 2 < input.length &&
                input[i] == '\u09B0' &&
                input[i + 1] == '\u09CD' &&
                input[i + 2] == '\u200B'
            ) {
                // Move র্ before the preceding consonant cluster in sb
                var insertPos = sb.length
                // Skip trailing vowel sign if any
                if (insertPos > 0 && isBengaliVowelSign(sb[insertPos - 1])) {
                    insertPos--
                }
                // Walk back over consonant cluster: C (+ Hasanta + C)*
                if (insertPos > 0 && isBengaliConsonant(sb[insertPos - 1])) {
                    insertPos--
                    while (insertPos >= 2 && sb[insertPos - 1] == '\u09CD' && isBengaliConsonant(sb[insertPos - 2])) {
                        insertPos -= 2
                    }
                }
                sb.insert(insertPos, "\u09B0\u09CD")
                i += 3
            } else {
                sb.append(input[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun isBengaliConsonant(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0995..0x09B9 ||
            code == 0x09CE || // ৎ
            code == 0x09DC || // ড়
            code == 0x09DD || // ঢ়
            code == 0x09DF    // য়
    }

    private fun isBengaliVowelSign(ch: Char): Boolean {
        val code = ch.code
        return code in 0x09BE..0x09CC || code == 0x09D7
    }

    private val bijoyMultiCharMap: List<Pair<String, String>> = listOf(
        "Av" to "আ",
        "GŠ" to "ঐ",
        "IŠ" to "ঔ",
        "ÿ" to "ক্ষ",
        "¶" to "ক্ষ",
        "Á" to "জ্ঞ",
        "³" to "ক্ত",
        "²" to "ক্ক",
        "·" to "ক্স",
        "¸" to "গু",
        "¹" to "গ্ধ",
        "º" to "গ্ন",
        "»" to "গ্ম",
        "¼" to "ঙ্ক",
        "½" to "ঙ্গ",
        "¾" to "জ্জ",
        "¿" to "ত্র",
        "À" to "জ্ঝ",
        "Â" to "ঞ্চ",
        "Ã" to "ঞ্ছ",
        "Ä" to "ঞ্জ",
        "Å" to "ঞ্ঝ",
        "Æ" to "ট্ট",
        "Ç" to "ড্ড",
        "È" to "ণ্ট",
        "É" to "ণ্ঠ",
        "Ê" to "ণ্ড",
        "Ë" to "ত্ত",
        "Ì" to "ত্থ",
        "Í" to "ত্ন",
        "Î" to "ত্র",
        "Ï" to "দ্দ",
        "Ð" to "দ্ধ",
        "Ñ" to "দ্ব",
        "Ò" to "দ্ভ",
        "Ó" to "দ্ম",
        "Ô" to "ধ্র",
        "Õ" to "ন্ত",
        "×" to "স্থ",
        "Ø" to "দ্ব",
        "Ù" to "ন্ধ",
        "Ú" to "ন্ন",
        "Û" to "ন্ব",
        "Ü" to "ন্ম",
        "Ý" to "প্ট",
        "Þ" to "প্ত",
        "ß" to "প্ন",
        "à" to "প্প",
        "á" to "প্ল",
        "â" to "প্স",
        "ã" to "ব্জ",
        "ä" to "ব্দ",
        "å" to "ব্ধ",
        "æ" to "ব্ব",
        "ç" to "ব্ল",
        "è" to "ভ্র",
        "é" to "ম্ন",
        "ê" to "ম্প",
        "ë" to "ম্ফ",
        "ì" to "ম্ব",
        "í" to "ম্ভ",
        "î" to "ম্ম",
        "ï" to "শু",
        "ð" to "শ্চ",
        "ñ" to "শ্ন",
        "ò" to "শ্ম",
        "ó" to "ষ্ট",
        "ô" to "ষ্ঠ",
        "õ" to "ষ্ণ",
        "ö" to "ষ্প",
        "÷" to "ষ্ফ",
        "ø" to "ষ্ম",
        "ù" to "স্ক",
        "ú" to "স্ট",
        "û" to "হু",
        "ü" to "হৃ",
        "ý" to "হ্ন",
        "þ" to "হ্ম",
        "Ö" to "্র", // Ra-phala
        "«" to "্র",
        "¨" to "্য", // Ja-phala
        "©" to "\u09B0\u09CD\u200B" // Post-base Bijoy Reph marker -> reordered to র্
    )

    /**
     * Generates OpenXML `word/fontTable.xml` declaring the selected Bengali font plus all 26 popular
     * Bengali fonts with proper fallback `w:altName` entries so MS Word, LibreOffice, WPS Office,
     * and Google Docs resolve the Bengali typeface without rendering tofu/square boxes.
     */
    fun buildDocxFontTableXml(primaryBengaliFont: BengaliFontOption): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append(
            """<w:font w:name="Calibri">""" +
                """<w:altName w:val="${primaryBengaliFont.fontFamilyName}"/>""" +
                """<w:charset w:val="00"/>""" +
                """<w:family w:val="swiss"/>""" +
                """<w:pitch w:val="variable"/>""" +
                """</w:font>"""
        )
        // Put the user's selected Bengali font first, followed by all other 25 popular Bengali fonts
        val orderedFonts = listOf(primaryBengaliFont) + BengaliFontOption.entries.filter { it != primaryBengaliFont }
        for (font in orderedFonts) {
            val familyAttr = if (font.isSerif) "roman" else "swiss"
            sb.append(
                """<w:font w:name="${font.fontFamilyName}">""" +
                    """<w:altName w:val="${font.fallbackFontName}"/>""" +
                    """<w:charset w:val="00"/>""" +
                    """<w:family w:val="$familyAttr"/>""" +
                    """<w:pitch w:val="variable"/>""" +
                    """</w:font>"""
            )
        }
        sb.append("""</w:fonts>""")
        return sb.toString()
    }

    private val bijoySingleCharMap: Map<Char, String> = mapOf(
        // Independent Vowels
        'A' to "অ",
        'B' to "ই",
        'C' to "ঈ",
        'D' to "উ",
        'E' to "ঊ",
        'F' to "ঋ",
        'G' to "এ",
        'H' to "ঐ",
        'I' to "ও",
        'J' to "ঔ",
        // Consonants
        'K' to "ক",
        'L' to "খ",
        'M' to "গ",
        'N' to "ঘ",
        'O' to "ঙ",
        'P' to "চ",
        'Q' to "ছ",
        'R' to "জ",
        'S' to "ঝ",
        'T' to "ঞ",
        'U' to "ট",
        'V' to "ঠ",
        'W' to "ড",
        'X' to "ঢ",
        'Y' to "ণ",
        'Z' to "ত",
        '_' to "থ",
        '`' to "দ",
        'a' to "ধ",
        'b' to "ন",
        'c' to "প",
        'd' to "ফ",
        'e' to "ব",
        'f' to "ভ",
        'g' to "ম",
        'h' to "য",
        'i' to "র",
        'j' to "ল",
        'k' to "শ",
        'l' to "ষ",
        'm' to "স",
        'n' to "হ",
        'o' to "ড়",
        'p' to "ঢ়",
        'q' to "য়",
        'r' to "ৎ",
        's' to "ং",
        't' to "ঃ",
        'u' to "ঁ",
        // Vowel Kars (Matras)
        'v' to "া",
        'w' to "ি",
        'x' to "ী",
        'y' to "ু",
        '~' to "ু",
        'z' to "ূ",
        '‚' to "ূ",
        '„' to "ৃ",
        '…' to "ৃ",
        '†' to "ে",
        '‡' to "ে",
        'ˆ' to "ৈ",
        '‰' to "ৈ",
        'Š' to "ৗ",
        // Virama / Hasanta & Dari
        '&' to "্",
        '|' to "।",
        // Bengali Digits
        '0' to "০",
        '1' to "১",
        '2' to "২",
        '3' to "৩",
        '4' to "৪",
        '5' to "৫",
        '6' to "৬",
        '7' to "৭",
        '8' to "৮",
        '9' to "৯"
    )
}
