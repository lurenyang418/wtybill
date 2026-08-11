package tech.wtybill.app

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.TimeUnit
import tech.wtybill.app.config.AppConfig
import tech.wtybill.app.data.douyu.StreamOptions
import tech.wtybill.app.data.douyu.StreamRate
import tech.wtybill.app.data.douyu.CdnLine
import tech.wtybill.app.danmaku.DanmakuBuffer
import tech.wtybill.app.danmaku.DanmakuTrackAllocator
import tech.wtybill.app.danmaku.DanmakuTrackSnapshot
import tech.wtybill.app.danmaku.DouyuDanmakuClient
import tech.wtybill.app.player.PlaybackViewModel
import tech.wtybill.app.settings.SettingsRepository
import tech.wtybill.app.settings.UserSettings
import tech.wtybill.app.net.NetworkMonitor
import tech.wtybill.app.net.NetworkClients
import tech.wtybill.app.ui.room.RoomUiState
import tech.wtybill.app.ui.room.RoomViewModel

class MainActivity : ComponentActivity() {
    private var refreshRoom: (() -> Unit)? = null
    private var stopPlayback: (() -> Unit)? = null
    private var releasePlayback: (() -> Unit)? = null
    private var recoverPlayback: ((Boolean) -> Unit)? = null
    private var playbackActive: (() -> Boolean)? = null
    private var resumeAfterForeground = false
    private var recoveryPending = false
    private var allowBackgroundAudio = false
    private var enteringPictureInPicture = false
    private var networkMonitor: NetworkMonitor? = null

    override fun onStart() {
        super.onStart()
        networkMonitor = NetworkMonitor(this) {
            runOnUiThread {
                refreshRoom?.invoke()
                recoverPlayback?.invoke(false)
            }
        }.also { it.start() }
        if (resumeAfterForeground || playbackActive?.invoke() == true || recoverPlayback == null) {
            resumeAfterForeground = false
            if (recoverPlayback == null) recoveryPending = true else recoverPlayback?.invoke(false)
        }
    }

