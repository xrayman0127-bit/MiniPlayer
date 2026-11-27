
package com.musab.miniplayer

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

import androidx.activity.ComponentActivity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.musab.miniplayer.data.local.DatabaseProvider
import com.musab.miniplayer.data.local.entity.FavoriteEntity

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
    val albumId: Long,
    val album: String = "Unknown Album",
    val fileName: String = "",
    val extension: String = "",
    val folder: String = "",
    val fileSize: Long = 0L
)

private fun Song.stableKey(): String {
    val identity = listOf(
        title.trim().lowercase(),
        artist.trim().lowercase(),
        album.trim().lowercase(),
        fileName.trim().lowercase(),
        extension.trim().lowercase(),
        fileSize.toString()
    ).joinToString("|")

    val digest = java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))

    return digest.joinToString("") { "%02x".format(it) }
}

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
private fun AlbumArtwork(
    bitmap: Bitmap?,
    onSwipeDown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF563C91))
            .pointerInput(onSwipeDown) {
                var totalDragY = 0f

                detectDragGestures(
                    onDragEnd = {
                        if (totalDragY > 80f) {
                            onSwipeDown()
                        }
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                    }
                )
            },
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

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        val projection = projectionList.toTypedArray()

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

            val albumColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

            val fileNameColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            val fileSizeColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }

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
                        albumId = it.getLong(albumIdColumn),
                        album = it.getString(albumColumn)
                            ?.takeIf { name -> name.isNotBlank() }
                            ?: "Unknown Album",
                        fileName = it.getString(fileNameColumn) ?: "",
                        extension = it.getString(fileNameColumn)
                            ?.substringAfterLast('.', "")
                            ?.lowercase()
                            ?: "",
                        folder = if (folderColumn >= 0) {
                            it.getString(folderColumn) ?: ""
                        } else {
                            ""
                        },
                        fileSize = it.getLong(fileSizeColumn)
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
    val database = remember(context) {
        DatabaseProvider.getDatabase(context)
    }
    val favoriteDao = remember(database) {
        database.favoriteDao()
    }
    val favoriteEntries by favoriteDao.getAllFavorites()
        .collectAsState(initial = emptyList())
    val favoriteKeys = remember(favoriteEntries) {
        favoriteEntries.map { it.songKey }.toSet()
    }
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember {
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

    val isFavorite = selectedSong.id != 0L &&
        selectedSong.stableKey() in favoriteKeys

    val waveformCache = remember {
        mutableMapOf<Long, List<Float>>()
    }

    var waveformSamples by remember(selectedSong.id) {
        mutableStateOf(
            waveformCache[selectedSong.id] ?: emptyList()
        )
    }

    LaunchedEffect(selectedSong.id, selectedSong.uri, selectedSong.duration) {
        val cached = waveformCache[selectedSong.id]

        if (cached != null) {
            waveformSamples = cached
        } else {
            waveformSamples = emptyList()

            if (selectedSong.id != 0L && selectedSong.uri != Uri.EMPTY) {
                val result = extractWaveform(
                    context = context,
                    uri = selectedSong.uri,
                    durationMs = selectedSong.duration
                )

                if (result.isNotEmpty()) {
                    waveformCache[selectedSong.id] = result
                    waveformSamples = result
                }
            }
        }
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

    var showQueue by remember { mutableStateOf(false) }

    BackHandler(enabled = showQueue && currentTab == "player") {
        showQueue = false
    }

    var librarySearchQuery by remember { mutableStateOf("") }
    var librarySortOption by remember { mutableStateOf("Title") }
    var librarySortAscending by remember { mutableStateOf(true) }
    var librarySelectedTab by remember { mutableStateOf("Songs") }
    var librarySelectedArtist by remember { mutableStateOf<String?>(null) }
    var librarySelectedAlbumId by remember { mutableStateOf<Long?>(null) }
    var librarySelectedFolder by remember { mutableStateOf<String?>(null) }
    val librarySongsListState = rememberLazyListState()

    BackHandler(enabled = currentTab == "library" || currentTab == "browse") {
        when {
            librarySelectedArtist != null ||
            librarySelectedAlbumId != null ||
            librarySelectedFolder != null -> {
                librarySelectedArtist = null
                librarySelectedAlbumId = null
                librarySelectedFolder = null
            }
            else -> {
                currentTab = "player"
            }
        }
    }



    var songs by remember {
        mutableStateOf<List<Song>>(emptyList())
    }

    val favoriteSongs = remember(songs, favoriteKeys) {
        songs.filter { it.stableKey() in favoriteKeys }
    }

    var isShuffleEnabled by remember {
        mutableStateOf(false)
    }

    var repeatMode by remember {
        mutableIntStateOf(Player.REPEAT_MODE_OFF)
    }

    var currentQueueSongs by remember {
        mutableStateOf<List<Song>>(emptyList())
    }
    val latestQueueSongs by rememberUpdatedState(currentQueueSongs)

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

    // الاتصال بخدمة تشغيل الموسيقى
    var controller by remember {
        mutableStateOf<MediaController?>(null)
    }

    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(
                context,
                ComponentName(context, MusicPlaybackService::class.java)
            )
        ).buildAsync()
    }

    DisposableEffect(controllerFuture) {
        var disposed = false

        controllerFuture.addListener(
            Runnable {
                if (!disposed) {
                    try {
                        controller = controllerFuture.get()
                    } catch (error: Exception) {
                        android.util.Log.e(
                            "MiniPlayer",
                            "Failed to connect to playback service",
                            error
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            disposed = true
            MediaController.releaseFuture(controllerFuture)
        }
    }

    val player = controller

    // مراقبة حالة المشغل المتصل بالخدمة
    DisposableEffect(player) {
        val connectedPlayer = player

        if (connectedPlayer == null) {
            onDispose {}
        } else {
            val listener = object : Player.Listener {

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
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
                    val songId = mediaItem?.mediaId?.toLongOrNull()
                    val song = songId?.let { id ->
                        latestQueueSongs.firstOrNull { it.id == id }
                    }
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

                override fun onRepeatModeChanged(newRepeatMode: Int) {
                    repeatMode = newRepeatMode
                }
            }

            connectedPlayer.addListener(listener)

            isPlaying = connectedPlayer.isPlaying
            isShuffleEnabled = connectedPlayer.shuffleModeEnabled
            repeatMode = connectedPlayer.repeatMode

            onDispose {
                connectedPlayer.removeListener(listener)
            }
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
    LaunchedEffect(isPlaying, selectedSong.id, player) {
        val connectedPlayer = player ?: return@LaunchedEffect

        while (isPlaying) {
            val duration = connectedPlayer.duration
            if (duration > 0) {
                currentPosition = connectedPlayer.currentPosition
                progress = (
                    currentPosition.toFloat() / duration.toFloat()
                ).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // وظائف قائمة التشغيل والتحكم
    fun playSong(song: Song, queueSongs: List<Song>) {
        val activePlayer = player ?: return
        val songIndex = queueSongs.indexOfFirst { it.id == song.id }
        if (songIndex < 0) return

        currentQueueSongs = queueSongs

        val mediaItems = queueSongs.map { songItem ->
            MediaItem.Builder()
                .setMediaId(songItem.id.toString())
                .setUri(songItem.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(songItem.title)
                        .setArtist(songItem.artist)
                        .build()
                )
                .build()
        }

        activePlayer.setMediaItems(mediaItems, songIndex, 0L)
        activePlayer.shuffleModeEnabled = isShuffleEnabled
        activePlayer.repeatMode = repeatMode
        activePlayer.prepare()
        activePlayer.play()

        selectedSong = song
        progress = 0f
        currentPosition = 0L
    }

    fun previousSong() {
        val activePlayer = player ?: return
        if (activePlayer.mediaItemCount > 0) {
            activePlayer.seekToPreviousMediaItem()
        }
    }

    fun nextSong() {
        val activePlayer = player ?: return
        if (activePlayer.mediaItemCount > 0) {
            activePlayer.seekToNextMediaItem()
        }
    }

    fun toggleShuffle() {
        player?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun cycleRepeat() {
        val activePlayer = player ?: return
        activePlayer.repeatMode = when (activePlayer.repeatMode) {
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
                        "MUZORA",
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(if (showQueue && currentTab == "player") Modifier else Modifier.verticalScroll(rememberScrollState()))
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                if (targetState != "player") {
                                    (fadeIn(tween(220)) +
                                        slideInHorizontally(tween(220)) { it / 12 }) togetherWith
                                        (fadeOut(tween(180)) +
                                            slideOutHorizontally(tween(180)) { -it / 12 })
                                } else {
                                    (fadeIn(tween(220)) +
                                        slideInHorizontally(tween(220)) { -it / 12 }) togetherWith
                                        (fadeOut(tween(180)) +
                                            slideOutHorizontally(tween(180)) { it / 12 })
                                }
                            },
                            label = "PlayerLibraryTransition"
                        ) { screen ->
                            if (screen == "player") {
                                if (showQueue) {
                                    QueueScreen(
                                        songs = currentQueueSongs,
                                        selectedSongId = selectedSong.id,
                                        onClose = { showQueue = false },
                                        onSongSelected = { song ->
                                            val index = currentQueueSongs.indexOfFirst {
                                                it.id == song.id
                                            }
                                            if (index >= 0) {
                                                player?.seekTo(index, 0L)
                                                showQueue = false
                                            }
                                        }
                                    )
                                } else {
                                PlayerScreen(
                                    artworkBitmap = artworkBitmap,
                                    artworkColors = artworkColors,
                                    dynamicAccent = dynamicAccent,
                                    song = selectedSong,
                                    isPlaying = isPlaying,
                                    isFavorite = isFavorite,
                                    progress = progress,
                                    waveformSamples = waveformSamples,
                                    currentPosition = currentPosition,
                                    isShuffleEnabled = isShuffleEnabled,
                                    repeatMode = repeatMode,
                                    onPrevious = { previousSong() },
                                    onNext = { nextSong() },
                                    onShuffle = { toggleShuffle() },
                                    onRepeat = { cycleRepeat() },

                                    onFavorite = {
                                        val song = selectedSong
                                        if (song.id != 0L && song.uri != Uri.EMPTY) {
                                            val songKey = song.stableKey()
                                            coroutineScope.launch {
                                                if (songKey in favoriteKeys) {
                                                    favoriteDao.removeFavorite(songKey)
                                                } else {
                                                    favoriteDao.addFavorite(
                                                        FavoriteEntity(
                                                            songKey = songKey,
                                                            mediaStoreId = song.id,
                                                            contentUri = song.uri.toString()
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    },

                                    onPlay = {
                                        val activePlayer = player
                                        if (
                                            selectedSong.uri != Uri.EMPTY &&
                                            activePlayer != null
                                        ) {
                                            if (activePlayer.isPlaying) {
                                                activePlayer.pause()
                                            } else {
                                                if (
                                                    activePlayer.playbackState ==
                                                    Player.STATE_ENDED
                                                ) {
                                                    activePlayer.seekTo(0)
                                                }
                                                activePlayer.play()
                                            }
                                        }
                                    },

                                    onProgress = { value ->
                                        progress = value
                                        val duration = player?.duration ?: 0L

                                        if (duration > 0) {
                                            player?.seekTo(
                                                (duration * value).toLong()
                                            )
                                        }
                                    },
                                    onShowQueue = {
                                        if (currentQueueSongs.isNotEmpty()) {
                                            showQueue = true
                                        }
                                    }
                                )
                                }
                            } else {
                                LibraryScreen(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    browseMode = screen == "browse",
                                    songs = songs,
                                    favoriteSongs = favoriteSongs,
                                    selectedSongId = selectedSong.id,
                                    songsListState = librarySongsListState,
                                    searchQuery = librarySearchQuery,
                                    onSearchQueryChange = { librarySearchQuery = it },
                                    sortOption = librarySortOption,
                                    onSortOptionChange = { librarySortOption = it },
                                    sortAscending = librarySortAscending,
                                    onSortAscendingChange = { librarySortAscending = it },
                                    selectedTab = librarySelectedTab,
                                    onSelectedTabChange = { librarySelectedTab = it },
                                    selectedArtist = librarySelectedArtist,
                                    onSelectedArtistChange = { librarySelectedArtist = it },
                                    selectedAlbumId = librarySelectedAlbumId,
                                    onSelectedAlbumIdChange = { librarySelectedAlbumId = it },
                                    selectedFolder = librarySelectedFolder,
                                    onSelectedFolderChange = { librarySelectedFolder = it },
                                    loading = loading,
                                    hasPermission = hasPermission,

                                    onRequestPermission = {
                                        permissionLauncher.launch(
                                            audioPermission()
                                        )
                                    },

                                    onSongSelected = { song, queueSongs ->
                                        playSong(song, queueSongs)
                                        currentTab = "player"
                                    }
                                )
                            }
                    }

                }

                if (
                    currentTab == "library" &&
                    selectedSong.id != 0L &&
                    selectedSong.uri != Uri.EMPTY
                ) {
                    MiniPlayer(
                        song = selectedSong,
                        artworkBitmap = artworkBitmap,
                        isPlaying = isPlaying,
                        onOpen = { currentTab = "player" },
                        onPlayPause = {
                            val activePlayer = player
                            if (activePlayer != null) {
                                if (activePlayer.isPlaying) {
                                    activePlayer.pause()
                                } else {
                                    if (activePlayer.playbackState == Player.STATE_ENDED) {
                                        activePlayer.seekTo(0)
                                    }
                                    activePlayer.play()
                                }
                            }
                        }
                    )
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
                            showQueue = false
                            librarySelectedTab = "Songs"
                            librarySelectedArtist = null
                            librarySelectedAlbumId = null
                            librarySelectedFolder = null
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
                        tint = if (currentTab == "browse") dynamicAccent else TextSecondary,
                        modifier = Modifier.clickable {
                            if (librarySelectedTab == "Songs") {
                                librarySelectedTab = "Artists"
                            }
                            librarySelectedArtist = null
                            librarySelectedAlbumId = null
                            librarySelectedFolder = null
                            currentTab = "browse"
                        },
                        contentDescription = "Browse"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
            }
        }
    }
}


}

private suspend fun extractWaveform(
    context: android.content.Context,
    uri: Uri,
    durationMs: Long,
    barCount: Int = 64
): List<Float> = withContext(Dispatchers.IO) {
    val sums = FloatArray(barCount)
    val counts = IntArray(barCount)

    val extractor = android.media.MediaExtractor()
    var decoder: android.media.MediaCodec? = null
    var decoderStarted = false

    try {
        val descriptor =
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext emptyList()

        descriptor.use { fd ->
            extractor.setDataSource(fd.fileDescriptor)

            var audioTrack = -1
            var inputFormat: android.media.MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(
                    android.media.MediaFormat.KEY_MIME
                )

                if (mime?.startsWith("audio/") == true) {
                    audioTrack = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrack < 0 || inputFormat == null) {
                return@withContext emptyList()
            }

            extractor.selectTrack(audioTrack)

            val mime = inputFormat.getString(
                android.media.MediaFormat.KEY_MIME
            ) ?: return@withContext emptyList()

            val codec = android.media.MediaCodec.createDecoderByType(mime)
            decoder = codec

            codec.configure(inputFormat, null, null, 0)
            codec.start()
            decoderStarted = true

            var sampleRate = try {
                inputFormat.getInteger(
                    android.media.MediaFormat.KEY_SAMPLE_RATE
                )
            } catch (_: Exception) {
                44100
            }

            var channels = try {
                inputFormat.getInteger(
                    android.media.MediaFormat.KEY_CHANNEL_COUNT
                )
            } catch (_: Exception) {
                2
            }.coerceAtLeast(1)

            var pcmEncoding = 2
            val totalFrames =
                (durationMs.coerceAtLeast(1L) * sampleRate / 1000L)
                    .coerceAtLeast(1L)

            val info = android.media.MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0

            while (!outputDone) {
                currentCoroutineContext().ensureActive()

                var madeProgress = false

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)

                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)

                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val size = extractor.readSampleData(inputBuffer, 0)

                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    size,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                            madeProgress = true
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10000)

                when {
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)

                        if (output != null && info.size > 0) {
                            val format = codec.outputFormat

                            sampleRate = try {
                                format.getInteger(
                                    android.media.MediaFormat.KEY_SAMPLE_RATE
                                )
                            } catch (_: Exception) {
                                sampleRate
                            }

                            channels = try {
                                format.getInteger(
                                    android.media.MediaFormat.KEY_CHANNEL_COUNT
                                )
                            } catch (_: Exception) {
                                channels
                            }.coerceAtLeast(1)

                            pcmEncoding = try {
                                format.getInteger(
                                    android.media.MediaFormat.KEY_PCM_ENCODING
                                )
                            } catch (_: Exception) {
                                pcmEncoding
                            }

                            val bytesPerSample = when (pcmEncoding) {
                                3 -> 1
                                4, 22 -> 4
                                21 -> 3
                                else -> 2
                            }

                            val buffer = output.duplicate().order(
                                java.nio.ByteOrder.LITTLE_ENDIAN
                            )
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)

                            val frameCount =
                                buffer.remaining() /
                                (bytesPerSample * channels)

                            val startFrame = (
                                info.presentationTimeUs.coerceAtLeast(0L) *
                                sampleRate / 1_000_000L
                            )

                            for (frame in 0 until frameCount) {
                                var framePower = 0f

                                for (channel in 0 until channels) {
                                    val value = when (pcmEncoding) {
                                        3 -> (
                                            (buffer.get().toInt() and 0xff) -
                                            128
                                        ) / 128f

                                        4 -> buffer.float.coerceIn(-1f, 1f)

                                        21 -> {
                                            val b0 = buffer.get().toInt() and 0xff
                                            val b1 = buffer.get().toInt() and 0xff
                                            val b2 = buffer.get().toInt()
                                            val raw = (b2 shl 16) or
                                                (b1 shl 8) or b0
                                            val signed = if (
                                                (raw and 0x800000) != 0
                                            ) {
                                                raw or -0x1000000
                                            } else {
                                                raw
                                            }
                                            signed / 8388608f
                                        }

                                        22 -> buffer.int / 2147483648f

                                        else -> buffer.short / 32768f
                                    }

                                    framePower += value * value
                                }

                                framePower /= channels

                                val absoluteFrame = startFrame + frame
                                val bucket = (
                                    absoluteFrame.toDouble() *
                                    barCount / totalFrames
                                ).toInt()

                                if (bucket in 0 until barCount) {
                                    sums[bucket] += framePower
                                    counts[bucket]++
                                }
                            }
                        }

                        val eos = (
                            info.flags and
                            android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        ) != 0

                        codec.releaseOutputBuffer(outputIndex, false)
                        if (eos) outputDone = true
                        madeProgress = true
                    }

                    outputIndex ==
                        android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        madeProgress = true
                    }
                }

                if (madeProgress) {
                    idleLoops = 0
                } else {
                    idleLoops++
                    if (idleLoops > 3000) {
                        break
                    }
                }
            }
        }

        val levels = FloatArray(barCount)
        var maximum = 0f

        for (i in 0 until barCount) {
            if (counts[i] > 0) {
                levels[i] = kotlin.math.sqrt(
                    (sums[i] / counts[i]).toDouble()
                ).toFloat()
                maximum = maxOf(maximum, levels[i])
            }
        }

        if (maximum <= 0f) {
            emptyList()
        } else {
            levels.map {
                (0.08f + 0.92f * (it / maximum))
                    .coerceIn(0.08f, 1f)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    } finally {
        if (decoderStarted) {
            runCatching { decoder?.stop() }
        }
        runCatching { decoder?.release() }
        runCatching { extractor.release() }
    }
}

@Composable
private fun WaveformProgress(
    progress: Float,
    samples: List<Float>,
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
        val count = if (samples.isEmpty()) 64 else samples.size
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val centerY = size.height / 2
        val played = progress.coerceIn(0f, 1f)

        for (i in 0 until count) {
            val heightFactor = if (samples.isEmpty()) {
                0.12f
            } else {
                samples[i].coerceIn(0.08f, 1f)
            }

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
    waveformSamples: List<Float>,
    currentPosition: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onProgress: (Float) -> Unit,
    onShowQueue: () -> Unit
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

            AlbumArtwork(artworkBitmap, onShowQueue)

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
                samples = waveformSamples,
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

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    browseMode: Boolean = false,
    songs: List<Song>,
    favoriteSongs: List<Song>,
    selectedSongId: Long,
    songsListState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: String,
    onSortOptionChange: (String) -> Unit,
    sortAscending: Boolean,
    onSortAscendingChange: (Boolean) -> Unit,
    selectedTab: String,
    onSelectedTabChange: (String) -> Unit,
    selectedArtist: String?,
    onSelectedArtistChange: (String?) -> Unit,
    selectedAlbumId: Long?,
    onSelectedAlbumIdChange: (Long?) -> Unit,
    selectedFolder: String?,
    onSelectedFolderChange: (String?) -> Unit,
    loading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val filteredSongs = remember(songs, searchQuery, sortOption, sortAscending) {
        val query = searchQuery.trim().lowercase()

        val matchingSongs = songs.filter { song ->
            query.isEmpty() ||
                song.title.lowercase().contains(query) ||
                song.artist.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
        }

        val comparator = when (sortOption) {
            "Artist" -> compareBy<Song> { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
            "Duration" -> compareBy<Song> { it.duration }
                .thenBy { it.title.lowercase() }
            else -> compareBy<Song> { it.title.lowercase() }
        }

        if (sortAscending) matchingSongs.sortedWith(comparator)
        else matchingSongs.sortedWith(comparator.reversed())
    }

    val artistGroups = remember(filteredSongs) {
        filteredSongs
            .groupBy { it.artist.trim().ifBlank { "Unknown Artist" } }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    val albumGroups = remember(filteredSongs) {
        filteredSongs
            .groupBy { it.albumId }
            .toList()
            .sortedBy { it.second.firstOrNull()?.album?.lowercase() ?: "" }
    }

    val folderGroups = remember(songs) {
        songs
            .filter { it.folder.isNotBlank() }
            .groupBy { it.folder.trim().trimEnd('/') }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    val detailSongs = remember(filteredSongs, songs, selectedArtist, selectedAlbumId, selectedFolder) {
        when {
            selectedArtist != null ->
                filteredSongs.filter { it.artist.trim().ifBlank { "Unknown Artist" } == selectedArtist }
            selectedAlbumId != null ->
                filteredSongs.filter { it.albumId == selectedAlbumId }
            selectedFolder != null ->
                songs.filter {
                    it.folder.trim().trimEnd('/') == selectedFolder
                }.sortedBy { it.title.lowercase() }
            else -> emptyList()
        }
    }

    val detailListState = rememberLazyListState()

    Column(modifier = modifier) {
        Text(
            if (browseMode) "BROWSE LIBRARY" else "MUSIC LIBRARY",
            color = TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${filteredSongs.size} of ${songs.size} songs",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            !hasPermission -> {
                Text(
                    "Allow Muzora to access your music files.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Grant Permission", color = Background)
                }
            }

            loading -> {
                CircularProgressIndicator(color = Accent)
            }

            songs.isEmpty() -> {
                Text(
                    "No music files found on your device.",
                    color = TextSecondary
                )
            }

            else -> {
                if (!browseMode && selectedTab == "Songs") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search songs") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (selectedArtist == null && selectedAlbumId == null && selectedFolder == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (if (browseMode) listOf("Artists", "Albums", "Folders", "Favorites") else emptyList()).forEach { tab ->
                            TextButton(
                                onClick = { onSelectedTabChange(tab) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedTab == tab) Accent else TextSecondary
                                )
                            ) {
                                Text(
                                    tab,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold
                                    else FontWeight.Normal
                                )
                            }
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            onSelectedArtistChange(null)
                            onSelectedAlbumIdChange(null)
                            onSelectedFolderChange(null)
                        }
                    ) {
                        Text("← Back to $selectedTab")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if ((!browseMode && selectedTab == "Songs") || selectedArtist != null || selectedAlbumId != null || selectedFolder != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Text("Sort: $sortOption")
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                listOf("Title", "Artist", "Duration").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            onSortOptionChange(option)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = { onSortAscendingChange(!sortAscending) }
                        ) {
                            Text(if (sortAscending) "Ascending ↑" else "Descending ↓")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    selectedArtist != null || selectedAlbumId != null || selectedFolder != null -> {
                        val detailTitle = selectedArtist
                            ?: songs.firstOrNull { it.albumId == selectedAlbumId }?.album
                            ?: selectedFolder?.substringAfterLast('/')
                            ?: "Unknown Album"

                        Text(
                            detailTitle,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "${detailSongs.size} songs",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        SongList(
                            songs = detailSongs,
                            selectedSongId = selectedSongId,
                            listState = detailListState,
                            onSongSelected = onSongSelected
                        )
                    }

                    selectedTab == "Artists" -> {
                        if (artistGroups.isEmpty()) {
                            Text("No artists found.", color = TextSecondary)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = artistGroups, key = { it.first }) { group ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceDark)
                                            .clickable { onSelectedArtistChange(group.first) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                group.first,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "${group.second.size} songs",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text("›", color = Accent, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }

                    selectedTab == "Albums" -> {
                        if (albumGroups.isEmpty()) {
                            Text("No albums found.", color = TextSecondary)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = albumGroups, key = { it.first }) { group ->
                                    val albumSongs = group.second
                                    val firstSong = albumSongs.first()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceDark)
                                            .clickable { onSelectedAlbumIdChange(group.first) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                firstSong.album,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "${albumSongs.size} songs • ${firstSong.artist}",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text("›", color = Accent, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }

                    selectedTab == "Folders" -> {
                        if (folderGroups.isEmpty()) {
                            Text("No folders found.", color = TextSecondary)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = folderGroups, key = { it.first }) { group ->
                                    val folderPath = group.first
                                    val folderName = folderPath.substringAfterLast('/').ifBlank { folderPath }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceDark)
                                            .clickable { onSelectedFolderChange(folderPath) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                folderName,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "${group.second.size} songs",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text("›", color = Accent, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }

                    selectedTab == "Favorites" -> {
                        if (favoriteSongs.isEmpty()) {
                            Text("No favorite songs yet.", color = TextSecondary)
                        } else {
                            SongList(
                                songs = favoriteSongs,
                                selectedSongId = selectedSongId,
                                listState = songsListState,
                                onSongSelected = onSongSelected
                            )
                        }
                    }

                    else -> {
                        if (filteredSongs.isEmpty()) {
                            Text("No matching songs found.", color = TextSecondary)
                        } else {
                            SongList(
                                songs = filteredSongs,
                                selectedSongId = selectedSongId,
                                listState = songsListState,
                                onSongSelected = onSongSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    selectedSongId: Long,
    listState: LazyListState,
    onSongSelected: (Song, List<Song>) -> Unit
) {
    if (songs.isEmpty()) {
        Text("No matching songs found.", color = TextSecondary)
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = songs, key = { it.id }) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (song.id == selectedSongId) Accent.copy(alpha = 0.18f)
                        else SurfaceDark
                    )
                    .clickable { onSongSelected(song, songs) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF563C91), Color(0xFFB65F83))
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

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${song.artist} • ${song.album}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

@Composable
private fun MiniPlayer(
    song: Song,
    artworkBitmap: Bitmap?,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark.copy(alpha = 0.96f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (artworkBitmap != null) {
            Image(
                bitmap = artworkBitmap.asImageBitmap(),
                contentDescription = "Album artwork",
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("♫", color = Color.White, fontSize = 22.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            if (isPlaying) "Ⅱ" else "▶",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPlayPause)
                .padding(12.dp),
            color = TextPrimary,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun QueueScreen(
    songs: List<Song>,
    selectedSongId: Long,
    onClose: () -> Unit,
    onSongSelected: (Song) -> Unit
) {
    val currentSongIndex = songs.indexOfFirst {
        it.id == selectedSongId
    }.coerceAtLeast(0)
    val queueListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentSongIndex
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101018))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "PLAYING QUEUE",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "${songs.size} songs",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Text(
                "×",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = TextPrimary,
                fontSize = 30.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Queue is empty", color = TextSecondary)
            }
        } else {
            LazyColumn(
                state = queueListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = songs, key = { it.id }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (song.id == selectedSongId) Accent.copy(alpha = 0.2f)
                                else SurfaceDark
                            )
                            .clickable { onSongSelected(song) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = if (song.id == selectedSongId) Accent else TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

private fun formatDuration(duration: Long): String {

    val totalSeconds = duration / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}
