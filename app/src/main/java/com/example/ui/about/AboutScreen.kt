package com.example.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R

data class LibraryLicenseEntry(
    val name: String,
    val artifact: String,
    val version: String,
    val licenseName: String,
    val copyrightHolder: String
)

object AppAboutRepository {
    const val PRIVACY_POLICY_PLACEHOLDER_URL = "https://pdfallinone.app/privacy-policy"

    val appVersionName: String
        get() = BuildConfig.VERSION_NAME

    val appVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    /**
     * Complete license list for every library and font bundled in the application.
     */
    fun getAllBundledLibraries(): List<LibraryLicenseEntry> = listOf(
        LibraryLicenseEntry(
            name = "PdfBox-Android",
            artifact = "com.tom-roush:pdfbox-android",
            version = "2.0.27.0",
            licenseName = "Apache License 2.0",
            copyrightHolder = "The Apache Software Foundation / Tom Roush"
        ),
        LibraryLicenseEntry(
            name = "BouncyCastle Provider & PKIX",
            artifact = "org.bouncycastle:bcprov-jdk15to18 / bcpkix-jdk15to18",
            version = "1.72",
            licenseName = "Bouncy Castle License (MIT-style)",
            copyrightHolder = "Legion of the Bouncy Castle Inc."
        ),
        LibraryLicenseEntry(
            name = "AndroidX Core, AppCompat & Activity Compose",
            artifact = "androidx.core:core-ktx / androidx.appcompat:appcompat / androidx.activity:activity-compose",
            version = "1.12.0 / 1.6.1 / 1.8.2",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "Jetpack Compose UI, Foundation & Material 3",
            artifact = "androidx.compose.material3:material3 / androidx.compose.ui:ui",
            version = "BOM 2024.06.00",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "Jetpack Navigation Compose",
            artifact = "androidx.navigation:navigation-compose",
            version = "2.7.7",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "AndroidX Lifecycle & ViewModel Coroutines",
            artifact = "androidx.lifecycle:lifecycle-runtime-compose / lifecycle-viewmodel-compose",
            version = "2.8.3",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "AndroidX Room Persistence Library",
            artifact = "androidx.room:room-runtime / room-ktx",
            version = "2.7.1",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "AndroidX WorkManager Runtime KTX",
            artifact = "androidx.work:work-runtime-ktx",
            version = "2.9.0",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "AndroidX Biometric",
            artifact = "androidx.biometric:biometric",
            version = "1.2.0-alpha05",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "AndroidX CameraX (Core, Camera2, Lifecycle, View)",
            artifact = "androidx.camera:camera-core / camera-camera2 / camera-lifecycle / camera-view",
            version = "1.3.4",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC / Android Open Source Project"
        ),
        LibraryLicenseEntry(
            name = "Google ML Kit Document Scanner & Text Recognition",
            artifact = "com.google.android.gms:play-services-mlkit-document-scanner / com.google.mlkit:text-recognition",
            version = "16.0.0-beta1 / 16.0.1",
            licenseName = "Google APIs Terms of Service & Apache 2.0",
            copyrightHolder = "Google LLC"
        ),
        LibraryLicenseEntry(
            name = "Firebase AI Logic & Firebase App Check",
            artifact = "com.google.firebase:firebase-ai / firebase-appcheck-playintegrity",
            version = "BOM 34.11.0",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Google LLC"
        ),
        LibraryLicenseEntry(
            name = "Kotlin Standard Library, Coroutines & Serialization",
            artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-android / kotlinx-serialization-json",
            version = "1.8.0 / 1.6.3",
            licenseName = "Apache License 2.0",
            copyrightHolder = "JetBrains s.r.o."
        ),
        LibraryLicenseEntry(
            name = "Coil Compose Image Loader",
            artifact = "io.coil-kt:coil-compose",
            version = "2.6.0",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Coil Contributors"
        ),
        LibraryLicenseEntry(
            name = "Retrofit, OkHttp & Moshi",
            artifact = "com.squareup.retrofit2:retrofit / com.squareup.okhttp3:okhttp / com.squareup.moshi:moshi-kotlin",
            version = "2.11.0 / 4.12.0 / 1.15.1",
            licenseName = "Apache License 2.0",
            copyrightHolder = "Square, Inc."
        ),
        LibraryLicenseEntry(
            name = "Noto Sans & Noto Sans Bengali Fonts",
            artifact = "res/font/noto_sans.ttf & res/font/noto_sans_bengali.ttf",
            version = "2.0",
            licenseName = "SIL Open Font License 1.1 (OFL-1.1)",
            copyrightHolder = "Google LLC / Noto Project Authors"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val libraries = remember { AppAboutRepository.getAllBundledLibraries() }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("about_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("about_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // App Identity & Version Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = stringResource(R.string.cd_app_logo),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Text(
                                text = stringResource(
                                    R.string.about_version_format,
                                    AppAboutRepository.appVersionName,
                                    AppAboutRepository.appVersionCode
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("about_version_text")
                            )

                            Text(
                                text = stringResource(R.string.settings_offline_badge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Privacy Policy Link Placeholder Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyDialog = true }
                            .testTag("about_privacy_policy_link"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Policy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.about_privacy_policy_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.about_privacy_policy_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = AppAboutRepository.PRIVACY_POLICY_PLACEHOLDER_URL,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Licenses Header
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.about_licenses_header, libraries.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.about_licenses_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Individual Library License Cards
                items(
                    items = libraries,
                    key = { it.name }
                ) { lib ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("license_item_${lib.name}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = lib.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = lib.licenseName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${lib.artifact} (${lib.version})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "© ${lib.copyrightHolder}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.about_privacy_policy_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Offline-First PDF Processing: Viewing, editing, merging, splitting, compression, encryption, form filling, and ML Kit OCR run locally on your device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Explicit Cloud AI Consent: No document text is ever sent to Gemini AI without your explicit opt-in consent. Password-protected PDFs require an extra confirmation.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Zero Analytics by Default: Usage analytics are strictly disabled unless you opt in within Settings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Privacy Policy Placeholder URL:\n${AppAboutRepository.PRIVACY_POLICY_PLACEHOLDER_URL}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(
                                "Privacy Policy URL",
                                AppAboutRepository.PRIVACY_POLICY_PLACEHOLDER_URL
                            )
                        )
                        Toast.makeText(context, "Privacy policy link copied", Toast.LENGTH_SHORT).show()
                        showPrivacyDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Link")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPrivacyDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
