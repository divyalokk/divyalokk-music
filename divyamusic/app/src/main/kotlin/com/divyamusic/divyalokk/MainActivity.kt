package com.divyamusic.divyalokk

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.divyamusic.divyalokk.R
import kotlinx.coroutines.delay


// ----------------------------------------------------
// SONG DATA
// ----------------------------------------------------

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)


// ----------------------------------------------------
// MAIN ACTIVITY
// ----------------------------------------------------

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null

    private val songs = mutableStateListOf<Song>()

    private var currentIndex by mutableIntStateOf(-1)

    private var isPlaying by mutableStateOf(false)

    private var currentPosition by mutableIntStateOf(0)

    private var duration by mutableIntStateOf(1)


    // ------------------------------------------------
    // PERMISSION
    // ------------------------------------------------

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                loadMusic()
            }
        }


    // ------------------------------------------------
    // ON CREATE
    // ------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            MusicApp(
                songs = songs,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,

                // Current MediaPlayer position
                getCurrentPosition = {
                    mediaPlayer?.runCatching {
                        currentPosition
                    }?.getOrDefault(0) ?: 0
                },

                // Update Compose position
                onPositionChange = { position ->
                    currentPosition = position
                },

                // Song click
                onSongClick = { index ->

                    if (index in songs.indices) {

                        currentIndex = index

                        playSong(
                            songs[index]
                        )
                    }
                },

                // Play / Pause
                onPlayPause = {
                    playPause()
                },

                // Previous
                onPrevious = {
                    previousSong()
                },

                // Next
                onNext = {
                    nextSong()
                },

                // Seek
                onSeek = { position ->

                    mediaPlayer?.let { player ->

                        val safePosition =
                            position.coerceIn(
                                0,
                                player.duration.coerceAtLeast(1)
                            )

                        player.seekTo(
                            safePosition
                        )

                        currentPosition =
                            safePosition
                    }
                }
            )
        }

        checkPermission()
    }


    // ------------------------------------------------
    // CHECK PERMISSION
    // ------------------------------------------------

    private fun checkPermission() {

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                Manifest.permission.READ_MEDIA_AUDIO

            } else {

                Manifest.permission.READ_EXTERNAL_STORAGE
            }


        if (
            checkSelfPermission(permission)
            == PackageManager.PERMISSION_GRANTED
        ) {

            loadMusic()

        } else {

            permissionLauncher.launch(permission)
        }
    }


    // ------------------------------------------------
    // LOAD DEVICE MUSIC
    // ------------------------------------------------

    private fun loadMusic() {

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI


        val projection = arrayOf(

            MediaStore.Audio.Media._ID,

            MediaStore.Audio.Media.TITLE,

            MediaStore.Audio.Media.ARTIST
        )


        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"


        val newSongs =
            mutableListOf<Song>()


        contentResolver.query(

            collection,

            projection,

            selection,

            null,

            "${MediaStore.Audio.Media.TITLE} ASC"

        )?.use { cursor ->


            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )


            val titleColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )


            val artistColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )


            while (cursor.moveToNext()) {

                val id =
                    cursor.getLong(idColumn)


                val title =
                    cursor.getString(titleColumn)


                val artist =
                    cursor.getString(artistColumn)


                val uri =
                    ContentUris.withAppendedId(
                        collection,
                        id
                    )


                newSongs.add(

                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        uri = uri
                    )
                )
            }
        }


        songs.clear()

        songs.addAll(
            newSongs
        )
    }


    // ------------------------------------------------
    // PLAY SONG
    // ------------------------------------------------

    private fun playSong(song: Song) {

        mediaPlayer?.release()

        mediaPlayer = null


        currentPosition = 0

        duration = 1


        try {

            mediaPlayer =
                MediaPlayer.create(
                    this,
                    song.uri
                )


            val player =
                mediaPlayer


            if (player == null) {

                isPlaying = false

                return
            }


            duration =
                player.duration.coerceAtLeast(1)


            player.setOnCompletionListener {

                nextSong()
            }


            player.start()


            isPlaying = true

        } catch (e: Exception) {

            e.printStackTrace()

            isPlaying = false
        }
    }


    // ------------------------------------------------
    // PLAY / PAUSE
    // ------------------------------------------------

    private fun playPause() {

        val player =
            mediaPlayer


        // No player
        if (player == null) {

            if (songs.isNotEmpty()) {

                currentIndex = 0

                playSong(
                    songs[currentIndex]
                )
            }

            return
        }


        try {

            if (player.isPlaying) {

                player.pause()

                isPlaying = false

            } else {

                player.start()

                isPlaying = true
            }

        } catch (e: Exception) {

            e.printStackTrace()

            isPlaying = false
        }
    }


    // ------------------------------------------------
    // PREVIOUS SONG
    // ------------------------------------------------

    private fun previousSong() {

        if (songs.isEmpty()) return


        if (currentIndex > 0) {

            currentIndex--

            playSong(
                songs[currentIndex]
            )

        } else if (currentIndex == 0) {

            // First song: restart from beginning
            mediaPlayer?.seekTo(0)

            currentPosition = 0
        }
    }


    // ------------------------------------------------
    // NEXT SONG
    // ------------------------------------------------

    private fun nextSong() {

        if (songs.isEmpty()) return


        if (currentIndex < songs.lastIndex) {

            currentIndex++

            playSong(
                songs[currentIndex]
            )

        } else {

            // Last song finished
            isPlaying = false

            currentPosition = duration
        }
    }


    // ------------------------------------------------
    // DESTROY
    // ------------------------------------------------

    override fun onDestroy() {

        mediaPlayer?.release()

        mediaPlayer = null

        super.onDestroy()
    }
}