    override fun onStop() {
        // Keep the service/player alive across configuration recreation. The
        // ViewModel and MediaController are reused by the replacement Activity.
        if (shouldReleasePlaybackOnStop(
                isChangingConfigurations = isChangingConfigurations,
                isInPictureInPictureMode = isInPictureInPictureMode,
                enteringPictureInPicture = enteringPictureInPicture,
                allowBackgroundAudio = allowBackgroundAudio,
            )
        ) {
            if (playbackActive?.invoke() == true) resumeAfterForeground = true
            stopPlayback?.invoke()
            releasePlayback?.invoke()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        networkMonitor?.stop()
        networkMonitor = null
        super.onStop()
    }

    private fun enterVideoPictureInPicture() {
        enteringPictureInPicture = true
        val sourceRect = Rect().also { window.decorView.getGlobalVisibleRect(it) }
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(true)
                .setSourceRectHint(sourceRect)
                .build(),
        )
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) enteringPictureInPicture = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: RoomViewModel = viewModel()
            refreshRoom = vm::refresh
            val state by vm.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val playbackViewModel: PlaybackViewModel = viewModel()
            val coordinator = playbackViewModel.coordinator
            val settingsRepository = remember { SettingsRepository(this@MainActivity) }
            val imageLoader = remember {
                ImageLoader.Builder(this@MainActivity)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { NetworkClients.image })) }
                    .build()
            }
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            allowBackgroundAudio = settings.backgroundAudio
            val activeRoomId = (state as? RoomUiState.Ready)?.room?.roomId
                ?: playbackViewModel.activeRoomId
                ?: AppConfig.ROOM_ID
            stopPlayback = coordinator::stop
            val danmakuTracks = remember { mutableStateOf(emptyList<DanmakuTrackSnapshot>()) }
            val danmakuBuffer = remember { DanmakuBuffer(100) }
            val danmakuTrackAllocator = remember { DanmakuTrackAllocator(6, 6) }
            val danmaku = remember {
                DouyuDanmakuClient(scope = scope, onMessage = { message ->
                    scope.launch {
                        danmakuBuffer.add(message)
                        danmakuTracks.value = danmakuTrackAllocator.add(message)
                    }
                })
            }
            var controller by remember { mutableStateOf<MediaController?>(null) }
            var streamError by remember { mutableStateOf<String?>(null) }
            var streamOptions by remember { mutableStateOf<StreamOptions?>(null) }
            var privacyPageVisible by remember { mutableStateOf(false) }
            var danmakuSettingsVisible by remember { mutableStateOf(false) }
            var controlsVisible by remember { mutableStateOf(true) }
            var fullscreen by remember { mutableStateOf(false) }
            var hasActivePlayback by remember { mutableStateOf(playbackViewModel.hasActivePlayback) }
            var isPlaying by remember { mutableStateOf(false) }
            var isMuted by remember { mutableStateOf(false) }
            var recoveryJob by remember { mutableStateOf<Job?>(null) }
            stopPlayback = {
                coordinator.stop()
            }
            releasePlayback = {
                coordinator.release()
                controller = null
            }
            playbackActive = { hasActivePlayback }
            recoverPlayback = { skipActiveCandidate ->
                if (hasActivePlayback && recoveryJob?.isActive != true) {
                    val launched = scope.launch {
                        controller = try {
                            coordinator.connect()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        }
                        if (controller == null) {
                            streamError = "播放器服务连接失败"
                            return@launch
                        }
                        streamError = coordinator.play(
                            activeRoomId,
                            settings.preferredRate,
                            settings.preferredCdn,
                            skipActiveCandidate = skipActiveCandidate,
                        ).exceptionOrNull()?.message
                    }
                    recoveryJob = launched
                    launched.invokeOnCompletion {
                        if (recoveryJob === launched) recoveryJob = null
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                runCatching { coordinator.connect() }
                    .onSuccess { controller = it }
                if (recoveryPending) {
                    recoveryPending = false
                    recoverPlayback?.invoke(false)
                }
            }
            androidx.compose.runtime.DisposableEffect(controller) {
                val player = controller
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        recoverPlayback?.invoke(true)
                    }
                }
                player?.addListener(listener)
                onDispose { player?.removeListener(listener) }
            }
            androidx.compose.runtime.LaunchedEffect(state) {
                when (val roomState = state) {
                    is RoomUiState.Ready -> danmaku.start(roomState.room.roomId)
                    is RoomUiState.Offline, is RoomUiState.Error -> {
                        danmaku.stop()
                        if (hasActivePlayback) {
                            coordinator.stop()
                            hasActivePlayback = false
                            playbackViewModel.markPlaybackStopped()
                            isPlaying = false
                            isMuted = false
                        }
                    }
                    RoomUiState.Idle, RoomUiState.Loading -> Unit
                }
            }
            androidx.compose.runtime.LaunchedEffect(state) {
                streamOptions = if (state is RoomUiState.Ready) {
                    runCatching { coordinator.loadStreamOptions((state as RoomUiState.Ready).room.roomId) }.getOrNull()
                } else null
            }
            androidx.compose.runtime.LaunchedEffect(hasActivePlayback, isPlaying, controlsVisible) {
                if (hasActivePlayback && isPlaying && controlsVisible) {
                    delay(4_000)
                    controlsVisible = false
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    recoverPlayback = null
                    stopPlayback = null
                    releasePlayback = null
                    playbackActive = null
                    danmakuBuffer.clear()
                    danmakuTrackAllocator.clear()
                    danmaku.stop()
                }
            }
            MaterialTheme(colorScheme = darkColorScheme(
                primary = BrandPurple,
                onPrimary = Color.White,
                background = Ink,
                surface = Panel,
                onSurface = TextPrimary,
                onSurfaceVariant = TextMuted,
                error = ErrorRed,
            )) {
                Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                    if (privacyPageVisible) {
                        PrivacyLicensePage(onBack = { privacyPageVisible = false })
                    } else {
                        Column(
                            Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.safeDrawingPadding()),
                        ) {
                        if (!fullscreen) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(AppConfig.PROJECT_NAME, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Text("LIVE", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.weight(1f))
                                Text("斗鱼 · ${AppConfig.ROOM_ID}", color = TextMuted, fontSize = 12.sp)
                            }
                        }

                        Column(
                            Modifier.fillMaxSize().weight(1f).then(if (fullscreen) Modifier else Modifier.verticalScroll(rememberScrollState())),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                                    .clip(if (fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp))
                                    .background(PlayerBlack),
                            ) {
                                if (controller != null) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "斗鱼 ${AppConfig.ROOM_ID} 直播视频" },
                                        factory = { context ->
                                            (LayoutInflater.from(context).inflate(R.layout.player_view, null) as PlayerView).apply {
                                                setOnClickListener { controlsVisible = true }
                                            }
                                        },
                                        update = {
                                            it.player = controller
                                            it.setOnClickListener { controlsVisible = true }
                                        },
                                    )
                                    if (settings.danmakuEnabled) {
                                        tech.wtybill.app.ui.room.DanmakuOverlay(
                                            tracks = danmakuTracks.value,
                                            textSize = settings.danmakuTextSize,
                                            opacity = settings.danmakuOpacity,
                                        )
                                    }
                                }
                                if ((controller == null || !hasActivePlayback) && streamOptions == null) {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("●", color = LiveRed, fontSize = 28.sp)
                                        Text("直播画面待播放", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        Text("点击下方按钮开始观看", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                                if (hasActivePlayback && isPlaying && !controlsVisible) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().clickable { controlsVisible = true },
                                    )
                                }
                                val readyRoom = state as? RoomUiState.Ready
                                val readyOptions = streamOptions
                                if (readyRoom != null && readyOptions != null && (!hasActivePlayback || !isPlaying || controlsVisible)) {
                                    val selectedRate = readyOptions.rates.firstOrNull { it.rate == settings.preferredRate } ?: readyOptions.rates.firstOrNull()
                                    val selectedCdn = readyOptions.cdns.firstOrNull { it.code == settings.preferredCdn } ?: readyOptions.cdns.firstOrNull()
                                    PlayerControls(
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                        isPlaying = hasActivePlayback && isPlaying,
                                        isMuted = isMuted,
                                        selectedRate = selectedRate?.name ?: "自动",
                                        selectedCdn = selectedCdn?.name ?: selectedCdn?.code ?: "自动",
                                        rates = readyOptions.rates,
                                        cdns = readyOptions.cdns,
                                        onPlay = {
                                            scope.launch {
                                                if (hasActivePlayback) {
                                                    if (isPlaying) controller?.pause() else controller?.play()
                                                } else {
                                                    streamError = runCatching {
                                                        coordinator.play(readyRoom.room.roomId, settings.preferredRate, settings.preferredCdn).getOrThrow()
                                                        hasActivePlayback = true
                                                        playbackViewModel.markPlaybackStarted(readyRoom.room.roomId)
                                                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                                    }.exceptionOrNull()?.message
                                                }
                                            }
                                        },
                                        onMute = {
                                            controller?.volume = if (isMuted) 1f else 0f
                                            isMuted = !isMuted
                                        },
                                        muteEnabled = hasActivePlayback,
                                        onSelectRate = { rate ->
                                            scope.launch {
                                                settingsRepository.setPreferredStream(rate.rate, selectedCdn?.code)
                                                if (hasActivePlayback) {
                                                    val result = coordinator.play(readyRoom.room.roomId, rate.rate, selectedCdn?.code)
                                                    streamError = result.exceptionOrNull()?.message
                                                    if (result.isSuccess) playbackViewModel.markPlaybackStarted(readyRoom.room.roomId)
                                                }
                                            }
                                        },
                                            onSelectCdn = { cdn ->
                                            scope.launch {
                                                settingsRepository.setPreferredStream(selectedRate?.rate, cdn.code)
                                                if (hasActivePlayback) {
                                                    val result = coordinator.play(readyRoom.room.roomId, selectedRate?.rate, cdn.code)
                                                    streamError = result.exceptionOrNull()?.message
                                                    if (result.isSuccess) playbackViewModel.markPlaybackStarted(readyRoom.room.roomId)
                                                }
                                                }
                                            },
                                            danmakuEnabled = settings.danmakuEnabled,
                                            backgroundAudio = settings.backgroundAudio,
                                            onRefresh = vm::refresh,
                                        onPip = ::enterVideoPictureInPicture,
                                            onFullscreen = {
                                                val entering = !fullscreen
                                                fullscreen = entering
                                                controlsVisible = true
                                                requestedOrientation = if (entering) {
                                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                } else {
                                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                }
                                                window.decorView.systemUiVisibility = if (entering) {
                                                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                                } else 0
                                            },
                                            onDanmaku = { scope.launch { settingsRepository.setDanmakuEnabled(!settings.danmakuEnabled) } },
                                            onDanmakuSettings = { danmakuSettingsVisible = true },
                                            onBackgroundAudio = { scope.launch { settingsRepository.setBackgroundAudio(!settings.backgroundAudio) } },
                                        )
                                    }
                            }

                            if (!fullscreen) {
                                when (val current = state) {
                                    RoomUiState.Idle, RoomUiState.Loading -> LoadingBlock()
                                    is RoomUiState.Offline -> {
                                        OfflineBlock(current.room.anchorName, current.room.title, vm::refresh)
                                    }
                                    is RoomUiState.Error -> {
                                        ErrorBlock(current.message, vm::refresh)
                                    }
                                    is RoomUiState.Ready -> {
                                        RoomHeader(
                                            title = current.room.title.ifBlank { "${AppConfig.PROJECT_NAME} 直播间" },
                                            anchor = current.room.anchorName.ifBlank { AppConfig.PROJECT_NAME },
                                            hot = formatHot(current.room.hot),
                                            duration = formatDuration(current.showTime),
                                            avatar = current.room.anchorAvatar,
                                            imageLoader = imageLoader,
                                        )
                                    }
                                }
                                PrivacySetting(onClick = { privacyPageVisible = true })
                                streamError?.let { StreamErrorCard(it) }
                                Spacer(Modifier.height(28.dp))
                            }
                        }
                    }
                }
                }
                if (danmakuSettingsVisible && !privacyPageVisible) {
                    AlertDialog(
                        onDismissRequest = { danmakuSettingsVisible = false },
                        title = { Text("弹幕设置") },
                        text = {
                            Column {
                                Text("调整弹幕在画面上的显示效果", color = TextMuted, fontSize = 12.sp)
                                SettingSlider("弹幕字号", "${settings.danmakuTextSize}", settings.danmakuTextSize.toFloat(), 10f..32f, 10) { value ->
                                    scope.launch { settingsRepository.setDanmakuTextSize(value.toInt()) }
                                }
                                SettingSlider("弹幕透明度", "${(settings.danmakuOpacity * 100).toInt()}%", settings.danmakuOpacity, 0.1f..1f, 0) { value ->
                                    scope.launch { settingsRepository.setDanmakuOpacity(value) }
                                }
                            }
                        },
                        confirmButton = { Button(onClick = { danmakuSettingsVisible = false }) { Text("完成") } },
                    )
                }
            }
            BackHandler(enabled = privacyPageVisible) { privacyPageVisible = false }
        }
    }
}

