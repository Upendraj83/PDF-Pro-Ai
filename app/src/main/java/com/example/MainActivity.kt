package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.AppLockScreen
import com.example.ui.BottomNavTab
import com.example.ui.PdfViewModel
import com.example.ui.ViewerState
import com.example.ui.common.GlobalErrorManager
import com.example.ui.common.GlobalErrorScreen
import com.example.ui.files.FilesScreen
import com.example.ui.home.HomeScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.scan.ScanScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tools.PageOrganizerScreen
import com.example.ui.tools.ToolsScreen
import com.example.ui.viewer.PdfViewerScreen
import com.example.ui.viewer.UniversalFileViewerScreen
import com.example.ui.viewer.UniversalViewerState
import com.example.viewer.UniversalFileParserEngine

class MainActivity : FragmentActivity() {

    private val viewModel: PdfViewModel by viewModels {
        PdfViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.ai.configureAppCheck(this, intent)
        com.example.pdf.PdfBoxMemoryUtil.cleanupScratchFiles(this)
        GlobalErrorManager.installGlobalExceptionHandler(this) { report ->
            viewModel.setGlobalErrorReport(report)
        }
        viewModel.settingsManager.applySavedLocaleOnStartup()
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val settings by viewModel.appSettings.collectAsStateWithLifecycle()
            val isAppLocked by viewModel.appLockManager.isAppLocked.collectAsStateWithLifecycle()
            val globalError by viewModel.globalErrorReport.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MyApplicationTheme(
                themeMode = settings.themeMode,
                appLanguage = settings.appLanguage
            ) {
                when {
                    globalError != null -> {
                        GlobalErrorScreen(
                            errorReport = globalError!!,
                            onDismiss = { viewModel.dismissGlobalError() }
                        )
                    }
                    isAppLocked -> {
                        AppLockScreen(appLockManager = viewModel.appLockManager)
                    }
                    else -> {
                        MainAppContent(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.appLockManager.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.appLockManager.onAppForegrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.example.ai.configureAppCheck(this, intent)
        handleIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.trimDeviceMemory()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action

        if (Intent.ACTION_VIEW == action) {
            intent.data?.let { uri ->
                viewModel.completeOnboarding()
                viewModel.openPdf(uri)
            }
        } else if (Intent.ACTION_SEND == action) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let {
                viewModel.completeOnboarding()
                viewModel.openPdf(it)
            }
        }
    }
}

data class NavItem(
    val tab: BottomNavTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val viewerState by viewModel.viewerState.collectAsStateWithLifecycle()
    val universalViewerState by viewModel.universalViewerState.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isOnboardingReplayOpen by viewModel.isOnboardingReplayOpen.collectAsStateWithLifecycle()

    val openPdfFromOnboardingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.openPdf(uri)
        }
    }

    // Show 3-screen skippable Onboarding ending at the file picker on first launch or replay
    if ((!settings.onboardingCompleted || isOnboardingReplayOpen) &&
        viewerState is ViewerState.Idle &&
        universalViewerState is UniversalViewerState.Idle
    ) {
        OnboardingScreen(
            onFinishAndPickFile = {
                viewModel.completeOnboarding()
                openPdfFromOnboardingLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES)
            },
            onSkipOnboarding = {
                viewModel.completeOnboarding()
            }
        )
        return
    }

    val navItems = listOf(
        NavItem(
            tab = BottomNavTab.HOME,
            label = stringResource(R.string.nav_home),
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "nav_home"
        ),
        NavItem(
            tab = BottomNavTab.FILES,
            label = stringResource(R.string.nav_files),
            selectedIcon = Icons.Filled.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            testTag = "nav_files"
        ),
        NavItem(
            tab = BottomNavTab.TOOLS,
            label = stringResource(R.string.nav_tools),
            selectedIcon = Icons.Filled.Build,
            unselectedIcon = Icons.Outlined.Build,
            testTag = "nav_tools"
        ),
        NavItem(
            tab = BottomNavTab.SCAN,
            label = stringResource(R.string.nav_scan),
            selectedIcon = Icons.Filled.CameraAlt,
            unselectedIcon = Icons.Outlined.CameraAlt,
            testTag = "nav_scan"
        ),
        NavItem(
            tab = BottomNavTab.PROFILE,
            label = stringResource(R.string.nav_profile),
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            testTag = "nav_profile"
        )
    )

    val isPageOrganizerOpen by viewModel.isPageOrganizerOpen.collectAsStateWithLifecycle()

    // Show Page Organizer when requested from Viewer
    if (isPageOrganizerOpen && viewerState is ViewerState.Viewing) {
        val viewing = viewerState as ViewerState.Viewing
        PageOrganizerScreen(
            docUri = viewing.uri,
            docName = viewing.fileName,
            viewModel = viewModel,
            onClose = { viewModel.setPageOrganizerOpen(false) }
        )
    } else if (universalViewerState !is UniversalViewerState.Idle) {
        UniversalFileViewerScreen(
            state = universalViewerState,
            onClose = { viewModel.closeUniversalViewer() },
            onConvertAndOpenAsPdf = { parsed -> viewModel.convertViewedUniversalFileToPdf(parsed) },
            onRunOcrOnBitmap = { bmp -> viewModel.ocrEngine.recognizeTextFromBitmap(bmp).fullText }
        )
    } else if (viewerState !is ViewerState.Idle) {
        PdfViewerScreen(viewModel = viewModel)
    } else {
        androidx.activity.compose.BackHandler(enabled = currentTab != BottomNavTab.HOME) {
            viewModel.selectTab(BottomNavTab.HOME)
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = stringResource(R.string.cd_app_logo),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_brand_short),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = stringResource(R.string.app_badge_pro_ai),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectTab(BottomNavTab.FILES) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_bar_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search_icon),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleLightDarkTheme() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_bar_theme_toggle_btn")
                        ) {
                            val isDarkActive = settings.themeMode == com.example.data.AppThemeMode.DARK
                            Icon(
                                imageVector = if (isDarkActive) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = if (isDarkActive) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { viewModel.selectTab(BottomNavTab.PROFILE) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_bar_profile_btn")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = stringResource(R.string.nav_profile),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    navItems.forEach { item ->
                        val isSelected = currentTab == item.tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(item.tab) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(text = item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = currentTab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                label = "nav_tab_crossfade"
            ) { tab ->
                when (tab) {
                    BottomNavTab.HOME -> HomeScreen(viewModel = viewModel)
                    BottomNavTab.FILES -> FilesScreen(viewModel = viewModel)
                    BottomNavTab.TOOLS -> ToolsScreen(viewModel = viewModel)
                    BottomNavTab.SCAN -> ScanScreen(viewModel = viewModel)
                    BottomNavTab.PROFILE -> ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}
