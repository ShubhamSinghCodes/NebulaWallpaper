package com.example.nebulawallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

class PreviewActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var renderer: NebulaRenderer? = null
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        surfaceView = SurfaceView(this)
        root.addView(surfaceView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(16, 16, 16, 16)
        }
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.BOTTOM
        root.addView(controls, params)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnRand = Button(this).apply { text = "Randomize"; setOnClickListener { renderer?.requestResetRandom() } }
        val btnClear = Button(this).apply { text = "Clear"; setOnClickListener { renderer?.requestResetClear() } }
        row1.addView(btnRand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(btnClear, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnSet = Button(this).apply {
            text = "Settings"
            setOnClickListener { startActivity(Intent(this@PreviewActivity, SettingsActivity::class.java)) }
        }
        val btnEdit = Button(this).apply {
            text = "Editor"
            setOnClickListener {
                renderer?.stop()
                startActivity(Intent(this@PreviewActivity, EditorActivity::class.java))
            }
        }
        val btnWall = Button(this).apply {
            text = "Set Wall"
            setTextColor(Color.YELLOW)
            setOnClickListener { setWallpaper() }
        }
        row2.addView(btnSet, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(btnEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(btnWall, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(row2)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                renderer = NebulaRenderer(this@PreviewActivity, h)
                val w = surfaceView.width.takeIf { it > 0 } ?: h.surfaceFrame.width()
                val h_ = surfaceView.height.takeIf { it > 0 } ?: h.surfaceFrame.height()
                renderer?.onSurfaceChanged(w, h_)
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isPowerSaveMode) renderer?.start()
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {
                renderer?.onSurfaceChanged(w, height)
            }
            override fun surfaceDestroyed(h: SurfaceHolder) {
                renderer?.stop()
                renderer = null
            }
        })

        surfaceView.setOnTouchListener { _, ev ->
            if (NebulaPreferences.isTouchEnabled(this)) {
                renderer?.setTouch(ev.x, ev.y)
            }
            true
        }

        getSharedPreferences("nebula_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this)
    }

    private fun setWallpaper() {
        try {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, NebulaWallpaperService::class.java))
            startActivity(i)
        } catch (e: Exception) {
            try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (_:Exception) {}
        }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) { renderer?.refreshSettings() }
    override fun onResume() {
        super.onResume()
        renderer?.refreshSettings()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if(!pm.isPowerSaveMode) renderer?.start()
    }
    override fun onPause() { super.onPause(); renderer?.stop() }
    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("nebula_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        renderer?.stop()
    }
}