/* Hallmark · pre-emit critique: P4 H5 E4 S4 R4 V4 */
private val Ink = Color(0xFF111014)
private val PlayerBlack = Color(0xFF09090B)
private val Panel = Color(0xFF1B191F)
private val PanelRaised = Color(0xFF25222B)
private val PlayerScrim = Color(0xE6111014)
private val BrandPurple = Color(0xFF9A7BFF)
private val BrandPurpleDeep = Color(0xFF6E4ED8)
private val TextPrimary = Color(0xFFF7F3FA)
private val TextMuted = Color(0xFFA9A3B0)
private val LiveRed = Color(0xFFFF6B7A)
private val ErrorRed = Color(0xFFFF8A96)

@androidx.compose.runtime.Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandPurple)
    }
}

@androidx.compose.runtime.Composable
private fun RoomHeader(
    title: String,
    anchor: String,
    hot: String,
    duration: String,
    avatar: String?,
    imageLoader: ImageLoader,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                imageLoader = imageLoader,
                contentDescription = "主播头像",
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(BrandPurpleDeep),
                contentAlignment = Alignment.Center,
            ) { Text(anchor.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(anchor, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("● 直播中", color = LiveRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("$hot$duration", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerControls(
    modifier: Modifier,
    isPlaying: Boolean,
    isMuted: Boolean,
    selectedRate: String,
    selectedCdn: String,
    rates: List<StreamRate>,
    cdns: List<CdnLine>,
    onPlay: () -> Unit,
    onMute: () -> Unit,
    muteEnabled: Boolean,
    onSelectRate: (StreamRate) -> Unit,
    onSelectCdn: (CdnLine) -> Unit,
    danmakuEnabled: Boolean,
    backgroundAudio: Boolean,
    onRefresh: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
    onDanmaku: () -> Unit,
    onDanmakuSettings: () -> Unit,
    onBackgroundAudio: () -> Unit,
) {
    var qualityExpanded by remember { mutableStateOf(false) }
    var cdnExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().background(PlayerScrim).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PlayerAction(if (isPlaying) "Ⅱ" else "▶", if (isPlaying) "暂停" else "播放", onPlay, Modifier.size(36.dp))
            PlayerAction(if (isMuted) "声" else "静", if (isMuted) "恢复" else "静音", onMute, Modifier.size(36.dp), enabled = muteEnabled)
            Box(Modifier.size(36.dp)) {
                PlayerAction("HD", "清晰度", { qualityExpanded = true }, Modifier.size(36.dp))
                DropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                    rates.forEach { rate ->
                        DropdownMenuItem(text = { Text(rate.name) }, onClick = {
                            qualityExpanded = false
                            onSelectRate(rate)
                        })
                    }
                }
            }
            Box(Modifier.size(36.dp)) {
                PlayerAction("≋", "线路", { cdnExpanded = true }, Modifier.size(36.dp))
                DropdownMenu(expanded = cdnExpanded, onDismissRequest = { cdnExpanded = false }) {
                    cdns.forEach { cdn ->
                        DropdownMenuItem(text = { Text(cdn.name ?: cdn.code) }, onClick = {
                            cdnExpanded = false
                            onSelectCdn(cdn)
                        })
                    }
                }
            }
        }
        Text(
            text = if (isPlaying) "正在播放 · $selectedRate · $selectedCdn" else "直播画面待播放",
            color = TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp),
            maxLines = 1,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PlayerAction("↻", "刷新", onRefresh, Modifier.size(36.dp))
            PlayerAction("▣", "画中画", onPip, Modifier.size(36.dp))
            PlayerAction("↗", "全屏", onFullscreen, Modifier.size(36.dp))
            PlayerAction(if (danmakuEnabled) "◎" else "⊘", if (danmakuEnabled) "关闭弹幕" else "打开弹幕", onDanmaku, Modifier.size(36.dp))
            PlayerAction("⚙", "弹幕设置", onDanmakuSettings, Modifier.size(36.dp))
            PlayerAction(if (backgroundAudio) "◉" else "○", if (backgroundAudio) "关闭后台音频" else "开启后台音频", onBackgroundAudio, Modifier.size(36.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerAction(icon: String, label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) PanelRaised else PanelRaised.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, color = if (enabled) BrandPurple else TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@androidx.compose.runtime.Composable
private fun PrivacySetting(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 20.dp, end = 20.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ⓘ", color = BrandPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("隐私与许可", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("权限、第三方许可与应用说明", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", color = TextMuted, fontSize = 22.sp)
        }
    }
}

@androidx.compose.runtime.Composable
private fun PrivacyLicensePage(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelRaised)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回" },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = TextPrimary, fontSize = 28.sp)
            }
            Text("隐私与许可", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            "了解 ${AppConfig.PROJECT_NAME} 如何使用权限、处理直播数据，以及项目使用的第三方组件。",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        PrivacyInfoCard(
            title = "权限说明",
            body = "网络权限：用于读取斗鱼 ${AppConfig.ROOM_ID} 房间信息、获取直播流和接收弹幕。\n\n" +
                "网络状态权限：用于检测网络变化，并在网络恢复后重新获取房间信息和直播流。",
        )
        PrivacyInfoCard(
            title = "隐私说明",
            body = "播放地址和动态签名仅在运行时生成，不作为长期资产保存。应用不收集账号、密码或个人信息，也不提供登录功能。",
        )
        PrivacyInfoCard(
            title = "第三方许可",
            body = "本项目使用的第三方组件及许可信息见项目中的 THIRD_PARTY_NOTICES.md，其中包含 CryptoJS MIT License 等声明。\n\n" +
                "直播内容、主播名称、头像和封面归相应权利人所有。",
        )
        Spacer(Modifier.height(28.dp))
    }
}

@androidx.compose.runtime.Composable
private fun PrivacyInfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = TextMuted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValue: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(valueLabel, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = value,
        onValueChange = onValue,
        valueRange = range,
        steps = steps,
        modifier = Modifier.semantics { contentDescription = "$label，当前$valueLabel" },
        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = BrandPurple, activeTrackColor = BrandPurple),
    )
}

