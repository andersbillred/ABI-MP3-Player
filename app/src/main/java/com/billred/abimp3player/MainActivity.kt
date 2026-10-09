package com.billred.abimp3player

import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val mint = Color.rgb(136, 240, 192)
    private val muted = Color.rgb(148, 161, 166)
    private var service: PlaybackService? = null
    private var bound = false
    private lateinit var title: TextView
    private lateinit var state: TextView
    private lateinit var elapsed: TextView
    private lateinit var remaining: TextView
    private lateinit var count: TextView
    private lateinit var play: Button
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var back: Button
    private lateinit var forward: Button
    private lateinit var seek: SeekBar
    private lateinit var list: DragPlaylist
    private lateinit var adapter: EpisodeAdapter
    private var scrubbing = false
    private var signature = ""
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 250) }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as PlaybackService.LocalBinder).service; flushPending(); render()
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(8))
            setBackgroundColor(Color.rgb(16, 19, 22))
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, dp(8) + bars.bottom)
            insets
        }
        setContentView(root)
        root.addView(label(getString(R.string.player_brand), 24, Color.WHITE, true))
        root.addView(label(getString(R.string.tagline), 11, muted).apply { letterSpacing = 0.15f }, params(-1, -2, 4, 18))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(16)); background = shape("#1B2227", 20)
        }
        root.addView(panel)
        state = label(getString(R.string.ready_to_listen), 11, mint, true).apply { letterSpacing = 0.12f }
        panel.addView(state)
        title = label(getString(R.string.add_first_podcast), 21, Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        panel.addView(title, params(-1, -2, 12, 8))
        panel.addView(label("▂ ▅ ▃ ▇ ▄ ▂ ▆ ▃ ▅ ▇ ▃ ▂ ▆ ▄ ▅ ▂", 19, mint).apply {
            typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, params(-1, 36))
        val times = LinearLayout(this)
        elapsed = label("00:00", 24, mint).apply { typeface = Typeface.MONOSPACE }
        remaining = label(getString(R.string.remaining_time, "00:00"), 14, muted).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; typeface = Typeface.MONOSPACE }
        times.addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f)); times.addView(remaining, LinearLayout.LayoutParams(0, -1, 1f))
        panel.addView(times, params(-1, -2, 8))
        seek = SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(mint); thumbTintList = ColorStateList.valueOf(mint)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(58, 69, 75)); contentDescription = getString(R.string.podcast_position)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { scrubbing = true }
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) { showTimes(progress, service?.duration ?: 0); if (!scrubbing) service?.seek(progress) } }
                override fun onStopTrackingTouch(bar: SeekBar?) { service?.seek(progress); scrubbing = false; render() }
            })
        }
        panel.addView(seek, params(-1, 48))
        val transport = LinearLayout(this).apply { gravity = Gravity.CENTER }
        previous = control("|◀", getString(R.string.previous_file)) { service?.next(-1) }
        back = control("−10", getString(R.string.skip_back)) { service?.let { it.seek(it.position - 10000) } }
        play = control("▶", getString(R.string.play)) { service?.toggle(); render() }.apply {
            setTextColor(Color.rgb(16, 19, 22)); background = shape("#88F0C0", 18); textSize = 24f
        }
        forward = control("+10", getString(R.string.skip_forward)) { service?.let { it.seek(it.position + 10000) } }
        next = control("▶|", getString(R.string.next_file)) { service?.next(1) }
        listOf(previous, back, play, forward, next).forEach { b -> transport.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }) }
        panel.addView(transport, params(-1, -2, 6))
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        count = label(getString(R.string.playlist_count, 0), 13, Color.WHITE, true).apply { letterSpacing = 0.08f }
        heading.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(control(getString(R.string.add_files_button), getString(R.string.add_audio_files)) { pickFiles() }.apply { setTextColor(mint); textSize = 13f }, LinearLayout.LayoutParams(dp(112), dp(48)))
        root.addView(heading, params(-1, -2, 18, 2))
        root.addView(label(getString(R.string.reorder_hint), 12, muted), params(-1, -2, 0, 12))
        val frame = FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        list = DragPlaylist(this).apply {
            divider = null; dividerHeight = dp(8)
            setOnItemClickListener { _, _, index, _ -> service?.let { it.select(it.episodes[index], true) }; render() }
            move = { from, to -> service?.move(from, to); this@MainActivity.adapter.notifyDataSetChanged() }
        }
        adapter = EpisodeAdapter(); list.adapter = adapter
        frame.addView(list, FrameLayout.LayoutParams(-1, -1))
        val empty = label(getString(R.string.empty_playlist), 15, muted).apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(24), dp(16), dp(24)); background = shape("#181E22", 16)
        }
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1)); list.emptyView = empty
        root.addView(label(getString(R.string.resume_footer), 10, muted).apply { gravity = Gravity.CENTER; letterSpacing = 0.12f }, params(-1, 32, 8))
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(16), 0) }
            val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val children = (0 until root.childCount).map { root.getChildAt(it) }
            root.removeAllViews(); root.orientation = LinearLayout.HORIZONTAL
            children.take(3).forEach { left.addView(it) }; children.drop(3).forEach { right.addView(it) }
            val scroll = ScrollView(this).apply { addView(left) }
            root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.requestApplyInsets()
    }
    override fun onStart() {
        super.onStart(); bound = bindService(Intent(this, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
        handler.post(refresh)
    }
    override fun onStop() {
        handler.removeCallbacks(refresh)
        if (bound) { unbindService(connection); bound = false }; service = null
        super.onStop()
    }
    private fun render() {
        val s = service ?: return
        title.text = s.current?.title ?: getString(R.string.add_first_podcast)
        state.text = when { s.error != null -> getString(R.string.file_unavailable); s.preparing -> getString(R.string.loading_podcast); s.playing -> getString(R.string.playing_now); s.current != null -> getString(R.string.paused_position_saved); else -> getString(R.string.ready_to_listen) }
        state.contentDescription = s.error ?: state.text
        if (s.error != null) title.text = s.error
        play.text = if (s.playing) "Ⅱ" else "▶"; play.contentDescription = if (s.playing) getString(R.string.pause) else getString(R.string.play)
        play.isEnabled = s.current != null
        val canSeek = s.duration > 0 && !s.preparing
        seek.isEnabled = canSeek; back.isEnabled = canSeek; forward.isEnabled = canSeek
        val index = s.episodes.indexOfFirst { it.uri == s.current?.uri }
        previous.isEnabled = index > 0; next.isEnabled = index >= 0 && index < s.episodes.lastIndex
        listOf(previous, next, back, forward, play).forEach { it.alpha = if (it.isEnabled) 1f else 0.3f }
        if (!scrubbing) { seek.max = s.duration.coerceAtLeast(1); seek.progress = s.position; showTimes(s.position, s.duration) }
        count.text = getString(R.string.playlist_count, s.episodes.size)
        val newSignature = s.episodes.joinToString { it.uri } + s.current?.uri
        if (signature != newSignature) { signature = newSignature; adapter.notifyDataSetChanged() }
    }
    private fun showTimes(position: Int, duration: Int) {
        elapsed.text = formatTime(position); remaining.text = getString(R.string.remaining_time, formatTime((duration - position).coerceAtLeast(0)))
    }
    private fun pickFiles() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, 10)
    }
    private val pendingItems = mutableListOf<Episode>()
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        val items = uris.mapNotNull { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: getString(R.string.audio_file)
                Episode(uri.toString(), name.removeSuffix(".mp3").removeSuffix(".MP3"))
            } catch (_: Exception) { Toast.makeText(this, getString(R.string.add_file_failed), Toast.LENGTH_LONG).show(); null }
        }
        pendingItems.addAll(items); flushPending(); signature = ""; render()
    }
    private fun flushPending() { service?.let { if (pendingItems.isNotEmpty()) { it.add(pendingItems.toList()); pendingItems.clear() } } }
    private inner class EpisodeAdapter : BaseAdapter() {
        override fun getCount() = service?.episodes?.size ?: 0
        override fun getItem(position: Int) = service!!.episodes[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            val episode = getItem(position); val active = episode.uri == service?.current?.uri
            val row = LinearLayout(this@MainActivity).apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76); background = shape(if (active) "#253B34" else "#1B2227", 12)
            }
            val handle = label("≡", 24, muted).apply {
                gravity = Gravity.CENTER; contentDescription = getString(R.string.move_episode, episode.title)
                setOnLongClickListener {
                    PopupMenu(this@MainActivity, this).apply {
                        if (position > 0) menu.add(getString(R.string.move_up)).setOnMenuItemClickListener { service?.move(position, position - 1); signature = ""; render(); true }
                        if (position < getCount() - 1) menu.add(getString(R.string.move_down)).setOnMenuItemClickListener { service?.move(position, position + 1); signature = ""; render(); true }
                        show()
                    }; true
                }
            }
            row.addView(handle, LinearLayout.LayoutParams(dp(48), -1))
            val text = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
            text.addView(label(episode.title, 15, if (active) mint else Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            val saved = PlaylistStore(this@MainActivity).position(episode.uri)
            text.addView(label(if (active) getString(R.string.selected_podcast) else if (saved > 0) getString(R.string.resume_from, formatTime(saved)) else getString(R.string.ready_to_play), 12, muted), params(-1, -2, 5))
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(control("×", getString(R.string.remove_episode, episode.title)) {
                service?.remove(position); signature = ""; render()
            }.apply { setTextColor(muted); textSize = 23f }, LinearLayout.LayoutParams(dp(48), dp(56)))
            return row
        }
    }
    private inner class DragPlaylist(context: Context) : ListView(context) {
        var move: ((Int, Int) -> Unit)? = null
        private var dragging = -1
        private var downY = 0f
        private var moved = false
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.x < dp(48)) {
                dragging = pointToPosition(event.x.toInt(), event.y.toInt()); downY = event.y; moved = false
                if (dragging >= 0) { parent.requestDisallowInterceptTouchEvent(true); return true }
            }
            if (dragging >= 0) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.y - downY) > dp(8)) moved = true
                        if (moved) {
                            val target = pointToPosition(dp(60), event.y.toInt())
                            if (target >= 0 && target != dragging) { move?.invoke(dragging, target); dragging = target }
                            if (event.y < dp(48)) smoothScrollBy(-dp(32), 100)
                            if (event.y > height - dp(48)) smoothScrollBy(dp(32), 100)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                            val row = getChildAt(dragging - firstVisiblePosition) as? ViewGroup
                            row?.getChildAt(0)?.performLongClick()
                        }
                        dragging = -1; parent.requestDisallowInterceptTouchEvent(false); signature = ""; render()
                    }
                }
                return true
            }
            return super.dispatchTouchEvent(event)
        }
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun control(value: String, description: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 16f; isAllCaps = false; contentDescription = description
        setTextColor(Color.WHITE); background = shape("#293238", 12); minimumWidth = 0; minWidth = 0; setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
    }
    private fun shape(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun params(width: Int, height: Int, top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(if (width < 0) width else dp(width), if (height < 0) height else dp(height)).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun formatTime(ms: Int): String { val seconds = ms.coerceAtLeast(0) / 1000; return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60) }
}