// ====================================================
// MUSIC APP UI
// ====================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(

    songs: List<Song>,

    currentIndex: Int,

    isPlaying: Boolean,

    currentPosition: Int,

    duration: Int,

    getCurrentPosition: () -> Int,

    onPositionChange: (Int) -> Unit,

    onSongClick: (Int) -> Unit,

    onPlayPause: () -> Unit,

    onPrevious: () -> Unit,

    onNext: () -> Unit,

    onSeek: (Int) -> Unit
) {


    // ------------------------------------------------
    // REAL TIME MEDIA PLAYER POSITION
    // ------------------------------------------------

    LaunchedEffect(
        isPlaying,
        currentIndex
    ) {

        while (isPlaying) {

            val position =
                getCurrentPosition()


            onPositionChange(
                position
            )


            delay(300)
        }
    }


    // ------------------------------------------------
    // CURRENT SONG
    // ------------------------------------------------

    val currentSong =

        if (
            currentIndex >= 0 &&
            currentIndex < songs.size
        ) {

            songs[currentIndex]

        } else {

            null
        }


    // ------------------------------------------------
    // SCREEN
    // ------------------------------------------------

    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    Row(

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Image(

                            painter =
                                painterResource(
                                    R.drawable.logo
                                ),

                            contentDescription =
                                null,

                            modifier =
                                Modifier.size(30.dp)
                        )


                        Text(

                            text =
                                "DivyaMusic",

                            modifier =
                                Modifier.padding(
                                    start = 10.dp
                                )
                        )


                        


                        IconButton(

                            onClick = {
                                // Settings
                                
                            }

                        ) {

                            Icon(

                                imageVector =
                                    Icons.Default.Favorite,

                                contentDescription =
                                    "Settings"
                            )
                        }
                    }
                },


                colors =
                    TopAppBarDefaults.topAppBarColors(

                        containerColor =
                            Color(0xA0FF2D55),

                        titleContentColor =
                            Color.White,

                        actionIconContentColor =
                            Color.White
                    )
            )
        }

    ) { paddingValues ->


        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
        ) {


            // ========================================
            // SONG LIST
            // ========================================

            if (songs.isEmpty()) {

                Box(

                    modifier =
                        Modifier.fillMaxSize(),

                    contentAlignment =
                        Alignment.Center

                ) {

                    Text(
                        text =
                            "No music found"
                    )
                }

            } else {


                LazyColumn(

                    modifier =
                        Modifier.weight(1f)

                ) {

                    itemsIndexed(
                        songs
                    ) { index, song ->


                        ListItem(

                            headlineContent = {

                                Text(
                                    text =
                                        song.title
                                )
                            },


                            supportingContent = {

                                Text(
                                    text =
                                        song.artist
                                )
                            },


                            leadingContent = {

                                Icon(

                                    painter =
                                        painterResource(
                                            R.drawable.music
                                        ),

                                    contentDescription =
                                        "Music"
                                )
                            },


                            modifier =
                                Modifier.clickable {

                                    onSongClick(
                                        index
                                    )
                                }
                        )


                        HorizontalDivider()
                    }
                }


                // ====================================
                // PLAYER AREA
                // ====================================

                HorizontalDivider()


                Text(

                    text =
                        currentSong?.title
                            ?: "Select a song",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp
                        )
                )


                // ====================================
                // SEEK SLIDER
                // ====================================

                val safeDuration =
                    duration.coerceAtLeast(1)


                val safePosition =
                    currentPosition.coerceIn(
                        0,
                        safeDuration
                    )


                Slider(

                    value =
                        safePosition.toFloat(),

                    onValueChange = { value ->

                        onSeek(
                            value.toInt()
                        )
                    },

                    valueRange =
                        0f..safeDuration.toFloat(),

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 12.dp
                            ),

                    colors =
                        SliderDefaults.colors(

                            thumbColor =
                                Color(0xFFFF2D55),

                            activeTrackColor =
                                Color(0xFFFF2D55),

                            inactiveTrackColor =
                                Color(0xA0FF2D55)
                        )
                )


                // ====================================
                // TIME
                // ====================================

                Row(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp
                            ),

                    horizontalArrangement =
                        Arrangement.SpaceBetween

                ) {

                    Text(
                        text =
                            formatTime(
                                safePosition
                            )
                    )


                    Text(
                        text =
                            formatTime(
                                safeDuration
                            )
                    )
                }


                // ====================================
                // CONTROLS
                // ====================================

                Row(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 8.dp,
                                bottom = 20.dp
                            ),

                    horizontalArrangement =
                        Arrangement.Center,

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {


                    // PREVIOUS
                    IconButton(

                        onClick =
                            onPrevious

                    ) {

                        Icon(

                            painter =
                                painterResource(
                                    R.drawable.next
                                ),

                            modifier =
                                Modifier.rotate(
                                    180f
                                ),

                            contentDescription =
                                "Previous"
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.width(20.dp)
                    )


                    // PLAY / PAUSE
                    Button(

                        onClick =
                            onPlayPause,

                        colors =
                            ButtonDefaults.buttonColors(

                                containerColor =
                                    Color(0xFFFF2D55)
                            )
                    ) {

                        Icon(

                            imageVector =

                                if (isPlaying) {

                                    Icons.Default.FavoriteBorder

                                } else {

                                    Icons.Default.PlayArrow
                                },

                            contentDescription =

                                if (isPlaying) {

                                    "Pause"

                                } else {

                                    "Play"
                                }
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.width(20.dp)
                    )


                    // NEXT
                    IconButton(

                        onClick =
                            onNext

                    ) {

                        Icon(

                            painter =
                                painterResource(
                                    R.drawable.next
                                ),

                            contentDescription =
                                "Next"
                        )
                    }
                }
            }
        }
    }
}


// ====================================================
// FORMAT TIME
// ====================================================

fun formatTime(
    milliseconds: Int
): String {

    val totalSeconds =
        milliseconds / 1000


    val minutes =
        totalSeconds / 60


    val seconds =
        totalSeconds % 60


    return "%d:%02d".format(
        minutes,
        seconds
    )
}


// ====================================================
// PREVIEW
// ====================================================

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun MusicAppPreview() {

    val previewSongs =
        listOf(

            Song(
                1,
                "My Song",
                "Artist",
                Uri.EMPTY
            ),

            Song(
                2,
                "Another Song",
                "Singer",
                Uri.EMPTY
            ),

            Song(
                3,
                "Favorite Music",
                "Unknown",
                Uri.EMPTY
            )
        )


    MaterialTheme {

        MusicApp(

            songs =
                previewSongs,

            currentIndex =
                0,

            isPlaying =
                false,

            currentPosition =
                45_000,

            duration =
                200_000,

            getCurrentPosition =
                {
                    45_000
                },

            onPositionChange =
                {},

            onSongClick =
                {},

            onPlayPause =
                {},

            onPrevious =
                {},

            onNext =
                {},

            onSeek =
                {}
        )
    }
}