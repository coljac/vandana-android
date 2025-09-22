package net.coljac.tiratnavandana.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coljac.tiratnavandana.model.Chant
import net.coljac.tiratnavandana.model.Verse
import org.json.JSONObject

class ChantRepository(private val context: Context) {

    suspend fun loadChants(): List<Chant> = withContext(Dispatchers.IO) {
        val json = context.assets.open("Resources/chants.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val chantsArr = root.getJSONArray("chants")
        val list = mutableListOf<Chant>()
        for (i in 0 until chantsArr.length()) {
            val c = chantsArr.getJSONObject(i)
            val verses = mutableListOf<Verse>()
            val vArr = c.getJSONArray("verses")
            for (j in 0 until vArr.length()) {
                val v = vArr.getJSONObject(j)
                verses += Verse(
                    id = v.getInt("id"),
                    title = v.optString("title", "Verse ${j + 1}"),
                    startTime = v.getDouble("start_time"),
                    endTime = v.getDouble("end_time"),
                    text = v.getString("text")
                )
            }

            val audioPath = c.getString("audio_file")
            list += Chant(
                id = c.getString("id"),
                title = c.getString("title"),
                audioFile = preferAac(audioPath),
                verses = verses
            )
        }
        list
    }

    fun assetUriFor(godotPath: String): Uri {
        // Godot path like: res://Resources/Audio/tiratna.ogg
        val relative = godotPath.removePrefix("res://")
        return Uri.parse("asset:///" + relative)
    }

    private fun preferAac(godotPath: String): String {
        // If the JSON references .ogg but an .aac with same basename exists in assets, substitute it.
        if (godotPath.endsWith(".ogg")) {
            val aacCandidate = godotPath.removeSuffix(".ogg") + ".aac"
            val relative = aacCandidate.removePrefix("res://")
            return if (assetExists(relative)) aacCandidate else godotPath
        }
        return godotPath
    }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close(); true
    } catch (e: Exception) { false }

    fun assetDurationMsFor(godotPath: String): Long {
        val relative = godotPath.removePrefix("res://")
        return try {
            context.assets.openFd(relative).use { afd ->
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                mmr.release()
                durStr?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) { 0L }
    }
}
