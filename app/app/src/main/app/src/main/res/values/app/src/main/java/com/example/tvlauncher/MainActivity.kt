package com.example.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var effects: EffectsView
    private lateinit var grid: LinearLayout
    private lateinit var scroll: ScrollView
    private var focusedTile: View? = null
    private var firstTile: View? = null

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val perRow = 6

    private val moveRunnable = Runnable {
        tilePoint()?.let { effects.moveMain(it.x, it.y) }
    }
    private val idleRunnable = Runnable {
        tilePoint()?.let { effects.startIdle(it.x, it.y, dp(136).toFloat()) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        val root = FrameLayout(this)

        val wp = loadBitmap("wallpaper.jpg") ?: loadBitmap("wallpaper.png")
        if (wp != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(wp)
            root.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
        } else {
            root.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF6FA8DC.toInt(), 0xFF3E7D32.toInt())
            )
        }

        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, 0x66000000)
        )
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, dp(340), Gravity.BOTTOM))

        val clock = TextClock(this)
        clock.format12Hour = "hh:mm"
        clock.format24Hour = "HH:mm"
        clock.textSize = 24f
        clock.setTextColor(Color.WHITE)
        val clockLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        clockLp.setMargins(0, dp(28), dp(48), 0)
        root.addView(clock, clockLp)

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(dp(56), dp(16), dp(56), dp(8))
        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        scroll.addView(grid)
        val scrollLp = FrameLayout.LayoutParams(MATCH, dp(300), Gravity.BOTTOM)
        scrollLp.bottomMargin = dp(12)
        root.addView(scroll, scrollLp)

        effects = EffectsView(this, loadSprites())
        root.addView(effects, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(root)
        buildTiles()
    }

    private fun buildTiles() {
        val pm = packageManager
        val seen = HashSet<String>()
        val items = ArrayList<Triple<String, Drawable, Intent>>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                    ?: pm.getLaunchIntentForPackage(pkg)
                    ?: continue
                items.add(Triple(ri.loadLabel(pm).toString(), ri.loadIcon(pm), launch))
            }
        }
        items.sortBy { it.first.lowercase() }
        for (chunk in items.chunked(perRow)) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for ((label, icon, intent) in chunk) {
                val tile = makeTile(label, icon, intent)
                if (firstTile == null) firstTile = tile
                line.addView(tile)
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.bottomMargin = dp(8)
            grid.addView(line, lp)
        }
        grid.post { firstTile?.requestFocus() }
    }

    private fun glass(focused: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(18).toFloat()
        if (focused) {
            g.setColor(0xE6222222.toInt())
            g.setStroke(dp(3), 0xFFFF9800.toInt())
        } else {
            g.setColor(0xB3262626.toInt())
        }
        return g
    }

    private fun makeTile(label: String, icon: Drawable, intent: Intent): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        tile.isFocusable = true
        tile.isClickable = true

        val box = FrameLayout(this)
        box.background = glass(false)
        val iv = ImageView(this)
        iv.setImageDrawable(icon)
        box.addView(iv, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        tile.addView(box, LinearLayout.LayoutParams(dp(84), dp(84)))

        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        tv.textSize = 12f
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = Gravity.CENTER
        val tvLp = LinearLayout.LayoutParams(dp(104), WRAP)
        tvLp.topMargin = dp(6)
        tile.addView(tv, tvLp)

        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.setMargins(dp(12), 0, dp(12), 0)
        tile.layoutParams = lp

        tile.setOnClickListener {
            try {
                startActivity(intent)
            } catch (e: Exception) {
            }
        }
        tile.setOnFocusChangeListener { v, has ->
            box.background = glass(has)
            val sc = if (has) 1.1f else 1f
            v.animate().scaleX(sc).scaleY(sc).setDuration(150).start()
            if (has) {
                focusedTile = v
                handler.removeCallbacks(moveRunnable)
                handler.postDelayed(moveRunnable, 200)
            }
        }
        return tile
    }

    private fun tilePoint(): PointF? {
        val t = focusedTile ?: return null
        val a = IntArray(2)
        val b = IntArray(2)
        t.getLocationInWindow(a)
        effects.getLocationInWindow(b)
        return PointF(a[0] - b[0] + t.width / 2f, (a[1] - b[1]).toFloat() - dp(2))
    }

    private fun loadBitmap(name: String): Bitmap? = try {
        assets.open(name).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private fun loadSprites(): List<Bitmap> {
        val names: List<String> = (try {
            assets.list("")?.filter { it.startsWith("clone") && it.endsWith(".png") }?.sorted()
        } catch (e: Exception) {
            null
        }) ?: emptyList()
        return names.mapNotNull { loadBitmap(it) }
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, 8000)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        effects.stopIdle()
        scheduleIdle()
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        scheduleIdle()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(idleRunnable)
        effects.stopIdle()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroll.smoothScrollTo(0, 0)
        firstTile?.requestFocus()
    }

    override fun onBackPressed() {}
}
