package com.example.ui.scan

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scan.OcrLanguageCategory
import com.example.scan.OcrLanguagePack
import com.example.scan.OcrLanguagePackManager
import com.example.scan.OcrPackInstallStatus
import kotlinx.coroutines.launch

/**
 * Compact, reusable horizontal OCR Language Pack bar with quick-select chips,
 * inline 1-tap download for uninstalled language packs, and a "Manage Packs" button.
 */
@Composable
fun OcrLanguageQuickSelectorBar(
    languagePackManager: OcrLanguagePackManager,
    onOpenManagePacksDialog: () -> Unit,
    onLanguageSelected: (OcrLanguagePack) -> Unit = {},
    onLanguageChanged: (OcrLanguagePack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val packManager = languagePackManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPack by packManager.selectedLanguagePack.collectAsState()
    val packStatuses by packManager.packStatuses.collectAsState()

    val installedCount = remember(packStatuses) {
        OcrLanguagePack.entries.count { pack ->
            pack.isBuiltIn || packStatuses[pack]?.isReadyToUse == true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ocr_language_quick_selector_bar"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "OCR Language: ${selectedPack.displayName} (${selectedPack.shortBadge})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(
                onClick = onOpenManagePacksDialog,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .testTag("ocr_open_language_packs_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Language Packs ($installedCount/${OcrLanguagePack.entries.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OcrLanguagePack.entries.forEach { pack ->
                val status = packStatuses[pack]
                val isInstalled = pack.isBuiltIn || status?.isReadyToUse == true
                val isDownloading = status is OcrPackInstallStatus.Downloading
                val isSelected = selectedPack == pack

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isInstalled) {
                            packManager.setSelectedLanguagePack(pack)
                            onLanguageSelected(pack)
                            onLanguageChanged(pack)
                        } else if (!isDownloading) {
                            scope.launch {
                                val ok = packManager.downloadLanguagePack(
                                    pack = pack,
                                    allowCellularOverride = true,
                                    autoSelectOnComplete = true
                                )
                                if (ok) {
                                    Toast.makeText(
                                        context,
                                        "Downloaded & activated ${pack.displayName} OCR pack",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onLanguageSelected(pack)
                                    onLanguageChanged(pack)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Could not download ${pack.displayName} pack",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    leadingIcon = {
                        when {
                            isDownloading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            isSelected -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            !isInstalled -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Download pack",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    },
                    label = {
                        val statusSuffix = when {
                            isDownloading -> {
                                val pct = ((status as OcrPackInstallStatus.Downloading).progressFraction * 100).toInt()
                                " ($pct%)"
                            }
                            !isInstalled -> " • ${Formatter.formatShortFileSize(context, pack.estimatedSizeBytes)}"
                            else -> ""
                        }
                        Text(
                            text = "${pack.displayName} (${pack.shortBadge})$statusSuffix",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("ocr_lang_chip_${pack.id}")
                )
            }
        }
    }
}

/**
 * Full-screen Material 3 dialog for downloading, selecting, and managing optional ML Kit OCR Language Packs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OcrLanguagePacksDialog(
    languagePackManager: OcrLanguagePackManager,
    onLanguageSelected: (OcrLanguagePack) -> Unit = {},
    onDismiss: () -> Unit
) {
    val packManager = languagePackManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedPack by packManager.selectedLanguagePack.collectAsState()
    val packStatuses by packManager.packStatuses.collectAsState()
    val wifiOnly by packManager.wifiOnlyDownloads.collectAsState()

    var selectedCategory by remember { mutableStateOf(OcrLanguageCategory.ALL) }
    var isBatchDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        packManager.refreshPackStatuses()
    }

    val installedCount = remember(packStatuses) {
        OcrLanguagePack.entries.count { pack ->
            pack.isBuiltIn || packStatuses[pack]?.isReadyToUse == true
        }
    }

    val totalInstalledBytes = remember(packStatuses) {
        packStatuses.values
            .filterIsInstance<OcrPackInstallStatus.Installed>()
            .sumOf { it.installedSizeBytes }
    }

    val filteredPacks = remember(selectedCategory, packStatuses) {
        when (selectedCategory) {
            OcrLanguageCategory.ALL -> OcrLanguagePack.entries
            OcrLanguageCategory.INSTALLED -> OcrLanguagePack.entries.filter { pack ->
                pack.isBuiltIn || packStatuses[pack]?.isReadyToUse == true
            }
            else -> OcrLanguagePack.entries.filter { it.category == selectedCategory || it == OcrLanguagePack.AUTO }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ocr_language_packs_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "ML Kit OCR Language Packs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Download & manage optional non-English OCR models",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("ocr_packs_close_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Language Packs")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    packManager.refreshPackStatuses()
                                    Toast.makeText(context, "Refreshed language pack status", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("ocr_packs_refresh_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Summary & Storage Overview Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active OCR Language: ${selectedPack.displayName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${selectedPack.nativeName} • ${selectedPack.scriptEngine.engineLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "$installedCount / ${OcrLanguagePack.entries.size} Ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Optional Packs Storage: ${Formatter.formatShortFileSize(context, totalInstalledBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                if (totalInstalledBytes > 0L) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val removed = packManager.deleteAllOptionalPacks()
                                                Toast.makeText(
                                                    context,
                                                    "Removed $removed optional OCR language pack(s)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("ocr_packs_clear_all_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Free Space", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Wi-Fi Only Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Download over Wi-Fi only",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Prevent mobile data usage when downloading language packs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = wifiOnly,
                                    onCheckedChange = { packManager.setWifiOnlyDownloads(it) },
                                    modifier = Modifier.testTag("ocr_packs_wifi_only_switch")
                                )
                            }
                        }
                    }
                }

                // 2. Quick Recommended Bundle Actions
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isBatchDownloading = true
                                    val count = packManager.downloadLanguagePacks(
                                        packs = listOf(
                                            OcrLanguagePack.BENGALI,
                                            OcrLanguagePack.HINDI_DEVANAGARI
                                        ),
                                        allowCellularOverride = true
                                    )
                                    isBatchDownloading = false
                                    Toast.makeText(
                                        context,
                                        "Installed $count South Asian OCR pack(s) (Bangla & Hindi)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !isBatchDownloading,
                            modifier = Modifier.testTag("ocr_bundle_south_asian_btn")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get Bangla + Hindi Packs", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isBatchDownloading = true
                                    val count = packManager.downloadLanguagePacks(
                                        packs = listOf(
                                            OcrLanguagePack.CHINESE,
                                            OcrLanguagePack.JAPANESE,
                                            OcrLanguagePack.KOREAN
                                        ),
                                        allowCellularOverride = true
                                    )
                                    isBatchDownloading = false
                                    Toast.makeText(
                                        context,
                                        "Installed $count East Asian (CJK) OCR pack(s)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !isBatchDownloading,
                            modifier = Modifier.testTag("ocr_bundle_cjk_btn")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get CJK Packs (ZH/JA/KO)", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // 3. Category Filter Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OcrLanguageCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) },
                                modifier = Modifier.testTag("ocr_category_chip_${category.name}")
                            )
                        }
                    }
                }

                // 4. Individual Language Pack Cards
                items(filteredPacks, key = { it.id }) { pack ->
                    val status = packStatuses[pack] ?: if (pack.isBuiltIn) {
                        OcrPackInstallStatus.BuiltIn
                    } else {
                        OcrPackInstallStatus.NotDownloaded
                    }
                    val isSelected = selectedPack == pack

                    OcrLanguagePackItemCard(
                        pack = pack,
                        status = status,
                        isSelected = isSelected,
                        onSelect = {
                            if (status.isReadyToUse) {
                                packManager.setSelectedLanguagePack(pack)
                                onLanguageSelected(pack)
                            } else {
                                scope.launch {
                                    val ok = packManager.downloadLanguagePack(
                                        pack = pack,
                                        allowCellularOverride = true,
                                        autoSelectOnComplete = true
                                    )
                                    if (ok) {
                                        onLanguageSelected(pack)
                                    }
                                }
                            }
                        },
                        onDownload = {
                            scope.launch {
                                val ok = packManager.downloadLanguagePack(
                                    pack = pack,
                                    allowCellularOverride = !wifiOnly
                                )
                                if (ok) {
                                    Toast.makeText(
                                        context,
                                        "${pack.displayName} OCR pack installed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                if (packManager.deleteLanguagePack(pack)) {
                                    Toast.makeText(
                                        context,
                                        "Removed ${pack.displayName} OCR pack",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrLanguagePackItemCard(
    pack: OcrLanguagePack,
    status: OcrPackInstallStatus,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onSelect() }
            .testTag("ocr_pack_card_${pack.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Script Badge Circle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pack.shortBadge,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = pack.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "• ${pack.nativeName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = pack.supportedScriptsDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = pack.scriptEngine.engineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action / Status Controls
                when (status) {
                    is OcrPackInstallStatus.BuiltIn -> {
                        Button(
                            onClick = onSelect,
                            enabled = !isSelected,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ocr_pack_use_${pack.id}")
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSelected) "Active" else "Use")
                        }
                    }

                    is OcrPackInstallStatus.NotDownloaded -> {
                        FilledTonalButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ocr_pack_download_${pack.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download ${pack.displayName}",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Formatter.formatShortFileSize(context, pack.estimatedSizeBytes))
                        }
                    }

                    is OcrPackInstallStatus.Downloading -> {
                        val pct = (status.progressFraction * 100).toInt()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is OcrPackInstallStatus.Installed -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = onSelect,
                                enabled = !isSelected,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ocr_pack_use_${pack.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSelected) "Active" else "Use")
                            }

                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("ocr_pack_delete_${pack.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete ${pack.displayName} pack",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    is OcrPackInstallStatus.Failed -> {
                        OutlinedButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("ocr_pack_retry_${pack.id}")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            if (status is OcrPackInstallStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { status.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            } else if (status is OcrPackInstallStatus.Failed) {
                Text(
                    text = status.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
