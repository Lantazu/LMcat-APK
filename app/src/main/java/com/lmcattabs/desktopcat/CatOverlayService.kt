package com.lmcattabs.desktopcat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.roundToInt

class CatOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.lmcattabs.START"
        const val ACTION_STOP = "com.lmcattabs.STOP"
        const val ACTION_RELOAD_PROFILE = "com.lmcattabs.RELOAD"
        const val ACTION_SPAWN_FISH = "com.lmcattabs.FISH"
        const val ACTION_TOGGLE_YARN = "com.lmcattabs.YARN"
        const val ACTION_TOGGLE_PILLOW = "com.lmcattabs.PILLOW"
        const val ACTION_CLIMB_WALL = "com.lmcattabs.CLIMB"
        private const val CHANNEL_ID = "lmcat_overlay_channel"
        private const val NOTIF_ID = 1090
        @Volatile
        var isRunning: Boolean = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var engine: CatPhysicsEngine
    private val renderer = CatSpriteRenderer()
    private val handler = Handler(Looper.getMainLooper())

    private var effectView: EffectOverlayView? = null
    private var catTouchView: CatTouchOverlayView? = null
    private var catLayoutParams: WindowManager.LayoutParams? = null
    private var pillowTouchView: PillowTouchOverlayView? = null
    private var pillowLayoutParams: WindowManager.LayoutParams? = null

    private var catBoxSizePx: Int = 260
    private var lastTickMs: Long = 0L

    private val frameLoop = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTickMs).coerceAtLeast(8L)) / 1000f
            lastTickMs = now

            engine.update(dt)
            syncCatWindowPosition()
            catTouchView?.invalidate()
            effectView?.invalidate()

            handler.postDelayed(this, engine.recommendedFrameDelayMs())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = resources.displayMetrics
        val dp = metrics.density
        val sw = metrics.widthPixels.toFloat()
        val sh = metrics.heightPixels.toFloat()
        val statusBarH = getStatusBarHeightPx().toFloat()
        val navBarH = getNavBarHeightPx().toFloat()
        catBoxSizePx = (128f * dp).roundToInt()

        val profile = CatProfileStore.load(this)
        engine = CatPhysicsEngine(
            screenW = sw,
            screenH = sh,
            statusBarY = statusBarH,
            floorY = sh - navBarH - 8f * dp,
            dpScale = dp,
            profile = profile
        )

        startAsForeground()
        attachOverlayWindows()
        lastTickMs = SystemClock.uptimeMillis()
        handler.post(frameLoop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD_PROFILE -> {
                engine.profile = CatProfileStore.load(this)
                syncPillowTouchWindow()
            }
            ACTION_SPAWN_FISH -> engine.spawnFish()
            ACTION_TOGGLE_YARN -> engine.toggleYarn()
            ACTION_TOGGLE_PILLOW -> {
                engine.togglePillow { CatProfileStore.save(this, it) }
                syncPillowTouchWindow()
            }
            ACTION_CLIMB_WALL -> engine.triggerWallClimb()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(frameLoop)
        removeViewSafe(effectView)
        removeViewSafe(catTouchView)
        removeViewSafe(pillowTouchView)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LMcat.Tabs Masaüstü Kedisi",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CatOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LMcat.Tabs Aktif")
            .setContentText("Piksel kediniz ekranınızda geziniyor. Ayarlar için dokunun.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Kapat", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun attachOverlayWindows() {
        // 1. Full-screen 100% click-through layer for toys & particles (NEVER blocks apps!)
        val effectParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        effectView = EffectOverlayView(this)
        windowManager.addView(effectView, effectParams)

        // 2. Compact floating cat hitbox window (only touches ON the cat are intercepted!)
        val cParams = WindowManager.LayoutParams(
            catBoxSizePx,
            catBoxSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        catLayoutParams = cParams
        catTouchView = CatTouchOverlayView(this)
        syncCatWindowPosition(addIfNeeded = true)

        syncPillowTouchWindow()
    }

    private fun syncCatWindowPosition(addIfNeeded: Boolean = false) {
        val params = catLayoutParams ?: return
        val view = catTouchView ?: return
        params.x = (engine.catX - catBoxSizePx * 0.5f).roundToInt()
        params.y = (engine.catY - catBoxSizePx * 0.78f).roundToInt()
        try {
            if (addIfNeeded) {
                windowManager.addView(view, params)
            } else {
                windowManager.updateViewLayout(view, params)
            }
        } catch (_: Exception) {
        }
    }

    private fun syncPillowTouchWindow() {
        if (!engine.profile.hasPillow) {
            removeViewSafe(pillowTouchView)
            pillowTouchView = null
            pillowLayoutParams = null
            return
        }
        if (pillowTouchView == null) {
            val pw = (104f * engine.dpScale).roundToInt()
            val ph = (34f * engine.dpScale).roundToInt()
            val pParams = WindowManager.LayoutParams(
                pw,
                ph,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (engine.profile.pillowX - pw * 0.5f).roundToInt()
                y = (engine.profile.pillowY - ph * 0.85f).roundToInt()
            }
            pillowLayoutParams = pParams
            pillowTouchView = PillowTouchOverlayView(this)
            try {
                windowManager.addView(pillowTouchView, pParams)
            } catch (_: Exception) {
            }
        }
    }

    private fun removeViewSafe(v: View?) {
        if (v == null) return
        try {
            windowManager.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun getStatusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (28f * resources.displayMetrics.density).roundToInt()
    }

    private fun getNavBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24f * resources.displayMetrics.density).roundToInt()
    }

    private inner class EffectOverlayView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (engine.profile.hasPillow) {
                renderer.drawPillow(canvas, engine.profile.pillowX, engine.profile.pillowY, engine.dpScale)
            }
            engine.yarn?.let { y -> renderer.drawYarn(canvas, y.x, y.y, engine.dpScale) }
            for (f in engine.fishes) {
                renderer.drawFish(canvas, f.x, f.y, engine.dpScale)
            }
            for (p in engine.particles) {
                renderer.drawParticle(canvas, p, engine.dpScale)
            }
        }
    }

    private inner class CatTouchOverlayView(context: Context) : View(context) {
        private var downRawX = 0f
        private var downRawY = 0f
        private var moved = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val fy = height * 0.78f
            renderer.drawCat(
                canvas = canvas,
                centerX = cx,
                footY = fy,
                dpScale = engine.dpScale,
                profile = engine.profile,
                action = engine.action,
                facing = engine.facing,
                animTime = engine.animTime,
                blinkActive = engine.blinkActive,
                climbSide = engine.climbSide
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    moved = false
                    engine.onCatTouchDown(event.rawX, event.rawY)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(event.rawX - downRawX, event.rawY - downRawY) > 10f * engine.dpScale) {
                        moved = true
                    }
                    engine.onCatTouchMove(event.rawX, event.rawY)
                    syncCatWindowPosition()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    engine.onCatTouchUp()
                    if (!moved) {
                        engine.onCatTapPet()
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private inner class PillowTouchOverlayView(context: Context) : View(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val params = pillowLayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val nx = event.rawX.coerceIn(52f * engine.dpScale, engine.screenW - 52f * engine.dpScale)
                    val ny = event.rawY.coerceIn(engine.statusBarY + 54f * engine.dpScale, engine.floorY)
                    engine.profile.pillowX = nx
                    engine.profile.pillowY = ny
                    params.x = (nx - width * 0.5f).roundToInt()
                    params.y = (ny - height * 0.85f).roundToInt()
                    try {
                        windowManager.updateViewLayout(this, params)
                    } catch (_: Exception) {
                    }
                    effectView?.invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    CatProfileStore.save(this@CatOverlayService, engine.profile)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
