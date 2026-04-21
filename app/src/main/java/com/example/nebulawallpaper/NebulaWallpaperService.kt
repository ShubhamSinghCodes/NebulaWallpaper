package com.example.nebulawallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class NebulaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return NebulaEngine()
    }

    inner class NebulaEngine : WallpaperService.Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private var renderer: NebulaRenderer? = null
        private var holderRef: SurfaceHolder? = null
        private var isVisible = false

        private val batterySaverReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { updateRenderingState() }
        }

        init { setTouchEventsEnabled(true) }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            holderRef = surfaceHolder
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            registerReceiver(batterySaverReceiver, filter)
            getSharedPreferences("nebula_prefs", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            renderer?.refreshSettings()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (renderer == null) {
                renderer = NebulaRenderer(applicationContext, holder)
            }
            renderer?.onSurfaceChanged(holder.surfaceFrame.width(), holder.surfaceFrame.height())
            updateRenderingState()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            renderer?.onSurfaceChanged(width, height)
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.isVisible = visible
            updateRenderingState()
        }

        private fun updateRenderingState() {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isBatterySaving = pm.isPowerSaveMode
            if (isVisible && !isBatterySaving) {
                renderer?.start()
            } else {
                renderer?.stop()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer?.stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            try {
                unregisterReceiver(batterySaverReceiver)
                getSharedPreferences("nebula_prefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
            } catch (e: Exception) {}
            renderer?.stop()
            renderer = null
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent) {
            // ONLY process touch if preference is enabled
            if (NebulaPreferences.isTouchEnabled(applicationContext)) {
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    renderer?.setTouch(event.x, event.y)
                }
            }
            super.onTouchEvent(event)
        }
    }
}