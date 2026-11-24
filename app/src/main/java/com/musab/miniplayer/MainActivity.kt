
package com.musab.miniplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val uri: Uri
)

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
            MediaStore.Audio.Media.DURATION
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
                        )
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
                Uri.EMPTY
            )
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
            color = Background
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                    Text(
                        "⋮",
                        color = TextPrimary,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                if (currentTab == "player") {

                    PlayerScreen(
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

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .padding(vertical = 17.dp),

                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        "☷",
                        color = if (
                            currentTab == "library"
                        ) Accent else TextSecondary,
                        fontSize = 23.sp,
                        modifier = Modifier.clickable {
                            currentTab = "library"
                        }
                    )

                    Text(
                        "♫",
                        color = if (
                            currentTab == "player"
                        ) Accent else TextSecondary,
                        fontSize = 23.sp,
                        modifier = Modifier.clickable {
                            currentTab = "player"
                        }
                    )

                    Text(
                        "☰",
                        color = TextSecondary,
                        fontSize = 23.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PlayerScreen(
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

    Text(
        "NOW PLAYING",
        color = TextSecondary,
        fontSize = 11.sp,
        letterSpacing = 3.sp
    )

    Spacer(modifier = Modifier.height(18.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF563C91),
                        Color(0xFFB65F83),
                        Color(0xFFEFAD78)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(230.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF30204F),
                            Color(0xFF171522)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFE9A1B5),
                                Color(0xFF7953A7)
                            )
                        )
                    )
            )
        }

        Text(
            "M",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold
        )
    }

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
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                song.artist,
                color = TextSecondary,
                fontSize = 15.sp
            )
        }

        Text(
            if (isFavorite) "♥" else "♡",
            color = Accent,
            fontSize = 30.sp,
            modifier = Modifier.clickable {
                onFavorite()
            }
        )
    }

    Spacer(modifier = Modifier.height(25.dp))

    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = onProgress,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = SurfaceDark
        )
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

        Text(
            if (repeatMode == Player.REPEAT_MODE_ONE) "↻¹" else "↻",
            color = if (repeatMode == Player.REPEAT_MODE_OFF) TextSecondary else Accent,
            fontSize = 25.sp,
            modifier = Modifier.clickable { onRepeat() }
        )

        Text(
            "◀",
            color = TextPrimary,
            fontSize = 27.sp,
            modifier = Modifier.clickable { onPrevious() }
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFC6A9FF),
                            Color(0xFF8E72D9)
                        )
                    )
                )
                .clickable {
                    onPlay()
                },
            contentAlignment = Alignment.Center
        ) {

            Text(
                if (isPlaying) "Ⅱ" else "▶",
                color = Color.White,
                fontSize = 30.sp
            )
        }

        Text(
            "▶",
            color = TextPrimary,
            fontSize = 27.sp,
            modifier = Modifier.clickable { onNext() }
        )

        Text(
            "⇄",
            color = if (isShuffleEnabled) Accent else TextSecondary,
            fontSize = 24.sp,
            modifier = Modifier.clickable { onShuffle() }
        )
    }
}

@Composable
fun LibraryScreen(
    songs: List<Song>,
    selectedSongId: Long,
    loading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSongSelected: (Song) -> Unit
) {

    Column {

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

                                Text(
                                    "♫",
                                    color = Color.White,
                                    fontSize = 22.sp
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
