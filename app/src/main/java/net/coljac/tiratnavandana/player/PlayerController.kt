package net.coljac.tiratnavandana.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.coljac.tiratnavandana.data.ChantRepository
import net.coljac.tiratnavandana.model.Chant

class PlayerController(
    private val context: Context,
    private val repo: ChantRepository
) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var currentChant: Chant? = null
    private var selection: List<Int> = emptyList()

    private val _currentSelectionIndex = MutableStateFlow(-1)
    val currentSelectionIndex: StateFlow<Int> = _currentSelectionIndex

    init {
        // Configure audio attributes (speech/music) and handle audio focus
        val attrs = AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player.setAudioAttributes(attrs, true)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentSelectionIndex.value = player.currentMediaItemIndex
            }
        })
    }

    fun setChant(chant: Chant) {
        currentChant = chant
        rebuildPlaylist()
    }

    fun setSelection(indices: List<Int>) {
        selection = indices.sorted()
        rebuildPlaylist()
    }

    fun setLoop(enabled: Boolean) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun play() { player.playWhenReady = true }
    fun stop() { player.stop(); _currentSelectionIndex.value = -1 }
    fun pause() { player.playWhenReady = false }

    fun isPlaying(): Boolean = player.isPlaying

    fun jumpToVerse(globalVerseIndex: Int) {
        val idx = selection.indexOf(globalVerseIndex)
        if (idx >= 0) {
            player.seekTo(idx, 0)
            play()
        }
    }

    private fun rebuildPlaylist() {
        val chant = currentChant ?: return
        val uri: Uri = repo.assetUriFor(chant.audioFile)
        val durationMs = repo.assetDurationMsFor(chant.audioFile).takeIf { it > 0 } ?: Long.MAX_VALUE
        // Filter selection to valid indices for this chant to avoid OOB when switching chants
        val validSelection = selection.filter { it in chant.verses.indices }
        val items = validSelection.map { verseIdx ->
            val verse = chant.verses[verseIdx]
            val startMs = (verse.startTime * 1000).toLong().coerceAtLeast(0)
            val unclampedEnd = (verse.endTime * 1000).toLong().coerceAtLeast(startMs + 1)
            val endMs = unclampedEnd.coerceAtMost(durationMs - 1)
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Verse ${verse.id + 1} — ${chant.title}")
                        .build()
                )
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        }
        val wasPlaying = player.isPlaying
        player.setMediaItems(items)
        player.prepare()
        _currentSelectionIndex.value = if (items.isNotEmpty()) 0 else -1
        if (wasPlaying && items.isNotEmpty()) player.play()
    }

    fun getPlayer(): ExoPlayer = player
}
