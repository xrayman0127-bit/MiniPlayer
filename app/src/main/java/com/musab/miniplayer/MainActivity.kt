
package com.musab.miniplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import kotlin.math.sin
import kotlin.math.abs
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private val Background = Color(0xFF101016)
private val SurfaceDark = Color(0xFF1D1C27)
private val Accent = Color(0xFFB69CFF)
private val TextPrimary = Color(0xFFF5F2FA)
private val TextSecondary = Color(0xFF9995A7)

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long
)

@Composable
private fun rememberAlbumArtwork(song: Song): Bitmap? {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = song.albumId
    ) {
        value = withContext(Dispatchers.IO) {
            if (song.albumId <= 0L) {
                null
            } else {
                runCatching {
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        song.albumId
                    )

                    context.contentResolver.openInputStream(artworkUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
        }
    }

    return bitmap
}

@Composable
private fun AlbumArtwork(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF563C91)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                "M",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiniPlayerApp()
        }
    }
}

private fun audioPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private suspend fun loadSongs(): List<Song> =
    withContext(Dispatchers.IO) {

        val songs = mutableListOf<Song>()

        val context = AppContextHolder.context
            ?: return@withContext songs

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder =
            "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {

            val idColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

            val titleColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

            val artistColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            val durationColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            val albumIdColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {

                val songId = it.getLong(idColumn)

                songs.add(
                    Song(
                        id = songId,
                        title = it.getString(titleColumn)
                            ?: "Unknown Track",
                        artist = it.getString(artistColumn)
                            ?: "Unknown Artist",
                        duration = it.getLong(durationColumn),
                        uri = ContentUris.withAppendedId(
                            collection,
                            songId
                        ),
                        albumId = it.getLong(albumIdColumn)
                    )
                )
            }
        }

        songs
    }

// يحتفظ بسياق التطبيق للوصول إلى ملفات الموسيقى
private object AppContextHolder {
    var context: android.content.Context? = null
}

