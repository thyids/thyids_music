package com.thyids.free_music

import android.Manifest
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thyids.free_music.data.PlayMode
import com.thyids.free_music.data.Playlist
import com.thyids.free_music.data.Song
import com.thyids.free_music.presentation.MusicViewModel
import com.thyids.free_music.ui.theme.GlassShapes
import com.thyids.free_music.ui.theme.GlassPane
import com.thyids.free_music.ui.theme.LocalBackdropViewport
import com.thyids.free_music.ui.theme.ProvideBackdropViewport
import com.thyids.free_music.ui.theme.drawAppBackdrop
import com.thyids.free_music.ui.theme.liquidGlass
import com.thyids.free_music.ui.theme.rememberBackdropPalette
import com.thyids.free_music.ui.theme.pressScale
import com.thyids.free_music.ui.theme.免费音乐Theme

class MainActivity : ComponentActivity() {
    private val musicViewModel: MusicViewModel by viewModels()
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            musicViewModel.startSpeakerDiscovery()
        } else {
            Toast.makeText(
                this,
                "需要本地网络权限才能发现和连接远程音箱",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            musicViewModel.startSpeakerDiscovery()
        }
        enableEdgeToEdge()
        setContent {
            // The glass surfaces are designed against a light backdrop, so the
            // palette is pinned to light, and pinned to the app's own violet and
            // pink rather than wallpaper colours, regardless of system settings.
            免费音乐Theme(darkTheme = false, dynamicColor = false) {
                WearApp(viewModel = musicViewModel)
            }
        }
    }
}

sealed class Screen {
    data object Search : Screen()
    data object Playlists : Screen()
    data class PlaylistDetail(val playlistId: String) : Screen()
    data object Player : Screen()
    data object Speaker : Screen()
}

// ─── Backdrop ────────────────────────────────────────────────────────────────

/**
 * A pale canvas carrying four wide colour fields. They are deliberately soft,
 * but they are real colour: a pane of glass can only look like glass if there
 * is something behind it to bend.
 */
@Composable
fun LiquidBackground() {
    val palette = rememberBackdropPalette()
    val viewport = LocalBackdropViewport.current

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport.size = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        // Static on purpose: an animated full-screen backdrop forces the window
        // to redraw every frame, which is what makes taps feel delayed.
        drawAppBackdrop(palette, size)
    }
}

