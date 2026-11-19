package com.lmcattabs.desktopcat

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class OverlayParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val kind: String,
    val color: Int,
    val scale: Float = 1f
)

data class FishItem(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float = 0f)
data class YarnItem(var x: Float, var y: Float, var vx: Float, var vy: Float)

class CatPhysicsEngine(
    var screenW: Float,
    var screenH: Float,
    var statusBarY: Float,
    var floorY: Float,
    val dpScale: Float,
    var profile: CatProfile
) {
    var catX: Float = screenW * 0.5f
    var catY: Float = floorY
    var vx: Float = 0f
    var vy: Float = 0f
    var facing: Int = 1
    var action: String = "IDLE_SIT"
    var perchZone: String = "bottom" // "bottom", "top", "wall_left", "wall_right", "air"
    var climbSide: Int = 0 // -1 left wall, 1 right wall, 0 none

    var animTime: Float = 0f
    var isDraggingCat: Boolean = false
    var isSleepingOnPillow: Boolean = false
    var blinkActive: Boolean = false

    private var targetX: Float? = null
    private var aiTimer: Float = 1.6f
    private var blinkTimer: Float = 0f
    private var nextBlink: Float = 2.8f
    private var zzzTimer: Float = 0.9f
    private var specialTimer: Float = 0f

    // 8-sample smooth touch drag velocity history for gentle parabolic toss
    private val dragX = FloatArray(8)
    private val dragY = FloatArray(8)
    private val dragT = FloatArray(8)
    private var dragCount = 0

    // Interactive toys & particles
    val fishes = mutableListOf<FishItem>()
    var yarn: YarnItem? = null
    val particles = mutableListOf<OverlayParticle>()

    fun recommendedFrameDelayMs(): Long {
        val active = isDraggingCat ||
            action == "RUN" ||
            action == "WALK" ||
            action == "JUMP" ||
            action == "FALL" ||
            action == "CLIMB_WALL" ||
            fishes.isNotEmpty() ||
            yarn != null ||
            particles.isNotEmpty()
        return if (active) 33L else 84L // Smart Adaptive FPS: 30 FPS active, 12 FPS resting!
    }

    fun spawnFish() {
        val sx = (catX + facing * 110f * dpScale).coerceIn(48f * dpScale, screenW - 48f * dpScale)
        val sy = (catY - 120f * dpScale).coerceAtLeast(statusBarY + 36f * dpScale)
        fishes.add(FishItem(sx, sy, facing * 90f * dpScale, -160f * dpScale))
        if (fishes.size > 4) fishes.removeAt(0)
        spawnParticles(sx, sy, "sparkle", Color.rgb(125, 211, 252), 4)
    }

    fun toggleYarn() {
        if (yarn != null) {
            yarn = null
            return
        }
        yarn = YarnItem(
            x = (screenW * 0.5f),
            y = (floorY - 100f * dpScale).coerceAtLeast(statusBarY + 60f * dpScale),
            vx = facing * 130f * dpScale,
            vy = -120f * dpScale
        )
    }

    fun togglePillow(saveCallback: (CatProfile) -> Unit) {
        if (profile.hasPillow) {
            profile.hasPillow = false
            isSleepingOnPillow = false
            if (action == "SLEEP_LOAF") action = "IDLE_SIT"
        } else {
            profile.hasPillow = true
            if (profile.pillowX < 0f || profile.pillowY < 0f) {
                profile.pillowX = (screenW * 0.5f)
                profile.pillowY = floorY
            }
            spawnParticles(profile.pillowX, profile.pillowY - 16f * dpScale, "sparkle", Color.rgb(165, 180, 252), 5)
        }
        saveCallback(profile)
    }

    fun triggerWallClimb() {
        val left = catX < screenW * 0.5f
        climbSide = if (left) -1 else 1
        catX = if (left) 20f * dpScale else screenW - 20f * dpScale
        catY = (floorY - 24f * dpScale)
        perchZone = if (left) "wall_left" else "wall_right"
        action = "CLIMB_WALL"
        isSleepingOnPillow = false
        vx = 0f
        vy = 0f
        spawnParticles(catX, catY - 16f * dpScale, "sparkle", Color.rgb(251, 191, 36), 4)
    }

    fun onCatTouchDown(rawX: Float, rawY: Float) {
        isDraggingCat = true
        isSleepingOnPillow = false
        action = "DANGLE"
        perchZone = "air"
        climbSide = 0
        vx = 0f
        vy = 0f
        dragCount = 1
        dragX[0] = rawX
        dragY[0] = rawY
        dragT[0] = animTime
    }

    fun onCatTouchMove(rawX: Float, rawY: Float) {
        if (!isDraggingCat) return
        val marginX = 24f * dpScale
        catX = rawX.coerceIn(marginX, screenW - marginX)
        catY = (rawY + 28f * dpScale).coerceIn(statusBarY + 26f * dpScale, floorY)

        if (dragCount < dragX.size) {
            dragX[dragCount] = rawX
            dragY[dragCount] = rawY
            dragT[dragCount] = animTime
            dragCount++
        } else {
            for (i in 0 until dragX.size - 1) {
                dragX[i] = dragX[i + 1]
                dragY[i] = dragY[i + 1]
                dragT[i] = dragT[i + 1]
            }
            dragX[dragX.size - 1] = rawX
            dragY[dragY.size - 1] = rawY
            dragT[dragT.size - 1] = animTime
        }
    }

    fun onCatTouchUp() {
        if (!isDraggingCat) return
        isDraggingCat = false

        // Drop onto Sleep Pillow -> curl up right away!
        if (profile.hasPillow && abs(catX - profile.pillowX) < 56f * dpScale && abs(catY - profile.pillowY) < 56f * dpScale) {
            catX = profile.pillowX
            catY = profile.pillowY - 8f * dpScale
            isSleepingOnPillow = true
            action = "SLEEP_LOAF"
            perchZone = "bottom"
            spawnParticles(catX, catY - 24f * dpScale, "zzz", Color.rgb(56, 189, 248), 2)
            return
        }

        // Compute smooth drag release velocity
        var rawVx = 0f
        var rawVy = 0f
        if (dragCount >= 2) {
            val latest = dragCount - 1
            var oldest = 0
            for (i in latest - 1 downTo 0) {
                if (dragT[latest] - dragT[i] >= 0.025f) {
                    oldest = i
                    if (dragT[latest] - dragT[i] >= 0.09f) break
                }
            }
            val dt = dragT[latest] - dragT[oldest]
            if (dt > 0.015f) {
                rawVx = (dragX[latest] - dragX[oldest]) / dt
                rawVy = (dragY[latest] - dragY[oldest]) / dt
            }
        }
        val speed = hypot(rawVx, rawVy)
        val tossThreshold = 130f * dpScale

        if (speed >= tossThreshold) {
            // Gentle parabolic toss!
            val maxVx = 360f * dpScale
            val gentleVx = (rawVx * 0.44f).coerceIn(-maxVx, maxVx)
            var gentleVy = (rawVy * 0.36f).coerceIn(-390f * dpScale, 150f * dpScale)
            if (gentleVy > -120f * dpScale) {
                gentleVy = -(145f * dpScale + min(130f * dpScale, abs(gentleVx) * 0.35f))
            }
            vx = gentleVx
            vy = gentleVy
            if (abs(vx) > 20f) facing = if (vx > 0f) 1 else -1
            action = "JUMP"
            perchZone = "air"
            spawnParticles(catX, catY - 12f * dpScale, "sparkle", Color.rgb(125, 211, 252), 4)
            return
        }

        // Dropped close to left/right screen edge -> cling & climb wall!
        if (catX <= 34f * dpScale || catX >= screenW - 34f * dpScale) {
            climbSide = if (catX <= 34f * dpScale) -1 else 1
            catX = if (climbSide < 0) 20f * dpScale else screenW - 20f * dpScale
            perchZone = if (climbSide < 0) "wall_left" else "wall_right"
            action = "CLIMB_WALL"
            return
        }

        // Dropped close to top status-bar edge -> perch on top edge!
        if (abs(catY - (statusBarY + 26f * dpScale)) <= 42f * dpScale) {
            catY = statusBarY + 26f * dpScale
            perchZone = "top"
            action = "IDLE_SIT"
            vx = 0f
            vy = 0f
            return
        }

        action = "FALL"
        perchZone = "air"
        vy = 20f * dpScale
    }

    fun onCatTapPet() {
        isSleepingOnPillow = false
        action = "HAPPY_PURR"
        specialTimer = 1.6f
        spawnParticles(catX, catY - 30f * dpScale, "heart", Color.rgb(251, 113, 133), 3)
    }

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.004f, 0.06f)
        animTime += dt

        // Blinking
        nextBlink -= dt
        if (nextBlink <= 0f) {
            blinkTimer = 0.14f
            nextBlink = 2.4f + Random.nextFloat() * 3.0f
        }
        if (blinkTimer > 0f) {
            blinkTimer -= dt
            blinkActive = true
        } else {
            blinkActive = false
        }

        updateParticles(dt)
        updateFishes(dt)
        updateYarn(dt)

        if (isDraggingCat) return

        // Sleep Zzz emitter
        if (action == "SLEEP_LOAF") {
            zzzTimer -= dt
            if (zzzTimer <= 0f) {
                zzzTimer = 1.25f
                spawnParticles(catX + facing * 12f * dpScale, catY - 22f * dpScale, "zzz", Color.rgb(56, 189, 248), 1)
            }
        }

        // Wall Climbing -> climbs up phone side wall and vaults onto top status-bar edge!
        if (action == "CLIMB_WALL") {
            val topPerchY = statusBarY + 26f * dpScale
            catY -= 115f * dpScale * dt
            if (catY <= topPerchY) {
                catY = topPerchY
                catX = if (climbSide < 0) 44f * dpScale else screenW - 44f * dpScale
                facing = if (climbSide < 0) 1 else -1
                climbSide = 0
                perchZone = "top"
                action = "IDLE_SIT"
                aiTimer = 1.8f
                spawnParticles(catX, catY, "sparkle", Color.rgb(251, 191, 36), 4)
            }
            return
        }

        // Timed happy/stretch actions
        if (action == "HAPPY_PURR" || action == "BIG_STRETCH") {
            specialTimer -= dt
            if (specialTimer <= 0f) {
                action = "IDLE_SIT"
                aiTimer = 1.6f
            }
            return
        }

        // Sleeping on Pillow
        if (isSleepingOnPillow && profile.hasPillow) {
            catX = profile.pillowX
            catY = profile.pillowY - 8f * dpScale
            action = "SLEEP_LOAF"
            if (fishes.isNotEmpty()) {
                isSleepingOnPillow = false
                action = "RUN"
            }
            return
        }

        // Airborne parabolic flight (Jump / Toss / Fall)
        if (action == "JUMP" || action == "FALL") {
            val prevY = catY
            val gravity = 760f * dpScale
            vy += gravity * dt
            catX += vx * dt
            catY += vy * dt

            // Bounce gently off side walls (or cling if slow!)
            val minX = 24f * dpScale
            val maxX = screenW - 24f * dpScale
            if (catX < minX) {
                catX = minX
                vx = abs(vx) * 0.45f
                facing = 1
            } else if (catX > maxX) {
                catX = maxX
                vx = -abs(vx) * 0.45f
                facing = -1
            }

            // Land on Top Status-Bar Edge if descending across it
            val topPerchY = statusBarY + 26f * dpScale
            if (vy > 0f && prevY <= topPerchY + 8f * dpScale && catY >= topPerchY && catY <= topPerchY + 24f * dpScale) {
                catY = topPerchY
                vx = 0f
                vy = 0f
                perchZone = "top"
                action = "IDLE_SIT"
                aiTimer = 1.8f
                spawnParticles(catX, catY, "dust", Color.rgb(203, 213, 225), 3)
                return
            }

            // Land on Sleep Pillow if descending onto it
            if (vy > 0f && profile.hasPillow && abs(catX - profile.pillowX) <= 48f * dpScale) {
                val py = profile.pillowY - 8f * dpScale
                if (prevY <= py + 14f * dpScale && catY >= py) {
                    catX = profile.pillowX
                    catY = py
                    vx = 0f
                    vy = 0f
                    isSleepingOnPillow = true
                    action = "SLEEP_LOAF"
                    perchZone = "bottom"
                    spawnParticles(catX, catY - 20f * dpScale, "zzz", Color.rgb(56, 189, 248), 2)
                    return
                }
            }

            // Land on bottom floor
            if (catY >= floorY) {
                catY = floorY
                vx = 0f
                vy = 0f
                perchZone = "bottom"
                action = "IDLE_SIT"
                aiTimer = 1.4f
                spawnParticles(catX, catY, "dust", Color.rgb(203, 213, 225), 3)
            }
            return
        }

        // Chase fish treats if any exist!
        if (fishes.isNotEmpty()) {
            val f = fishes.first()
            if (perchZone == "top") {
                vx = (f.x - catX).coerceIn(-200f * dpScale, 200f * dpScale)
                vy = -120f * dpScale
                action = "JUMP"
                perchZone = "air"
                return
            }
            val dx = f.x - catX
            facing = if (dx >= 0f) 1 else -1
            action = "RUN"
            val runSpeed = 240f * dpScale
            catX = (catX + facing * runSpeed * dt).coerceIn(24f * dpScale, screenW - 24f * dpScale)
            if (abs(f.x - catX) < 30f * dpScale && abs(f.y - catY) < 42f * dpScale) {
                fishes.removeAt(0)
                action = "HAPPY_PURR"
                specialTimer = 1.5f
                spawnParticles(catX, catY - 28f * dpScale, "heart", Color.rgb(251, 113, 133), 4)
            }
            return
        }

        // Bat yarn ball playfully!
        yarn?.let { yBall ->
            if (perchZone == "bottom" && abs(yBall.y - floorY) < 60f * dpScale) {
                val dx = yBall.x - catX
                if (abs(dx) < 38f * dpScale) {
                    yBall.vx = (if (dx >= 0f) 1f else -1f) * 260f * dpScale
                    yBall.vy = -170f * dpScale
                    spawnParticles(yBall.x, yBall.y, "sparkle", Color.rgb(251, 191, 36), 3)
                } else if (action != "RUN") {
                    targetX = yBall.x
                    action = "RUN"
                }
            }
        }

        // Walking / Running toward targetX
        val tx = targetX
        if ((action == "WALK" || action == "RUN") && tx != null) {
            val dx = tx - catX
            if (abs(dx) <= 10f * dpScale) {
                targetX = null
                action = "IDLE_SIT"
                aiTimer = 1.6f + Random.nextFloat() * 2.2f
            } else {
                facing = if (dx > 0f) 1 else -1
                val speed = (if (action == "RUN") 215f else 92f) * dpScale
                catX = (catX + facing * speed * dt).coerceIn(24f * dpScale, screenW - 24f * dpScale)
            }
            return
        }

        // Autonomous Mood AI
        aiTimer -= dt
        if (aiTimer > 0f) return

        val roll = Random.nextFloat()
        if (profile.mood == "SLEEPY") {
            if (profile.hasPillow && perchZone == "bottom" && roll < 0.65f) {
                if (abs(catX - profile.pillowX) < 36f * dpScale) {
                    isSleepingOnPillow = true
                    action = "SLEEP_LOAF"
                } else {
                    targetX = profile.pillowX
                    action = "WALK"
                }
                aiTimer = 3.0f
                return
            }
            action = if (roll < 0.7f) "SLEEP_LOAF" else "BIG_STRETCH"
            specialTimer = 2.0f
            aiTimer = 4.5f
            return
        }

        if (perchZone == "top") {
            if (roll < 0.55f) {
                targetX = (42f * dpScale) + Random.nextFloat() * (screenW - 84f * dpScale)
                action = "WALK"
            } else {
                // Gracefully hop back down to bottom edge
                vx = (Random.nextFloat() - 0.5f) * 180f * dpScale
                vy = -90f * dpScale
                facing = if (vx >= 0f) 1 else -1
                action = "JUMP"
                perchZone = "air"
            }
            aiTimer = 2.2f
            return
        }

        when {
            roll < 0.22f -> {
                // Climb left or right phone wall up to top status bar!
                triggerWallClimb()
            }
            roll < 0.52f -> {
                targetX = (36f * dpScale) + Random.nextFloat() * (screenW - 72f * dpScale)
                action = if (profile.mood == "CHAOTIC" || profile.mood == "HUNTER" || Random.nextFloat() < 0.4f) "RUN" else "WALK"
            }
            roll < 0.72f -> {
                action = "BIG_STRETCH"
                specialTimer = 1.9f
            }
            roll < 0.86f && profile.hasPillow -> {
                targetX = profile.pillowX
                action = "WALK"
            }
            else -> {
                action = "IDLE_SIT"
                aiTimer = 2.0f + Random.nextFloat() * 2.0f
            }
        }
    }

    private fun updateFishes(dt: Float) {
        val iter = fishes.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            f.age += dt
            f.vy += 680f * dpScale * dt
            f.x += f.vx * dt
            f.y += f.vy * dt
            if (f.y >= floorY - 6f * dpScale) {
                f.y = floorY - 6f * dpScale
                f.vy = -f.vy * 0.35f
                f.vx *= 0.78f
            }
            if (f.x < 24f * dpScale || f.x > screenW - 24f * dpScale) f.vx = -f.vx
            if (f.age > 14f) iter.remove()
        }
    }

    private fun updateYarn(dt: Float) {
        val yBall = yarn ?: return
        yBall.vy += 650f * dpScale * dt
        yBall.x += yBall.vx * dt
        yBall.y += yBall.vy * dt
        val r = 13f * dpScale
        if (yBall.y >= floorY - r) {
            yBall.y = floorY - r
            yBall.vy = -yBall.vy * 0.52f
            yBall.vx *= 0.92f
        }
        if (yBall.x < r) {
            yBall.x = r
            yBall.vx = abs(yBall.vx) * 0.75f
        } else if (yBall.x > screenW - r) {
            yBall.x = screenW - r
            yBall.vx = -abs(yBall.vx) * 0.75f
        }
    }

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) iter.remove()
        }
    }

    fun spawnParticles(x: Float, y: Float, kind: String, color: Int, count: Int) {
        for (i in 0 until count) {
            particles.add(
                OverlayParticle(
                    x = x + (Random.nextFloat() - 0.5f) * 20f * dpScale,
                    y = y + (Random.nextFloat() - 0.5f) * 10f * dpScale,
                    vx = (Random.nextFloat() - 0.5f) * 35f * dpScale,
                    vy = -(28f + Random.nextFloat() * 34f) * dpScale,
                    life = 0.9f + Random.nextFloat() * 0.5f,
                    maxLife = 1.4f,
                    kind = kind,
                    color = color,
                    scale = 0.9f + Random.nextFloat() * 0.35f
                )
            )
        }
    }
}