@androidx.compose.runtime.Composable
private fun OfflineBlock(anchor: String, title: String, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(18.dp)) {
            Text("当前未开播", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("$anchor · ${title.ifBlank { "暂无直播标题" }}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)) { Text("刷新状态") }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(18.dp)) {
            Text("房间加载失败", color = ErrorRed, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) { Text("重新尝试") }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StreamErrorCard(message: String) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp, start = 20.dp, end = 20.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF321E26))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("!", color = ErrorRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(start = 10.dp)) {
                Text("无法获取直播流", color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(message, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 2)
            }
        }
    }
}

private fun formatHot(value: Long): String = when {
    value >= 100_000_000 -> "${value / 100_000_000.0}亿热度"
    value >= 10_000 -> "${value / 10_000.0}万热度"
    else -> "$value 热度"
}

private fun formatDuration(showTime: Long?): String {
    if (showTime == null || showTime <= 0L) return ""
    val elapsed = (System.currentTimeMillis() / 1000L - showTime).coerceAtLeast(0L)
    val hours = TimeUnit.SECONDS.toHours(elapsed)
    val minutes = TimeUnit.SECONDS.toMinutes(elapsed) % 60
    return "（${hours}小时${minutes}分）"
}

internal fun shouldReleasePlaybackOnStop(
    isChangingConfigurations: Boolean,
    isInPictureInPictureMode: Boolean,
    enteringPictureInPicture: Boolean,
    allowBackgroundAudio: Boolean,
): Boolean = !isChangingConfigurations &&
    !isInPictureInPictureMode &&
    !enteringPictureInPicture &&
    !allowBackgroundAudio
