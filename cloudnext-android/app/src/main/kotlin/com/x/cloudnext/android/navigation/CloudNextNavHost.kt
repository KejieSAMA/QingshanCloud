package com.x.cloudnext.android.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.feature.home.HomeScreen
import com.x.cloudnext.android.feature.login.LoginScreen
import com.x.cloudnext.android.feature.myfiles.FolderScreen
import com.x.cloudnext.android.feature.myfiles.MyFilesScreen
import com.x.cloudnext.android.feature.preview.PreviewTarget
import com.x.cloudnext.android.feature.preview.image.ImagePreviewScreen
import com.x.cloudnext.android.feature.preview.office.OnlyofficeWebScreen
import com.x.cloudnext.android.feature.preview.pdf.PdfPreviewScreen
import com.x.cloudnext.android.feature.preview.resolvePreviewTarget
import com.x.cloudnext.android.feature.preview.text.TextPreviewScreen
import com.x.cloudnext.android.feature.preview.video.VideoPreviewScreen
import com.x.cloudnext.android.feature.links.AccessLogsScreen
import com.x.cloudnext.android.feature.links.CreateLinkScreen
import com.x.cloudnext.android.feature.links.LinksRootScreen
import com.x.cloudnext.android.feature.search.SearchScreen
import com.x.cloudnext.android.feature.settings.AboutScreen
import com.x.cloudnext.android.feature.settings.SettingsScreen
import com.x.cloudnext.android.feature.shared.SharedHostScreen
import com.x.cloudnext.android.feature.shared.create.CreateShareScreen
import com.x.cloudnext.android.feature.shared.incoming.IncomingFolderScreen
import com.x.cloudnext.android.feature.shared.org.OrgPickerMode
import com.x.cloudnext.android.feature.shared.org.OrgPickerScreen
import com.x.cloudnext.android.feature.shared.org.ShareTargetCodec
import com.x.cloudnext.android.feature.transfer.TransferCenterScreen
import com.x.cloudnext.android.feature.transfer.TransferDetailScreen
import com.x.cloudnext.android.feature.trash.TrashScreen
import com.x.cloudnext.android.feature.workspaces.WorkspaceDetailScreen
import com.x.cloudnext.android.feature.workspaces.WorkspaceMembersScreen
import com.x.cloudnext.android.feature.workspaces.WorkspacesScreen
import com.x.cloudnext.android.network.BuildConfig
import com.x.cloudnext.android.ui.CnBottomTabs
import com.x.cloudnext.android.ui.CnDrawer
import com.x.cloudnext.android.ui.CnTab
import com.x.cloudnext.android.viewmodel.BootstrapState
import com.x.cloudnext.android.viewmodel.RootViewModel

private const val SHARE_TARGETS_KEY = "share.targets"
private const val MEMBER_PICKED_KEY = "member.picked"

// 白屏诊断 logcat tag。配合 `adb logcat | findstr "CnLifecycle CnShell CnRoute"` 看时序。
private const val TAG_SHELL = "CnShell"
private const val TAG_ROUTE = "CnRoute"

/**
 * 包装 onBack：先打 log 当前栈顶 destination，再 popBackStack。
 *
 * **栈底保护**（修白屏根因 2026-05-23）：如果 previousBackStackEntry 已经是 null，
 * 说明当前栈只剩一个 destination（Home），再 pop 会把栈清空 → Compose Navigation 没东西
 * 渲染就显示白屏。所以必须拒绝。常见触发场景：fullscreen route 的 BackHandler 在
 * composition 已离开后被 predictive back 二次触发（OnlyofficeWebScreen 的 WebView
 * BackHandler 已知会这样），或用户连续两次点返回箭头。
 */
private fun NavHostController.popLogged(routeTag: String): Boolean {
    val before = currentDestination?.route
    if (previousBackStackEntry == null) {
        Timber.tag(TAG_ROUTE).w("Pop %s SKIPPED: at root, current=%s", routeTag, before)
        return false
    }
    val ok = popBackStack()
    Timber.tag(TAG_ROUTE).d("Pop %s ok=%s before=%s after=%s", routeTag, ok, before, currentDestination?.route)
    return ok
}