// ─── Main app ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearApp(viewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var screenStack by remember { mutableStateOf(listOf<Screen>(Screen.Search)) }
    // +1 when pushing deeper, -1 when going back. The old code guessed the
    // direction from the stack depth, which made every pop except the last one
    // animate as if it were a push.
    var forward by remember { mutableIntStateOf(1) }

    val currentScreen = screenStack.last()

    BackHandler(enabled = screenStack.size > 1) {
        forward = -1
        screenStack = screenStack.dropLast(1)
    }

    fun navigateTo(screen: Screen) {
        forward = 1
        screenStack = screenStack + screen
    }

    fun navigateBack() {
        if (screenStack.size > 1) {
            forward = -1
            screenStack = screenStack.dropLast(1)
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    ProvideBackdropViewport {
    LiquidBackground()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 104.dp)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (forward > 0) {
                        (
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.92f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { full -> full } +
                                fadeIn(tween(durationMillis = 240, delayMillis = 40))
                            ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 1f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { full -> -full / 5 } +
                                fadeOut(tween(durationMillis = 200)) +
                                scaleOut(targetScale = 0.97f, animationSpec = tween(durationMillis = 260))
                        )
                    } else {
                        (
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.92f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { full -> -full / 5 } +
                                fadeIn(tween(durationMillis = 240, delayMillis = 30)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(durationMillis = 280))
                            ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 1f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { full -> full } +
                                fadeOut(tween(durationMillis = 220)) +
                                scaleOut(targetScale = 0.97f, animationSpec = tween(durationMillis = 280))
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    is Screen.Playlists -> {
                        PlaylistsPage(
                            viewModel = viewModel,
                            onPlaylistClick = { playlist ->
                                if (screenStack.last() !is Screen.PlaylistDetail) {
                                    navigateTo(Screen.PlaylistDetail(playlist.id))
                                }
                            }
                        )
                    }

                    is Screen.PlaylistDetail -> {
                        val playlist = viewModel.playlists.find { it.id == screen.playlistId }
                        if (playlist != null) {
                            PlaylistDetailPage(
                                viewModel = viewModel,
                                playlist = playlist,
                                onBack = { navigateBack() },
                                onSongClick = { song ->
                                    val index = playlist.songs.indexOf(song)
                                    viewModel.playSongFromPlaylist(playlist, index)
                                    if (screenStack.last() !is Screen.Player) {
                                        navigateTo(Screen.Player)
                                    }
                                }
                            )
                        } else {
                            navigateBack()
                        }
                    }

                    is Screen.Player -> {
                        PlaybackScreen(
                            viewModel = viewModel,
                            onBack = { navigateBack() }
                        )
                    }

                    is Screen.Speaker -> {
                        SpeakerPage(
                            viewModel = viewModel,
                            onBack = { navigateBack() }
                        )
                    }

                    is Screen.Search -> {
                        SearchPage(
                            viewModel = viewModel,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onNavigateToPlaylists = {
                                if (screenStack.last() !is Screen.Playlists) {
                                    navigateTo(Screen.Playlists)
                                }
                            },
                            onNavigateToSpeaker = {
                                if (screenStack.last() !is Screen.Speaker) {
                                    navigateTo(Screen.Speaker)
                                }
                            },
                            onSongSelected = { song ->
                                viewModel.playSong(song)
                                if (screenStack.last() !is Screen.Player) {
                                    navigateTo(Screen.Player)
                                }
                            }
                        )
                    }
                }
            }
        }

        // The bar is permanent: it is part of the chrome, not a now-playing
        // affordance that appears and disappears.
        FloatingBottomNav(
            viewModel = viewModel,
            screenStack = screenStack,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { newScreen ->
            if (screenStack.lastOrNull() != newScreen) {
                navigateTo(newScreen)
            }
        }
    }

    viewModel.captchaUrl?.let { url ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.captchaUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("人机验证", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                        IconButton(onClick = { viewModel.captchaUrl = null }) {
                            Icon(Icons.Default.Add, contentDescription = "关闭", modifier = Modifier.rotate(45f))
                        }
                    }
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        // If the user finishes verification, they usually redirected or page changes
                                    }
                                }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
    }
}