@Composable
fun MiniPlayerApp() {

    val context = LocalContext.current

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var isFavorite by remember {
        mutableStateOf(false)
    }

    var progress by remember {
        mutableFloatStateOf(0f)
    }

    var currentPosition by remember {
        mutableLongStateOf(0L)
    }

    var selectedSong by remember {
        mutableStateOf(
            Song(
                0,
                "Your First Track",
                "Unknown Artist",
                238000,
                Uri.EMPTY,
                0L
            )
        )
    }

    val artworkBitmap = rememberAlbumArtwork(selectedSong)

    val artworkColors = remember(artworkBitmap) {
        if (artworkBitmap == null) {
            listOf(Color(0xFF563C91), Color(0xFF8A527F))
        } else {
            listOf(
                Color(artworkBitmap.getPixel(artworkBitmap.width / 4, artworkBitmap.height / 4)),
                Color(artworkBitmap.getPixel(artworkBitmap.width / 2, artworkBitmap.height / 2)),
                Color(artworkBitmap.getPixel(artworkBitmap.width * 3 / 4, artworkBitmap.height * 3 / 4))
            )
        }
    }

    val dynamicAccent = remember(artworkColors) {
        val color = artworkColors.maxByOrNull {
            it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f
        } ?: Accent

        Color(
            red = (color.red + 0.28f).coerceAtMost(1f),
            green = (color.green + 0.28f).coerceAtMost(1f),
            blue = (color.blue + 0.28f).coerceAtMost(1f),
            alpha = 1f
        )
    }

    var currentTab by remember {
        mutableStateOf("player")
    }

    var songs by remember {
        mutableStateOf<List<Song>>(emptyList())
    }

    var isShuffleEnabled by remember {
        mutableStateOf(false)
    }

    var repeatMode by remember {
        mutableIntStateOf(Player.REPEAT_MODE_OFF)
    }

    val latestSongs by rememberUpdatedState(songs)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                audioPermission()
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    AppContextHolder.context = context.applicationContext

    // إنشاء مشغل الصوت
    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    // مراقبة حالة المشغل وتحرير موارده
    DisposableEffect(player) {

        val listener = object : Player.Listener {

            override fun onIsPlayingChanged(
                isPlayingNow: Boolean
            ) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    progress = 0f
                    currentPosition = 0L
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                val song = latestSongs.getOrNull(player.currentMediaItemIndex)
                if (song != null) {
                    selectedSong = song
                    progress = 0f
                    currentPosition = 0L
                }
            }

            override fun onShuffleModeEnabledChanged(
                shuffleModeEnabled: Boolean
            ) {
                isShuffleEnabled = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(
                newRepeatMode: Int
            ) {
                repeatMode = newRepeatMode
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // طلب صلاحية الوصول إلى الموسيقى
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    // تحميل الأغاني
    LaunchedEffect(hasPermission) {

        if (hasPermission) {
            loading = true
            songs = loadSongs()
            loading = false
        }
    }

    // تحديث الوقت وشريط التقدم
    LaunchedEffect(isPlaying, selectedSong.id) {

        while (isPlaying) {

            val duration = player.duration

            if (duration > 0) {

                currentPosition = player.currentPosition

                progress = (
                    currentPosition.toFloat() /
                    duration.toFloat()
                ).coerceIn(0f, 1f)
            }

            delay(500)
        }
    }

    // وظائف قائمة التشغيل والتحكم
    fun playSong(song: Song) {
        val songIndex = songs.indexOfFirst { it.id == song.id }
        if (songIndex < 0) return

        val mediaItems = songs.map { songItem ->
            MediaItem.fromUri(songItem.uri)
        }

        player.setMediaItems(mediaItems, songIndex, 0L)
        player.shuffleModeEnabled = isShuffleEnabled
        player.repeatMode = repeatMode
        player.prepare()
        player.play()

        selectedSong = song
        progress = 0f
        currentPosition = 0L
    }

    fun previousSong() {
        if (player.mediaItemCount > 0) {
            player.seekToPreviousMediaItem()
        }
    }

    fun nextSong() {
        if (player.mediaItemCount > 0) {
            player.seekToNextMediaItem()
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (artworkBitmap != null) {
                    Image(
                        bitmap = artworkBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(
                                36.dp,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                            ),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    artworkColors[0].copy(alpha = 0.48f),
                                    artworkColors[1].copy(alpha = 0.62f),
                                    Background.copy(alpha = 0.88f),
                                    Background
                                )
                            )
                        )
                )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        "MINIPLAYER",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )

                    AppIcon(
                        kind = AppIconKind.MoreVertical,
                        footprint = "⋮",
                        fontSize = 28.sp,
                        iconSize = 28.dp,
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {

                if (currentTab == "player") {

                    PlayerScreen(
                        artworkBitmap = artworkBitmap,
                        artworkColors = artworkColors,
                        dynamicAccent = dynamicAccent,
                        song = selectedSong,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        progress = progress,
                        currentPosition = currentPosition,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        onPrevious = { previousSong() },
                        onNext = { nextSong() },
                        onShuffle = { toggleShuffle() },
                        onRepeat = { cycleRepeat() },

                        onFavorite = {
                            isFavorite = !isFavorite
                        },

                        onPlay = {

                            if (selectedSong.uri != Uri.EMPTY) {

                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    if (
                                        player.playbackState ==
                                        Player.STATE_ENDED
                                    ) {
                                        player.seekTo(0)
                                    }

                                    player.play()
                                }
                            }
                        },

                        onProgress = { value ->

                            progress = value

                            val duration = player.duration

                            if (duration > 0) {
                                player.seekTo(
                                    (duration * value).toLong()
                                )
                            }
                        }
                    )

                } else {

                    LibraryScreen(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        songs = songs,
                        selectedSongId = selectedSong.id,
                        loading = loading,
                        hasPermission = hasPermission,

                        onRequestPermission = {
                            permissionLauncher.launch(
                                audioPermission()
                            )
                        },

                        onSongSelected = { song ->
                            playSong(song)
                            currentTab = "player"
                        }
                    )
                }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .padding(vertical = 17.dp),

                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    AppIcon(
                        kind = AppIconKind.Library,
                        footprint = "☷",
                        fontSize = 23.sp,
                        iconSize = 23.dp,
                        tint = if (
                            currentTab == "library"
                        ) dynamicAccent else TextSecondary,
                        modifier = Modifier.clickable {
                            currentTab = "library"
                        },
                        contentDescription = "Library"
                    )

                    AppIcon(
                        kind = AppIconKind.Music2,
                        footprint = "♫",
                        fontSize = 23.sp,
                        iconSize = 23.dp,
                        tint = if (
                            currentTab == "player"
                        ) dynamicAccent else TextSecondary,
                        modifier = Modifier.clickable {
                            currentTab = "player"
                        },
                        contentDescription = "Player"
                    )

                    AppIcon(
                        kind = AppIconKind.MoreHorizontal,
                        footprint = "☰",
                        fontSize = 23.sp,
                        iconSize = 23.dp,
                        tint = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
            }
        }
    }
}


@Composable
private fun WaveformProgress(
    progress: Float,
    onProgress: (Float) -> Unit,
    activeColor: Color,
    inactiveColor: Color
) {
    val currentOnProgress by rememberUpdatedState(onProgress)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    currentOnProgress(
                        (position.x / size.width).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    currentOnProgress(
                        (change.position.x / size.width).coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        val count = 64
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val centerY = size.height / 2
        val played = progress.coerceIn(0f, 1f)

        for (i in 0 until count) {
            val heightFactor =
                0.22f + abs(sin(i * 0.47)).toFloat() * 0.78f

            val barHeight = size.height * heightFactor
            val x = i * (barWidth + gap)
            val barCenter = (x + barWidth / 2) / size.width

            drawRoundRect(
                color = if (barCenter <= played) activeColor else inactiveColor,
                topLeft = Offset(x, centerY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

@Composable
fun PlayerScreen(
    artworkBitmap: Bitmap?,
    artworkColors: List<Color>,
    dynamicAccent: Color,
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: Float,
    currentPosition: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onProgress: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
            Text(
                "NOW PLAYING",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            AlbumArtwork(artworkBitmap)

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        song.title,
                        color = TextPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        song.artist,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AppIcon(
                    kind = if (isFavorite) AppIconKind.HeartFilled else AppIconKind.Heart,
                    footprint = if (isFavorite) "♥" else "♡",
                    fontSize = 30.sp,
                    iconSize = 30.dp,
                    tint = Accent,
                    modifier = Modifier.clickable {
                        onFavorite()
                    },
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                )
            }

            Spacer(modifier = Modifier.height(25.dp))

            WaveformProgress(
                progress = progress,
                onProgress = onProgress,
                activeColor = dynamicAccent,
                inactiveColor = Color.White.copy(alpha = 0.22f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDuration(currentPosition),
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Text(
                    formatDuration(song.duration),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    kind = if (repeatMode == Player.REPEAT_MODE_ONE) AppIconKind.RepeatOne else AppIconKind.Repeat,
                    footprint = if (repeatMode == Player.REPEAT_MODE_ONE) "↻¹" else "↻",
                    fontSize = 25.sp,
                    iconSize = 25.dp,
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) TextSecondary else Accent,
                    modifier = Modifier.clickable { onRepeat() },
                    contentDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    }
                )

                AppIcon(
                    kind = AppIconKind.SkipBack,
                    footprint = "◀",
                    fontSize = 27.sp,
                    iconSize = 27.dp,
                    tint = TextPrimary,
                    modifier = Modifier.clickable { onPrevious() },
                    contentDescription = "Previous track"
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    artworkColors[0],
                                    artworkColors[1]
                                )
                            )
                        )
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        kind = if (isPlaying) AppIconKind.Pause else AppIconKind.Play,
                        footprint = if (isPlaying) "Ⅱ" else "▶",
                        fontSize = 30.sp,
                        iconSize = 30.dp,
                        tint = Color.White,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                AppIcon(
                    kind = AppIconKind.SkipForward,
                    footprint = "▶",
                    fontSize = 27.sp,
                    iconSize = 27.dp,
                    tint = TextPrimary,
                    modifier = Modifier.clickable { onNext() },
                    contentDescription = "Next track"
                )

                AppIcon(
                    kind = AppIconKind.Shuffle,
                    footprint = "⇄",
                    fontSize = 24.sp,
                    iconSize = 24.dp,
                    tint = if (isShuffleEnabled) Accent else TextSecondary,
                    modifier = Modifier.clickable { onShuffle() },
                    contentDescription = if (isShuffleEnabled) "Shuffle on" else "Shuffle off"
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    selectedSongId: Long,
    loading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSongSelected: (Song) -> Unit
) {

    Column(modifier = modifier) {

        Text(
            "MUSIC LIBRARY",
            color = TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${songs.size} songs",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        when {

            !hasPermission -> {

                Text(
                    "Allow MiniPlayer to access your music files.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent
                    )
                ) {

                    Text(
                        "Grant Permission",
                        color = Background
                    )
                }
            }

            loading -> {

                CircularProgressIndicator(
                    color = Accent
                )
            }

            songs.isEmpty() -> {

                Text(
                    "No music files found on your device.",
                    color = TextSecondary
                )
            }

            else -> {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),

                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    items(
                        items = songs,
                        key = { song -> song.id }
                    ) { song ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (song.id == selectedSongId)
                                        Accent.copy(alpha = 0.18f)
                                    else SurfaceDark
                                )
                                .clickable {
                                    onSongSelected(song)
                                }
                                .padding(16.dp),

                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF563C91),
                                                Color(0xFFB65F83)
                                            )
                                        )
                                    ),

                                contentAlignment = Alignment.Center
                            ) {

                                AppIcon(
                                    kind = AppIconKind.Music,
                                    footprint = "♫",
                                    fontSize = 22.sp,
                                    iconSize = 22.dp,
                                    tint = Color.White
                                )
                            }

                            Spacer(
                                modifier = Modifier.width(14.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {

                                Text(
                                    song.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )

                                Text(
                                    song.artist,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Text(
                                formatDuration(song.duration),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(duration: Long): String {

    val totalSeconds = duration / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}