/**
 * 共享根 NavController。Tab 内嵌 NavHost 通过 LocalRootNav 拿到顶层 navController，
 * 用于跳转到 OrgPicker / WorkspaceDetail 这类只在顶层注册的 fullscreen route。
 */
val LocalRootNav = staticCompositionLocalOf<NavHostController> {
    error("LocalRootNav not provided")
}

/**
 * 当前窗口大小档位。M6 引入，由 MainActivity 通过 calculateWindowSizeClass 计算后 provide。
 * Compact = 手机竖屏；Medium = 折叠 / 小平板；Expanded = 大平板 / 横屏（开启双栏）。
 */
val LocalWindowSizeClass = staticCompositionLocalOf<androidx.compose.material3.windowsizeclass.WindowSizeClass> {
    error("LocalWindowSizeClass not provided")
}

/**
 * 根 NavHost。
 * - 顶层：Login（未登录）/ Main（已登录，含 4 Tab Scaffold + Drawer）
 * - Main 内部不再走顶层 NavHost：用 selectedTab state + 4 NavController（每 Tab 一个）
 *   切换 Tab 不破坏各自 backstack
 */
@Composable
fun CloudNextNavHost(
    modifier: Modifier = Modifier,
    rootNavController: NavHostController = rememberNavController(),
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val tokenState by rootViewModel.tokenFlow.collectAsState()
    val bootstrap by rootViewModel.bootstrap.collectAsState()

    if (bootstrap == BootstrapState.Checking) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {}
        return
    }

    val initial: CnRoute =
        if (tokenState.isNullOrBlank()) CnRoute.Login else CnRoute.Home

    CompositionLocalProvider(LocalRootNav provides rootNavController) {
    NavHost(
        navController = rootNavController,
        startDestination = initial,
        modifier = modifier,
    ) {
        composable<CnRoute.Login> {
            LoginScreen(
                onLoginSuccess = {
                    rootNavController.navigate(CnRoute.Home) {
                        popUpTo(CnRoute.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<CnRoute.Home> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            MainShell(
                onLoggedOut = {
                    rootNavController.navigate(CnRoute.Login) {
                        popUpTo(rootNavController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenTransferCenter = {
                    rootNavController.navigate(CnRoute.TransferCenter)
                },
                onOpenTrash = {
                    rootNavController.navigate(CnRoute.TrashRoot)
                },
                onOpenSearch = {
                    rootNavController.navigate(CnRoute.SearchRoot)
                },
                onOpenCreateShare = { itemIds ->
                    rootNavController.navigate(CnRoute.CreateShare(itemIdsCsv = itemIds.joinToString(",")))
                },
                onOpenCreateLink = { itemId, fileName ->
                    rootNavController.navigate(CnRoute.CreateLink(itemId = itemId, fileName = fileName))
                },
                onOpenAccessLogs = { linkId, fileName ->
                    rootNavController.navigate(CnRoute.AccessLogs(linkId = linkId, fileName = fileName))
                },
                onOpenSettings = {
                    rootNavController.navigate(CnRoute.Settings)
                },
                onOpenLinks = {
                    rootNavController.navigate(CnRoute.LinksRoot)
                },
                onOpenFile = { item -> openPreview(rootNavController, ctx, item) },
            )
        }
        composable<CnRoute.TransferCenter> {
            TransferCenterScreen(
                onBack = { rootNavController.popLogged("TransferCenter") },
                onOpenDetail = { taskId ->
                    rootNavController.navigate(CnRoute.TransferDetail(taskId))
                },
            )
        }
        composable<CnRoute.TransferDetail> {
            TransferDetailScreen(onBack = { rootNavController.popLogged("TransferDetail") })
        }

        // ---- Preview routes (M4) ----
        composable<CnRoute.ImagePreview> { entry ->
            val r: CnRoute.ImagePreview = entry.toRoute()
            ImagePreviewScreen(
                itemId = r.itemId,
                name = r.name,
                baseUrl = BuildConfig.API_BASE_URL,
                onBack = { rootNavController.popLogged("ImagePreview") },
            )
        }
        composable<CnRoute.PdfPreview> { entry ->
            val r: CnRoute.PdfPreview = entry.toRoute()
            PdfPreviewScreen(
                name = r.name,
                onBack = { rootNavController.popLogged("PdfPreview") },
            )
        }
        composable<CnRoute.VideoPreview> { entry ->
            val r: CnRoute.VideoPreview = entry.toRoute()
            VideoPreviewScreen(
                name = r.name,
                onBack = { rootNavController.popLogged("VideoPreview") },
            )
        }
        composable<CnRoute.OnlyofficeWeb> { entry ->
            val r: CnRoute.OnlyofficeWeb = entry.toRoute()
            OnlyofficeWebScreen(
                name = r.name,
                onBack = { rootNavController.popLogged("OnlyofficeWeb") },
            )
        }
        composable<CnRoute.TextPreview> { entry ->
            val r: CnRoute.TextPreview = entry.toRoute()
            TextPreviewScreen(
                name = r.name,
                onBack = { rootNavController.popLogged("TextPreview") },
            )
        }

        // ---- M5a routes ----
        composable<CnRoute.TrashRoot> {
            TrashScreen(onBack = { rootNavController.popLogged("TrashRoot") })
        }
        composable<CnRoute.SearchRoot> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            SearchScreen(
                onBack = { rootNavController.popLogged("SearchRoot") },
                onOpenItem = { item -> openPreview(rootNavController, ctx, item) },
            )
        }
        composable<CnRoute.CreateShare> { entry ->
            val r: CnRoute.CreateShare = entry.toRoute()
            val itemIds = remember(r.itemIdsCsv) { r.itemIdsCsv.split(",").filter { it.isNotBlank() } }
            // 从 savedStateHandle 读 OrgPicker 回传的 targets
            val targetsJson = entry.savedStateHandle.getStateFlow<String?>(SHARE_TARGETS_KEY, null)
                .collectAsState()
            val presetTargets = remember(targetsJson.value) {
                ShareTargetCodec.decode(targetsJson.value)
            }
            CreateShareScreen(
                itemIds = itemIds,
                presetTargets = presetTargets,
                onBack = {
                    entry.savedStateHandle.remove<String>(SHARE_TARGETS_KEY)
                    rootNavController.popLogged("CreateShare")
                },
                onPickOrg = {
                    rootNavController.navigate(CnRoute.OrgPicker(mode = "multi", resultKey = SHARE_TARGETS_KEY))
                },
            )
        }
        composable<CnRoute.OrgPicker> { entry ->
            val r: CnRoute.OrgPicker = entry.toRoute()
            val pickerMode = if (r.mode == "single") OrgPickerMode.Single else OrgPickerMode.Multi
            OrgPickerScreen(
                onCancel = { rootNavController.popLogged("OrgPicker(cancel)") },
                onConfirm = { selected ->
                    val previous = rootNavController.previousBackStackEntry
                    previous?.savedStateHandle?.set(
                        r.resultKey,
                        ShareTargetCodec.encode(selected),
                    )
                    rootNavController.popLogged("OrgPicker(confirm)")
                },
                mode = pickerMode,
            )
        }

        // ---- M5b 顶层 fullscreen routes ----
        composable<CnRoute.CreateLink> { entry ->
            val r: CnRoute.CreateLink = entry.toRoute()
            CreateLinkScreen(
                itemId = r.itemId,
                fileName = r.fileName,
                onBack = { rootNavController.popLogged("CreateLink") },
            )
        }
        composable<CnRoute.AccessLogs> { entry ->
            val r: CnRoute.AccessLogs = entry.toRoute()
            AccessLogsScreen(
                fileName = r.fileName,
                onBack = { rootNavController.popLogged("AccessLogs") },
            )
        }
        composable<CnRoute.WorkspaceDetail> { entry ->
            val r: CnRoute.WorkspaceDetail = entry.toRoute()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            WorkspaceDetailScreen(
                titleFallback = r.name,
                onBack = { rootNavController.popLogged("WorkspaceDetail") },
                onOpenFolder = { wsId, item ->
                    rootNavController.navigate(
                        CnRoute.WorkspaceFolder(workspaceId = wsId, parentId = item.id, name = item.name),
                    )
                },
                onOpenFile = { item -> openPreview(rootNavController, ctx, item) },
                onOpenMembers = { wsId, name ->
                    rootNavController.navigate(CnRoute.WorkspaceMembers(workspaceId = wsId, name = name))
                },
            )
        }
        composable<CnRoute.WorkspaceFolder> { entry ->
            val r: CnRoute.WorkspaceFolder = entry.toRoute()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            WorkspaceDetailScreen(
                titleFallback = r.name,
                onBack = { rootNavController.popLogged("WorkspaceFolder") },
                onOpenFolder = { wsId, item ->
                    rootNavController.navigate(
                        CnRoute.WorkspaceFolder(workspaceId = wsId, parentId = item.id, name = item.name),
                    )
                },
                onOpenFile = { item -> openPreview(rootNavController, ctx, item) },
                onOpenMembers = { wsId, name ->
                    rootNavController.navigate(CnRoute.WorkspaceMembers(workspaceId = wsId, name = name))
                },
            )
        }
        composable<CnRoute.Settings> {
            SettingsScreen(
                onBack = { rootNavController.popLogged("Settings") },
                onOpenAbout = { rootNavController.navigate(CnRoute.About) },
            )
        }
        composable<CnRoute.About> {
            AboutScreen(
                onBack = { rootNavController.popLogged("About") },
                appVersion = com.x.cloudnext.android.BuildConfig.VERSION_NAME,
                versionCode = com.x.cloudnext.android.BuildConfig.VERSION_CODE,
            )
        }
        composable<CnRoute.LinksRoot> {
            LinksRootScreen(
                onBack = { rootNavController.popLogged("LinksRoot") },
                onOpenAccessLogs = { linkId, fileName ->
                    rootNavController.navigate(CnRoute.AccessLogs(linkId = linkId, fileName = fileName))
                },
            )
        }
        composable<CnRoute.WorkspaceMembers> { entry ->
            val r: CnRoute.WorkspaceMembers = entry.toRoute()
            // 监听 OrgPicker SINGLE 返回结果
            val pickedJson = entry.savedStateHandle.getStateFlow<String?>(MEMBER_PICKED_KEY, null)
                .collectAsState()
            val viewModel: com.x.cloudnext.android.feature.workspaces.WorkspaceMembersViewModel = hiltViewModel()
            LaunchedEffect(pickedJson.value) {
                val raw = pickedJson.value ?: return@LaunchedEffect
                val targets = ShareTargetCodec.decode(raw)
                targets.firstOrNull()?.let { target ->
                    viewModel.addMember(personOaId = target.id, personName = target.name)
                }
                entry.savedStateHandle.remove<String>(MEMBER_PICKED_KEY)
            }
            WorkspaceMembersScreen(
                workspaceName = r.name,
                onBack = { rootNavController.popLogged("WorkspaceMembers") },
                onPickPerson = {
                    rootNavController.navigate(
                        CnRoute.OrgPicker(mode = "single", resultKey = MEMBER_PICKED_KEY),
                    )
                },
                viewModel = viewModel,
            )
        }
    }
    }  // CompositionLocalProvider close

    LaunchedEffect(tokenState) {
        if (tokenState.isNullOrBlank()) {
            rootNavController.navigate(CnRoute.Login) {
                popUpTo(rootNavController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

/**
 * 已登录的主界面：Drawer + Scaffold（bottomBar = CnBottomTabs）。
 *
 * - 每个 Tab 独立 NavController；切换 Tab 不破坏 backstack
 * - Home → Folder 直跳：用 [pendingFolder] state 把意图从 HomeTabHost 传递到 FilesTabHost
 */
@Composable
private fun MainShell(
    onLoggedOut: () -> Unit,
    onOpenTransferCenter: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCreateShare: (List<String>) -> Unit,
    onOpenCreateLink: (itemId: String, fileName: String) -> Unit,
    onOpenAccessLogs: (linkId: String, fileName: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLinks: () -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    viewModel: com.x.cloudnext.android.viewmodel.MainShellViewModel = hiltViewModel(),
) {
    var selected by rememberSaveable(stateSaver = TabSaver) {
        mutableStateOf(CnTab.Home)
    }
    // Home 点击文件夹时写入；切到 Files 后由 FilesTabHost 消费并清空
    var pendingFolder by remember { mutableStateOf<CnRoute.Folder?>(null) }

    // ✅ NavController 提到 MainShell 顶部：跨 Scaffold/Drawer 重组保持同一实例，
    //   解决"从 fullscreen 返回后 ☰ 概率性白屏"的根因（原本在 Scaffold lambda 内 remember）。
    val homeNav = rememberNavController()
    val filesNav = rememberNavController()
    val sharedNav = rememberNavController()
    val workspacesNav = rememberNavController()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // ---- 诊断日志：MainShell composition / tab / drawer 时序 ----
    DisposableEffect(Unit) {
        Timber.tag(TAG_SHELL).d("MainShell ENTER composition")
        onDispose { Timber.tag(TAG_SHELL).d("MainShell LEAVE composition") }
    }
    LaunchedEffect(selected) {
        Timber.tag(TAG_SHELL).d("MainShell tab=%s", selected)
    }
    LaunchedEffect(drawerState.currentValue) {
        Timber.tag(TAG_SHELL).d("MainShell drawer=%s", drawerState.currentValue)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // ✅ drawerContent 抽独立 Composable，参数显式传入，避免 lambda 捕获 ViewModel/State
            //   导致每次重组都重建闭包、与 Drawer 动画 race。
            MainShellDrawerContent(
                viewModel = viewModel,
                drawerState = drawerState,
                scope = scope,
                onSwitchTab = { tab -> selected = tab },
                onOpenLinks = onOpenLinks,
                onOpenTransferCenter = onOpenTransferCenter,
                onOpenTrash = onOpenTrash,
                onOpenSettings = onOpenSettings,
                onLoggedOut = {
                    viewModel.logout()
                    onLoggedOut()
                },
            )
        },
    ) {
        Scaffold(
            bottomBar = {
                CnBottomTabs(
                    selected = selected,
                    onSelect = { selected = it },
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
            // ✅ Tab 切换走 when(selected) 销毁式（保守稳妥）。
            //   关键修复其实在 NavController 已提升到 MainShell 顶部（行 402-405）：
            //   切回原 Tab 时 NavController 实例不变 → backstack 不丢；
            //   不像原代码在 Scaffold lambda 内 remember 会导致重组竞态白屏。
            //   之前试过 Tab 永驻 + zIndex/alpha 方案，在 BLAST surface 重建时序里
            //   会让 4 套 NavHost composition 同时活着而崩 —— 现回退保守方案。
            when (selected) {
                CnTab.Home -> HomeTabHost(
                    navController = homeNav,
                    onOpenDrawer = openDrawer,
                    padding = padding,
                    onRequestOpenFolder = { folder ->
                        pendingFolder = folder
                        selected = CnTab.Files
                    },
                    onOpenFile = onOpenFile,
                    onOpenSearch = onOpenSearch,
                    onSwitchTab = { tab -> selected = tab },
                    onOpenTrash = onOpenTrash,
                )
                CnTab.Files -> FilesTabHost(
                    navController = filesNav,
                    onOpenDrawer = openDrawer,
                    padding = padding,
                    pendingFolder = pendingFolder,
                    onPendingConsumed = { pendingFolder = null },
                    onOpenFile = onOpenFile,
                    onRequestShare = onOpenCreateShare,
                    onRequestCreateLink = onOpenCreateLink,
                    onOpenSearch = onOpenSearch,
                )
                CnTab.Shared -> SharedTabHost(
                    navController = sharedNav,
                    onOpenDrawer = openDrawer,
                    padding = padding,
                    onOpenFile = onOpenFile,
                    onOpenSearch = onOpenSearch,
                )
                CnTab.Workspaces -> WorkspacesTabHost(
                    navController = workspacesNav,
                    onOpenDrawer = openDrawer,
                    padding = padding,
                )
            }
        }
    }
}

/**
 * Drawer 内容抽 Composable：参数显式，避免在 MainShell lambda 里直接捕获 ViewModel/state
 * 导致每次重组都重建闭包 / 与 Drawer 动画时序竞态。
 */
@Composable
private fun MainShellDrawerContent(
    viewModel: com.x.cloudnext.android.viewmodel.MainShellViewModel,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onSwitchTab: (CnTab) -> Unit,
    onOpenLinks: () -> Unit,
    onOpenTransferCenter: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val displayLabel by viewModel.displayLabel.collectAsState()
    val sessionInfo by viewModel.sessionInfo.collectAsState()
    CnDrawer(
        userName = displayLabel,
        userEmail = sessionInfo?.email,
        onSwitchTab = { tab ->
            scope.launch { drawerState.close() }
            onSwitchTab(tab)
        },
        onLinksClick = {
            scope.launch { drawerState.close() }
            onOpenLinks()
        },
        onTransferClick = {
            scope.launch { drawerState.close() }
            onOpenTransferCenter()
        },
        onTrashClick = {
            scope.launch { drawerState.close() }
            onOpenTrash()
        },
        onSettingsClick = {
            scope.launch { drawerState.close() }
            onOpenSettings()
        },
        onLogoutClick = {
            scope.launch {
                drawerState.close()
                onLoggedOut()
            }
        },
    )
}

@Composable
private fun HomeTabHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    padding: PaddingValues,
    onRequestOpenFolder: (CnRoute.Folder) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onOpenSearch: () -> Unit,
    onSwitchTab: (CnTab) -> Unit,
    onOpenTrash: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = CnRoute.Home,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        composable<CnRoute.Home> {
            HomeScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenFolder = { item ->
                    onRequestOpenFolder(CnRoute.Folder(itemId = item.id, name = item.name))
                },
                onOpenFile = onOpenFile,
                onOpenSearch = onOpenSearch,
                onSwitchTab = onSwitchTab,
                onOpenTrash = onOpenTrash,
            )
        }
    }
}

@Composable
private fun FilesTabHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    padding: PaddingValues,
    pendingFolder: CnRoute.Folder?,
    onPendingConsumed: () -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onRequestShare: (List<String>) -> Unit,
    onRequestCreateLink: (itemId: String, fileName: String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    LaunchedEffect(pendingFolder) {
        val folder = pendingFolder ?: return@LaunchedEffect
        navController.navigate(folder)
        onPendingConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = CnRoute.MyFilesRoot,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        composable<CnRoute.MyFilesRoot> {
            MyFilesScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenFolder = { item ->
                    navController.navigate(CnRoute.Folder(itemId = item.id, name = item.name))
                },
                onOpenFile = onOpenFile,
                onRequestShare = onRequestShare,
                onRequestCreateLink = onRequestCreateLink,
                onOpenSearch = onOpenSearch,
            )
        }
        composable<CnRoute.Folder> { entry ->
            val folder: CnRoute.Folder = entry.toRoute()
            FolderScreen(
                folderName = folder.name,
                onBack = { navController.popBackStack() },
                onOpenFolder = { item ->
                    navController.navigate(CnRoute.Folder(itemId = item.id, name = item.name))
                },
                onOpenFile = onOpenFile,
                onBreadcrumb = { id ->
                    popToFolder(navController, targetId = id)
                },
                onRequestShare = onRequestShare,
                onRequestCreateLink = onRequestCreateLink,
                onOpenSearch = onOpenSearch,
            )
        }
    }
}

@Composable
private fun SharedTabHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    padding: PaddingValues,
    onOpenFile: (DriveItem) -> Unit,
    onOpenSearch: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = CnRoute.SharedHost,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        composable<CnRoute.SharedHost> {
            SharedHostScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenSearch = onOpenSearch,
                onOpenIncomingFolder = { shareId, itemId, name ->
                    navController.navigate(
                        CnRoute.IncomingFolder(shareId = shareId, itemId = itemId, name = name),
                    )
                },
                onOpenFile = onOpenFile,
            )
        }
        composable<CnRoute.IncomingFolder> { entry ->
            val r: CnRoute.IncomingFolder = entry.toRoute()
            IncomingFolderScreen(
                name = r.name,
                onBack = { navController.popBackStack() },
                onOpenFile = onOpenFile,
                onOpenFolder = { shareId, itemId, name ->
                    navController.navigate(
                        CnRoute.IncomingFolder(shareId = shareId, itemId = itemId, name = name),
                    )
                },
            )
        }
    }
}

@Composable
private fun WorkspacesTabHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    padding: PaddingValues,
) {
    // Workspaces Tab 只承载 WorkspacesRoot；进入空间后走顶层 fullscreen route
    // （WorkspaceDetail/Folder/Members 在 CloudNextNavHost 顶层注册），
    // 这样成员管理 → OrgPicker SINGLE → 回传可以走根 navController。
    NavHost(
        navController = navController,
        startDestination = CnRoute.WorkspacesRoot,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        composable<CnRoute.WorkspacesRoot> {
            val rootNav = LocalRootNav.current
            WorkspacesScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenWorkspace = { id, name ->
                    rootNav.navigate(CnRoute.WorkspaceDetail(workspaceId = id, name = name))
                },
            )
        }
    }
}

/**
 * 面包屑回跳：把 backstack 顶部不断 pop 掉，直到当前栈顶是目标文件夹。
 */
private fun popToFolder(navController: NavHostController, targetId: String?) {
    if (targetId == null) {
        navController.popBackStack(route = CnRoute.MyFilesRoot, inclusive = false)
        return
    }
    repeat(32) {
        val current = navController.currentBackStackEntry ?: return
        val folder = runCatching { current.toRoute<CnRoute.Folder>() }.getOrNull()
        if (folder?.itemId == targetId) return
        if (!navController.popBackStack()) return
    }
}

private val TabSaver: androidx.compose.runtime.saveable.Saver<CnTab, String> =
    androidx.compose.runtime.saveable.Saver(
        save = { it.name },
        restore = { runCatching { CnTab.valueOf(it) }.getOrDefault(CnTab.Home) },
    )

/**
 * 文件预览路由器：按 fileType 决定跳哪个 Screen；文件夹 / 不支持的类型 Toast 提示。
 */
private fun openPreview(navController: NavHostController, context: Context, item: DriveItem) {
    if (item.isFolder) {
        Toast.makeText(context, "请在「文件」页打开该文件夹", Toast.LENGTH_SHORT).show()
        return
    }
    val target = resolvePreviewTarget(item.name, item.fileType)
    val route: CnRoute? = when (target) {
        PreviewTarget.Image -> CnRoute.ImagePreview(item.id, item.name)
        PreviewTarget.Pdf -> CnRoute.PdfPreview(item.id, item.name)
        PreviewTarget.Video -> CnRoute.VideoPreview(item.id, item.name)
        PreviewTarget.Office -> CnRoute.OnlyofficeWeb(item.id, item.name, mode = "view")
        PreviewTarget.Text -> CnRoute.TextPreview(item.id, item.name)
        null -> null
    }
    if (route != null) {
        navController.navigate(route)
    } else {
        Toast.makeText(context, "暂不支持预览：${item.name}", Toast.LENGTH_SHORT).show()
    }
}