// ─── Search page ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    viewModel: MusicViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToSpeaker: () -> Unit,
    onSongSelected: (Song) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = "免费音乐",
                onPlaylistClick = onNavigateToPlaylists,
                onSpeakerClick = onNavigateToSpeaker,
                isControllerMode = viewModel.isControllerMode
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GlassPane(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = GlassShapes.Pill,
                fillAlpha = 0.34f,
                shadowElevation = 14.dp,
                rimAlpha = 0.9f,
                backdropBlur = 14.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "搜索歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) viewModel.search(searchQuery)
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = GlassShapes.Pill,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (!viewModel.isLoading && viewModel.songs.isEmpty() && viewModel.searchPerformed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "未找到歌曲，请尝试其他关键词",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.captchaUrl = "https://www.gequbao.com/" }) {
                            Text("进行人机验证")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(viewModel.songs.size) { index ->
                        val song = viewModel.songs[index]
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(song.id) { visible = true }
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 320,
                                    delayMillis = (index.coerceAtMost(8)) * 35,
                                    easing = EaseInOut
                                )
                            ) { it / 3 } +
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 260,
                                        delayMillis = (index.coerceAtMost(8)) * 35
                                    )
                                ) +
                                scaleIn(
                                    initialScale = 0.94f,
                                    animationSpec = tween(
                                        durationMillis = 340,
                                        delayMillis = (index.coerceAtMost(8)) * 35,
                                        easing = EaseInOut
                                    )
                                )
                        ) {
                            SongCard(song = song, viewModel = viewModel) {
                                onSongSelected(song)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Glass top app bar ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: String,
    onPlaylistClick: () -> Unit = {},
    onSpeakerClick: () -> Unit = {},
    isControllerMode: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = onSpeakerClick) {
            Icon(
                imageVector = if (isControllerMode) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = "远程音箱",
                tint = if (isControllerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onPlaylistClick) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "歌单",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
) {
    GlassPane(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
        fillAlpha = 0.34f,
        shadowElevation = 12.dp,
        backdropBlur = 18.dp
    ) {
        TopAppBar(
            title = {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            actions = actions
        )
    }
}

// ─── Floating pill bottom nav ────────────────────────────────────────────────

@Composable
fun FloatingBottomNav(
    viewModel: MusicViewModel,
    screenStack: List<Screen>,
    modifier: Modifier = Modifier,
    onNavigate: (Screen) -> Unit
) {
    val current = screenStack.lastOrNull()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassPane(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = GlassShapes.Pill,
            fillAlpha = 0.38f,
            shadowElevation = 24.dp,
            rimAlpha = 0.95f,
            sheenAlpha = 0.30f,
            backdropBlur = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavButton(
                    icon = Icons.Default.Home,
                    label = "首页",
                    isSelected = current is Screen.Search,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.Search) }
                )
                NavButton(
                    icon = Icons.Default.LibraryMusic,
                    label = "歌单",
                    isSelected = current is Screen.Playlists || current is Screen.PlaylistDetail,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.Playlists) }
                )
                NowPlayingButton(
                    isPlaying = viewModel.isPlaying,
                    isActive = current is Screen.Player,
                    modifier = Modifier.weight(1.1f),
                    onClick = {
                        if (viewModel.currentSong != null) onNavigate(Screen.Player)
                    }
                )
                NavButton(
                    icon = Icons.Default.Explore,
                    label = "发现",
                    isSelected = false,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedTint = MaterialTheme.colorScheme.primary
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant
    val tint by animateColorAsState(
        targetValue = if (isSelected) selectedTint else idleTint,
        animationSpec = tween(durationMillis = 280),
        label = "navTint"
    )
    val lift by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navLift"
    )
    val press by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (press) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "navPress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(56.dp)
            .clip(GlassShapes.Pill)
            .background(selectedTint.copy(alpha = 0.10f * lift))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = -2.dp.toPx() * lift
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * The centre control. It is a solid accent lozenge rather than another glass
 * panel, which gives the bar exactly one focal point. A halo pulses outward
 * while audio is playing.
 */
@Composable
fun NowPlayingButton(
    isPlaying: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val press by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (press) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "playPress"
    )
    val activeScale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playActive"
    )

    Box(
        modifier = modifier.height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // The halo only exists while audio is actually running, so an idle app
        // schedules no frames at all.
        if (isPlaying) {
            // Static halo: a pulsing one redraws the whole window every frame,
            // which is exactly the "everything feels slow" trap.
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .graphicsLayer { alpha = 0.16f }
                    .clip(GlassShapes.Circle)
                    .background(primary)
            )
        }
        Box(
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    scaleX = pressScale * activeScale
                    scaleY = pressScale * activeScale
                }
                .clip(GlassShapes.Circle)
                .background(
                    Brush.linearGradient(
                        colors = listOf(primary, tertiary),
                        start = Offset.Zero,
                        end = Offset(50f, 50f)
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(initialScale = 0.6f, animationSpec = tween(200)) + fadeIn(tween(200)))
                        .togetherWith(
                            scaleOut(targetScale = 0.6f, animationSpec = tween(140)) + fadeOut(tween(140))
                        )
                },
                label = "playIcon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ─── Song card (liquid glass) ────────────────────────────────────────────────

@Composable
fun SongCard(
    song: Song,
    viewModel: MusicViewModel,
    playlistId: String? = null,
    onPlay: () -> Unit
) {
    val isFav = viewModel.isFavorite(song.id)
    val isCached = viewModel.isSongCached(song.id)

    GlassPane(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassShapes.Card,
        fillAlpha = 0.34f,
        shadowElevation = 11.dp,
        pressedScale = 0.975f,
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(GlassShapes.Tile)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCached) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "已缓存",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlistId != null) {
                    IconButton(onClick = { viewModel.removeSongFromPlaylist(playlistId, song.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "移出歌单",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                val favTint by animateColorAsState(
                    targetValue = if (isFav) Color(0xFFE0356B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 260),
                    label = "favTint"
                )
                val favScale by animateFloatAsState(
                    targetValue = if (isFav) 1f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "favScale"
                )
                IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = favTint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = favScale
                                scaleY = favScale
                            }
                    )
                }
                IconButton(onClick = { viewModel.showAddToPlaylistDialog(song) }) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "添加到歌单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBarWithBack(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    GlassPane(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
        fillAlpha = 0.34f,
        shadowElevation = 12.dp,
        backdropBlur = 18.dp
    ) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }
}

