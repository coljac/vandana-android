package net.coljac.tiratnavandana.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import net.coljac.tiratnavandana.ui.MainActivity

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        // Minimal notification manager, can be replaced by Media3 NotificationCompat later
        notificationManager = PlayerNotificationManager.Builder(
            this,
            1,
            "playback_channel"
        ).setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun createCurrentContentIntent(player: androidx.media3.common.Player): PendingIntent? {
                val intent = Intent(this@PlaybackService, MainActivity::class.java)
                return PendingIntent.getActivity(
                    this@PlaybackService, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            override fun getCurrentContentText(player: androidx.media3.common.Player) = null
            override fun getCurrentContentTitle(player: androidx.media3.common.Player): CharSequence =
                player.mediaMetadata.title ?: "Tiratnavandana"
            override fun getCurrentLargeIcon(player: androidx.media3.common.Player, callback: PlayerNotificationManager.BitmapCallback) = null
        }).build()

        @Suppress("DEPRECATION")
        notificationManager?.setMediaSessionToken(mediaSession!!.sessionCompatToken)
        notificationManager?.setPlayer(player)
        startForeground(1, buildSilentNotification())
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "playback_channel",
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildSilentNotification(): Notification {
        // Placeholder; PlayerNotificationManager will update the ongoing notification
        return Notification.Builder(this, "playback_channel").build()
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}
