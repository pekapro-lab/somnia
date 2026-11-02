package com.pekapro.somnia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano (mediaPlayback): mantiene vivo el motor de audio con la pantalla apagada,
 * gestiona el foco de audio, los auriculares desconectados, el wakelock y la notificación multimedia.
 */
class SomniaPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var resumeOnFocusGain = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false
    private var noisyRegistered = false
    private lateinit var mediaSession: MediaSessionCompat

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeOnFocusGain = false
                SomniaController.pause()
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                SomniaController.audioEngine.duckMultiplier = 1f
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    SomniaController.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                resumeOnFocusGain = false
                SomniaController.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = SomniaController.uiState.value.isPlaying
                SomniaController.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                SomniaController.audioEngine.duckMultiplier = 0.3f
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SomniaController.init(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, "SomniaBinaural").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = SomniaController.play()
                override fun onPause() = SomniaController.pause()
                override fun onStop() = SomniaController.stop()
            })
            isActive = true
        }
        createChannel()
        scope.launch {
            SomniaController.uiState
                .map { Triple(it.isPlaying, if (it.isTimerActive) it.timerRemainingSeconds else -1, it.selectedMode.nameEs) }
                .distinctUntilChanged()
                .collect { (playing, _, _) -> onStateChanged(playing) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> SomniaController.togglePlayback()
            ACTION_STOP -> { SomniaController.stop(); return START_NOT_STICKY }
        }
        val playing = SomniaController.uiState.value.isPlaying
        if (intent?.action == null || playing) {
            // Tras startForegroundService hay que llamar siempre a startForeground.
            goForeground()
            if (!playing) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                isForeground = false
            }
        }
        return START_NOT_STICKY
    }

    private var lastPlaying: Boolean? = null

    private fun onStateChanged(playing: Boolean) {
        if (playing != lastPlaying) {
            lastPlaying = playing
            if (playing) {
                requestFocus(); registerNoisy(); acquireWakeLock(); goForeground()
            } else {
                releaseWakeLock(); unregisterNoisy()
                if (!resumeOnFocusGain) abandonFocus()
                if (isForeground) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForeground = false
                }
            }
        }
        val playbackState = android.support.v4.media.session.PlaybackStateCompat.Builder()
            .setActions(
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (playing) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
            ).build()
        mediaSession.setPlaybackState(playbackState)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: el servicio sigue funcionando igualmente.
        }
    }

    private fun goForeground() {
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
            isForeground = true
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Reproducción", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controles de la sesión binaural y tiempo restante"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, SomniaPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): Notification {
        val s = SomniaController.uiState.value
        val content = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val timeText = if (s.isTimerActive) "Quedan ${formatRemaining(s.timerRemainingSeconds)}" else "Modo continuo"
        val status = if (s.isPlaying) timeText else "En pausa • $timeText"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle(s.selectedMode.nameEs)
            .setContentText(status)
            .setSubText("${String.format(java.util.Locale.US, "%.1f", s.beatHz)} Hz")
            .setContentIntent(content)
            .setDeleteIntent(servicePending(ACTION_STOP, 3))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(s.isPlaying)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setColor(0xFFF4C464.toInt())
            .addAction(
                if (s.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (s.isPlaying) "Pausar" else "Reanudar",
                servicePending(ACTION_TOGGLE, 1)
            )
            .addAction(R.drawable.ic_notif_stop, "Detener", servicePending(ACTION_STOP, 2))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun requestFocus() {
        if (hasFocus) return
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(false)
                .build().also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else @Suppress("DEPRECATION") audioManager.abandonAudioFocus(focusListener)
        hasFocus = false
        SomniaController.audioEngine.duckMultiplier = 1f
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        noisyRegistered = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Somnia:Playback").apply {
            setReferenceCounted(false)
            acquire(9L * 60 * 60 * 1000) // tope de seguridad: 9 h (el temporizador máximo es 8 h)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si se cierra la app desde recientes estando en pausa, cerramos el servicio.
        if (!SomniaController.uiState.value.isPlaying) SomniaController.stop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        if (SomniaController.uiState.value.isPlaying) SomniaController.pause()
        releaseWakeLock(); unregisterNoisy(); abandonFocus()
        mediaSession.release()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "somnia_playback"
        const val NOTIFICATION_ID = 7183
        const val ACTION_TOGGLE = "com.pekapro.somnia.TOGGLE"
        const val ACTION_STOP = "com.pekapro.somnia.STOP"

        fun formatRemaining(totalSeconds: Int): String {
            val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
            return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }
}