// ─── Playlist card ───────────────────────────────────────────────────────────

@Composable
fun PlaylistCard(
    playlist: Playlist,
    viewModel: MusicViewModel,
    onClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassPane(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassShapes.Card,
        fillAlpha = 0.34f,
        shadowElevation = 11.dp,
        pressedScale = 0.975f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(GlassShapes.Tile)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${playlist.songs.size} 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!playlist.isFavorite) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除歌单",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("删除歌单") },
            text = { Text("确定要删除歌单 \"${playlist.name}\" 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    showDeleteConfirm = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

// ─── Playlists page ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsPage(
    viewModel: MusicViewModel,
    onPlaylistClick: (Playlist) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showJsonImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = "我的歌单",
                onPlaylistClick = {},
                actions = {
                    IconButton(onClick = { showJsonImportDialog = true }) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "导入 JSON",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "文字导入",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = "新建歌单",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val displayPlaylists = viewModel.playlists

        if (displayPlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "暂无歌单",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新建", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { showJsonImportDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
                            )
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导入 JSON", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                items(displayPlaylists) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        viewModel = viewModel,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        }
    }

    if (showJsonImportDialog) {
        var jsonText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJsonImportDialog = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("JSON 导入歌单") },
            text = {
                Column {
                    Text("请粘贴导出的歌单 JSON 数据：", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        label = { Text("JSON 数据") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (jsonText.isNotBlank()) {
                        viewModel.importPlaylist(jsonText.trim())
                        showJsonImportDialog = false
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showJsonImportDialog = false }) { Text("取消") }
            }
        )
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createPlaylist(name.trim())
                        showCreateDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    if (showImportDialog) {
        var inputText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("文字导入歌单") },
            text = {
                Column {
                    Text("每行输入一首歌曲（如：歌手 歌名）：", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("歌曲列表") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.startTextImport(inputText.trim())
                        showImportDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }

    if (viewModel.importProgress.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!viewModel.isImporting) viewModel.importProgress = emptyList() },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text(if (viewModel.isImporting) "正在处理歌单..." else "导入预览") },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(viewModel.importProgress) { (query, status) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(query, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    status,
                                    color = when {
                                        status.startsWith("成功") -> MaterialTheme.colorScheme.primary
                                        status == "未找到" || status == "失败" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!viewModel.isImporting && viewModel.showImportNamingDialog) {
                    TextButton(onClick = {}) { Text("完成") }
                }
            },
            dismissButton = {
                if (!viewModel.isImporting) {
                    TextButton(onClick = { viewModel.importProgress = emptyList() }) { Text("关闭") }
                }
            }
        )
    }

    if (viewModel.showImportNamingDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showImportNamingDialog = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("保存歌单") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.finalizeImport(playlistName) }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showImportNamingDialog = false }) { Text("取消") }
            }
        )
    }
}

