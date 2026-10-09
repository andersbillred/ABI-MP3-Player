package com.billred.abimp3player

import android.app.*
import android.content.*
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.*

class PlaybackService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@PlaybackService }
    private val binder = LocalBinder()
    private lateinit var store: PlaylistStore
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var ready = false
    private var pendingSeek: Int? = null
    private var requestedPlay = false
    private var resumeAfterFocus = false
    private var foreground = false
    val episodes = mutableListOf<Episode>()
    var current: Episode? = null; private set
    private var hasError = false
    val error: String? get() = if (hasError) getString(R.string.file_open_failed) else null
    var preparing = false; private set
    val playing get() = ready && player?.isPlaying == true
    val duration get() = if (ready) player?.duration ?: 0 else 0
    val position get() = pendingSeek ?: if (ready) player?.currentPosition ?: 0 else current?.let { store.position(it.uri) } ?: 0
    private val ticker = object : Runnable {
        override fun run() { if (playing) { savePosition(); updateSession() }; handler.postDelayed(this, 1000) }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pause() }
    }
    override fun onCreate() {
        super.onCreate()
        store = PlaylistStore(this)
        episodes.addAll(store.episodes())
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes()).setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeAfterFocus = false; pause() }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeAfterFocus = playing; pause(true)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) { resumeAfterFocus = false; play() }
                }
            }.build()
        session = MediaSession(this, "ABI MP3")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { play() }
            override fun onPause() { pause() }
            override fun onSeekTo(pos: Long) { seek(pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
            override fun onSkipToNext() { next(1) }
            override fun onSkipToPrevious() { next(-1) }
            override fun onFastForward() { seek(position + 10000) }
            override fun onRewind() { seek(position - 10000) }
            override fun onStop() { pause() }
        })
        session.isActive = true
        updateChannel()
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        val saved = episodes.find { it.uri == store.current() } ?: episodes.firstOrNull()
        saved?.let { select(it, false) }
        handler.post(ticker)
    }
    private fun updateChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("playback", getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateChannel()
        updateSession()
        if (getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1 }) notifyPlayback()
    }
    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { "play" -> play(); "pause" -> pause(); "next" -> next(1); "previous" -> next(-1) }
        return START_NOT_STICKY
    }
    fun add(items: List<Episode>) {
        items.forEach { if (episodes.none { old -> old.uri == it.uri }) episodes.add(it) }
        persist()
        if (current == null) episodes.firstOrNull()?.let { select(it, false) }
    }
    fun move(from: Int, to: Int) {
        if (from in episodes.indices && to in episodes.indices && from != to) { episodes.add(to, episodes.removeAt(from)); persist() }
    }
    fun remove(index: Int) {
        if (index !in episodes.indices) return
        val removed = episodes[index]
        val active = removed.uri == current?.uri
        val continuePlaying = playing
        if (active) { savePosition(); releasePlayer(); current = null }
        episodes.removeAt(index)
        if (active) {
            episodes.getOrNull(index.coerceAtMost(episodes.lastIndex))?.let { select(it, continuePlaying) }
                ?: run { pause(); updateSession() }
        }
        persist()
        runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(removed.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    fun select(episode: Episode, autoplay: Boolean = true) {
        savePosition(); releasePlayer()
        current = episode; hasError = false; preparing = true; requestedPlay = autoplay
        persist()
        if (autoplay) ensureForeground()
        val mp = MediaPlayer(); player = mp
        try {
            mp.setAudioAttributes(attributes())
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, Uri.parse(episode.uri))
            mp.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                ready = true; preparing = false
                val saved = store.position(episode.uri).coerceIn(0, it.duration.coerceAtLeast(0))
                pendingSeek = if (saved > 0) saved else null
                it.setOnSeekCompleteListener { p -> pendingSeek = null; p.setOnSeekCompleteListener(null); if (requestedPlay) play() else updateSession() }
                if (saved > 0) it.seekTo(saved.toLong(), MediaPlayer.SEEK_CLOSEST) else { if (requestedPlay) play() else updateSession() }
            }
            mp.setOnCompletionListener {
                store.savePosition(episode.uri, it.duration)
                val index = episodes.indexOfFirst { e -> e.uri == episode.uri }
                if (index >= 0 && index < episodes.lastIndex) select(episodes[index + 1], true) else pause()
            }
            mp.setOnErrorListener { _, _, _ -> fail(); true }
            mp.prepareAsync()
            updateSession()
        } catch (_: Exception) { fail() }
    }
    private fun fail() {
        hasError = true
        releasePlayer(); requestedPlay = false; stopForeground(STOP_FOREGROUND_REMOVE); foreground = false
        audio.abandonAudioFocusRequest(focus); updateSession()
    }
    fun toggle() { if (playing || requestedPlay) pause() else play() }
    private fun ensureForeground() {
        startService(Intent(this, PlaybackService::class.java))
        startForeground(1, notification()); foreground = true
    }
    fun play() {
        if (current == null) return
        if (!ready) {
            if (!preparing) select(current!!, true) else { requestedPlay = true; ensureForeground() }
            return
        }
        // Foreground status is established before requesting focus (required on Android 15+).
        ensureForeground()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            requestedPlay = false; stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; return
        }
        requestedPlay = true
        player?.start(); updateSession(); notifyPlayback()
    }
    fun pause(forFocusLoss: Boolean = false) {
        if (!forFocusLoss) resumeAfterFocus = false
        requestedPlay = false
        if (playing) player?.pause()
        savePosition(); if (!forFocusLoss) audio.abandonAudioFocusRequest(focus)
        updateSession()
        if (foreground) {
            notifyPlayback()
            if (!forFocusLoss) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false }
        }
    }
    fun seek(target: Int) {
        if (!ready) return
        val clamped = target.coerceIn(0, duration)
        pendingSeek = clamped
        player?.setOnSeekCompleteListener { pendingSeek = null; savePosition(); updateSession() }
        player?.seekTo(clamped.toLong(), MediaPlayer.SEEK_CLOSEST)
        current?.let { store.savePosition(it.uri, clamped) }
        updateSession()
    }
    fun next(direction: Int) {
        val index = episodes.indexOfFirst { it.uri == current?.uri }
        episodes.getOrNull(index + direction)?.let { select(it, playing || requestedPlay) }
    }
    private fun savePosition() { if (ready) current?.let { store.savePosition(it.uri, position) } }
    private fun persist() = store.save(episodes, current?.uri)
    private fun releasePlayer() { pendingSeek = null; ready = false; preparing = false; player?.release(); player = null }
    private fun updateSession() {
        val state = when { error != null -> PlaybackState.STATE_ERROR; preparing -> PlaybackState.STATE_BUFFERING; playing -> PlaybackState.STATE_PLAYING; else -> PlaybackState.STATE_PAUSED }
        session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD).setState(state, position.toLong(), if (playing) 1f else 0f).build())
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, current?.title ?: getString(R.string.app_name)).putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong()).build())
    }
    private fun action(name: String) = PendingIntent.getService(this, name.hashCode(), Intent(this, PlaybackService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "playback").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(current?.title ?: getString(R.string.app_name)).setContentText(if (playing) getString(R.string.notification_playing) else getString(R.string.notification_paused))
            .setContentIntent(open).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, getString(R.string.previous), action("previous")).build())
            .addAction(Notification.Action.Builder(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) getString(R.string.pause) else getString(R.string.play), action(if (playing) "pause" else "play")).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, getString(R.string.next), action("next")).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }
    private fun notifyPlayback() { getSystemService(NotificationManager::class.java).notify(1, notification()) }
    override fun onTaskRemoved(rootIntent: Intent?) { savePosition(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        savePosition(); handler.removeCallbacksAndMessages(null); unregisterReceiver(noisy)
        releasePlayer(); session.release(); audio.abandonAudioFocusRequest(focus)
        getSystemService(NotificationManager::class.java).cancel(1)
        super.onDestroy()
    }
}
