package com.example.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.data.AppLanguage
import com.example.data.AppThemeMode
import com.example.data.SettingsManager
import java.util.Locale

private val DarkColorScheme = darkColorScheme(
    primary = PdfRedPrimaryDark,
    onPrimary = PdfRedOnPrimaryDark,
    primaryContainer = PdfRedPrimaryContainerDark,
    onPrimaryContainer = PdfRedOnPrimaryContainerDark,
    secondary = PdfRedSecondaryDark,
    onSecondary = PdfRedOnSecondaryDark,
    secondaryContainer = PdfRedSecondaryContainerDark,
    onSecondaryContainer = PdfRedOnSecondaryContainerDark,
    tertiary = PdfGoldTertiaryDark,
    onTertiary = PdfGoldOnTertiaryDark,
    tertiaryContainer = PdfGoldTertiaryContainerDark,
    onTertiaryContainer = PdfGoldOnTertiaryContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = PdfRedPrimary,
    onPrimary = PdfRedOnPrimary,
    primaryContainer = PdfRedPrimaryContainer,
    onPrimaryContainer = PdfRedOnPrimaryContainer,
    secondary = PdfRedSecondary,
    onSecondary = PdfRedOnSecondary,
    secondaryContainer = PdfRedSecondaryContainer,
    onSecondaryContainer = PdfRedOnSecondaryContainer,
    tertiary = PdfGoldTertiary,
    onTertiary = PdfGoldOnTertiary,
    tertiaryContainer = PdfGoldTertiaryContainer,
    onTertiaryContainer = PdfGoldOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    appLanguage: AppLanguage = AppLanguage.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current

    val localizedContext = remember(baseContext, baseConfig, appLanguage) {
        SettingsManager.createLocalizedContext(baseContext, appLanguage)
    }
    val localizedConfig = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }

    val isBengaliActive = remember(appLanguage, localizedConfig) {
        when (appLanguage) {
            AppLanguage.BENGALI -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> {
                val primaryLocale = localizedConfig.locales.get(0) ?: Locale.getDefault()
                primaryLocale.language.equals("bn", ignoreCase = true)
            }
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (baseContext as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(localizedContext) else dynamicLightColorScheme(localizedContext)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val appTypography = remember(isBengaliActive) {
        // Always include Noto Sans Bengali for Bengali locale or fallback rendering
        buildAppTypography(useBengaliFont = isBengaliActive)
    }

    val currentRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current

    if (currentRegistryOwner != null) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfig,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides currentRegistryOwner
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = appTypography,
                content = content
            )
        }
    } else {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfig
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = appTypography,
                content = content
            )
        }
    }
}