// ─── Playlist detail page ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailPage(
    viewModel: MusicViewModel,
    playlist: Playlist,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBarWithBack(
                title = playlist.name,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showMergeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "合并歌单",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "导出歌单",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!playlist.isFavorite) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除歌单",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (playlist.songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("歌单为空", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = { if (playlist.songs.isNotEmpty()) onSongClick(playlist.songs[0]) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("播放全部", fontWeight = FontWeight.SemiBold)
                    }
                }
                items(playlist.songs) { song ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 3 }
                    ) {
                        SongCard(
                            song = song,
                            viewModel = viewModel,
                            playlistId = playlist.id,
                            onPlay = { onSongClick(song) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("删除歌单") },
            text = { Text("确定要删除歌单 \"${playlist.name}\" 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showMergeDialog) {
        val otherPlaylists = viewModel.playlists.filter { it.id != playlist.id }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("合并歌单") },
            text = {
                Column {
                    Text("请选择要合并进当前歌单的源歌单：", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (otherPlaylists.isEmpty()) {
                        Text("没有其他歌单可供合并")
                    } else {
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(otherPlaylists) { other ->
                                TextButton(
                                    onClick = {
                                        viewModel.mergePlaylists(playlist.id, other.id)
                                        showMergeDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(other.name)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text("取消") }
            }
        )
    }

    if (showExportDialog) {
        val json = viewModel.exportPlaylist(playlist)
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("导出歌单") },
            text = {
                Column {
                    Text("歌单JSON数据已生成：", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = json,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("JSON 数据") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Playlist JSON", json)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showExportDialog = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("关闭") }
            }
        )
    }
}

// ─── Playback screen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isFav = viewModel.currentSong?.let { viewModel.isFavorite(it.id) } ?: false
    val isCached = viewModel.currentSong?.let { viewModel.isSongCached(it.id) } ?: false

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = isCached,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut()
            ) {
                Text(
                    text = "已离线",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(GlassShapes.Pill)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Album disc: a solid gradient puck inside a glass rim. Rotation runs on
        // an Animatable so a pause freezes the disc instead of rewinding it.
        val discRotation = remember { Animatable(0f) }
        LaunchedEffect(viewModel.isPlaying) {
            if (viewModel.isPlaying) {
                while (true) {
                    discRotation.animateTo(
                        targetValue = discRotation.value + 360f,
                        animationSpec = tween(durationMillis = 14000, easing = LinearEasing)
                    )
                }
            }
        }

        GlassPane(
            modifier = Modifier.size(248.dp),
            shape = GlassShapes.Circle,
            fillAlpha = 0.26f,
            shadowElevation = 26.dp,
            rimAlpha = 0.95f,
            sheenAlpha = 0.30f,
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .clip(GlassShapes.Circle)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            ),
                            center = Offset(0.35f, 0.3f),
                            radius = 300f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(discRotation.value)
                ) {
                    val radius = size.minDimension / 2f
                    repeat(7) { index ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.07f),
                            radius = radius * (0.24f + index * 0.105f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = radius * 0.18f
                    )
                }
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(GlassShapes.Circle)
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }
        }

        // Song info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = viewModel.currentSong?.title ?: "",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = viewModel.currentSong?.artist ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val progress = if (viewModel.duration > 0) viewModel.currentPosition.toFloat() / viewModel.duration else 0f
        var isScrubbing by remember { mutableStateOf(false) }
        var sliderPosition by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(progress, isScrubbing) {
            if (!isScrubbing) sliderPosition = progress
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Slider(
                value = sliderPosition,
                onValueChange = {
                    isScrubbing = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    viewModel.seekTo((sliderPosition * viewModel.duration).toLong())
                    isScrubbing = false
                },
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(GlassShapes.Circle)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(GlassShapes.Circle)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                track = { sliderState ->
                    val fraction = (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(GlassShapes.Pill)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(GlassShapes.Pill)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        )
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(viewModel.currentPosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatTime(viewModel.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Transport controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            GlassIconButton(
                onClick = { viewModel.playPrevious() },
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }

            PrimaryPlayButton(
                isPlaying = viewModel.isPlaying,
                onClick = { viewModel.togglePlayPause() }
            )

            GlassIconButton(
                onClick = { viewModel.playNext() },
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Secondary actions live on one glass rail so they read as a group.
        GlassPane(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = GlassShapes.Pill,
            fillAlpha = 0.34f,
            shadowElevation = 16.dp,
            rimAlpha = 0.9f,
            backdropBlur = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val favTint by animateColorAsState(
                    targetValue = if (isFav) Color(0xFFE0356B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 260),
                    label = "playerFavTint"
                )
                GlassIconButton(
                    onClick = { viewModel.currentSong?.let { viewModel.toggleFavorite(it) } },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = favTint
                    )
                }
                GlassIconButton(
                    onClick = { viewModel.currentSong?.let { viewModel.showAddToPlaylistDialog(it) } },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "添加到歌单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassIconButton(
                    onClick = { viewModel.cacheCurrentSong() },
                    enabled = !viewModel.isCaching,
                    modifier = Modifier.size(46.dp)
                ) {
                    if (viewModel.isCaching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = if (isCached) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                            contentDescription = "离线缓存",
                            tint = if (isCached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GlassIconButton(
                    onClick = {
                        val url = viewModel.currentPlayUrl
                        val song = viewModel.currentSong
                        if (url != null && song != null) {
                            downloadSong(context, url, song.title, song.artist)
                        } else {
                            Toast.makeText(context, "暂无播放链接", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (viewModel.showPlaylistDialog) {
        val nonFavPlaylists = viewModel.playlists.filter { !it.isFavorite }
        AlertDialog(
            onDismissRequest = {
                viewModel.showPlaylistDialog = false
                viewModel.pendingSongForPlaylist = null
            },
            containerColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("添加到歌单") },
            text = {
                if (nonFavPlaylists.isEmpty()) {
                    Text("暂无歌单，请先创建歌单")
                } else {
                    LazyColumn {
                        items(nonFavPlaylists) { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addSongToPlaylist(playlist.id)
                                    viewModel.showPlaylistDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    viewModel.showPlaylistDialog = false
                    viewModel.pendingSongForPlaylist = null
                }) { Text("取消") }
            }
        )
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glassIconScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .liquidGlass(
                shape = GlassShapes.Circle,
                fillAlpha = 0.52f,
                shadowElevation = 12.dp,
                rimAlpha = 0.92f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * The single strongest control in the app, so it gets the only fully saturated
 * fill plus a halo that breathes while audio is playing.
 */
@Composable
fun PrimaryPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "primaryPress"
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        if (isPlaying) {
            val breathe = rememberInfiniteTransition(label = "breathe")
            val pulse by breathe.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Box(
                modifier = Modifier
                    .size(64.dp + 10.dp * pulse)
                    .graphicsLayer { alpha = 0.18f + 0.10f * (1f - pulse) }
                    .clip(GlassShapes.Circle)
                    .background(primary)
            )
        }
        Box(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .liquidGlass(
                    shape = GlassShapes.Circle,
                    fillAlpha = 0.10f,
                    shadowElevation = 22.dp,
                    rimAlpha = 0.95f,
                    sheenAlpha = 0.35f
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(primary, tertiary),
                        start = Offset.Zero,
                        end = Offset(68f, 68f)
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(initialScale = 0.55f, animationSpec = tween(220)) + fadeIn(tween(200)))
                        .togetherWith(
                            scaleOut(targetScale = 0.55f, animationSpec = tween(150)) + fadeOut(tween(150))
                        )
                },
                label = "primaryIcon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

private fun downloadSong(context: Context, url: String, title: String, artist: String) {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = Uri.parse(url)
    val fileName = "${title} - ${artist}.mp3".replace(Regex("[/\\\\:*?\"<>|]"), "_")

    val request = DownloadManager.Request(uri)
        .setTitle(title)
        .setDescription("正在下载: $title - $artist")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "免费音乐/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    downloadManager.enqueue(request)
    Toast.makeText(context, "已开始下载: $fileName", Toast.LENGTH_SHORT).show()
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerPage(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    if (viewModel.isSpeakerMode) {
        SpeakerLockScreen(viewModel)
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBarWithBack(
                title = "远程音箱",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "远程音箱功能允许你通过局域网在另一台设备上播放音乐，避免直接传输音频导致的卡顿。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassPane(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassShapes.Card,
                fillAlpha = 0.3f,
                onClick = { viewModel.startSpeakerMode() }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CellTower,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("设为音箱端", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("此设备将接收并播放其他设备的指令", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("局域网内的音箱：", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start))

            if (viewModel.discoveredSpeakers.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("正在寻找音箱...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.discoveredSpeakers) { ip ->
                        val isConnected = viewModel.connectedSpeakerIp == ip
                        GlassPane(
                            modifier = Modifier.fillMaxWidth(),
                            shape = GlassShapes.Card,
                            fillAlpha = if (isConnected) 0.5f else 0.2f,
                            onClick = {
                                if (isConnected) viewModel.disconnectFromSpeaker()
                                else viewModel.connectToSpeaker(ip)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ip, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (isConnected) "已连接" else "点击连接",
                                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakerLockScreen(viewModel: MusicViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "音箱模式运行中",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val song = viewModel.currentSong
            if (song != null) {
                Text("正在播放：", color = Color.Gray)
                Text(song.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                Text("等待指令...", color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { viewModel.stopSpeakerMode() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("退出音箱模式")
            }
        }
    }
}
