package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletBridge
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class ArcadeGameMode(val title: String, val tag: String) {
    DAG_DASH("DAG Dash", "arcade_tab_dag_dash"),
    KASPA_2048("Kaspa 2048+", "arcade_tab_2048"),
    PRISM_BREAKER("Prism Breaker", "arcade_tab_breaker")
}

private const val ARCADE_PREFS = "kaspa_offline_arcade"
private const val KEY_BEST_RUNNER = "best_dag_dash"
private const val KEY_BEST_2048 = "best_kaspa_2048"
private const val KEY_BEST_BREAKER = "best_prism_breaker"

enum class MoveDirection {
    LEFT, RIGHT, UP, DOWN
}

/**
 * Pure deterministic 2048 puzzle state machine shared between UI and unit tests.
 */
class Kaspa2048Engine(
    private val random: Random = Random.Default
) {
    var grid: IntArray = IntArray(16)
        private set
    var score: Int = 0
        private set
    var hasWon2048: Boolean = false
        private set
    var continueAfterWin: Boolean = false
        private set
    var isGameOver: Boolean = false
        private set

    private var prevGrid: IntArray? = null
    private var prevScore: Int = 0

    val canUndo: Boolean
        get() = prevGrid != null

    val highestTile: Int
        get() = grid.maxOrNull() ?: 0

    init {
        reset()
    }

    fun reset() {
        grid = IntArray(16)
        score = 0
        hasWon2048 = false
        continueAfterWin = false
        isGameOver = false
        prevGrid = null
        prevScore = 0
        spawnTile()
        spawnTile()
    }

    fun loadGridForTest(values: IntArray, initialScore: Int = 0) {
        require(values.size == 16)
        grid = values.copyOf()
        score = initialScore
        prevGrid = null
        prevScore = 0
        hasWon2048 = grid.any { it >= 2048 }
        continueAfterWin = false
        isGameOver = !hasAvailableMoves()
    }

    fun continueEndless() {
        continueAfterWin = true
    }

    fun undo(): Boolean {
        val saved = prevGrid ?: return false
        grid = saved.copyOf()
        score = prevScore
        prevGrid = null
        isGameOver = !hasAvailableMoves()
        return true
    }

    fun move(direction: MoveDirection): Boolean {
        if (isGameOver) return false
        val before = grid.copyOf()
        val beforeScore = score
        var changed = false
        var pointsGained = 0

        for (i in 0 until 4) {
            val line = IntArray(4) { j ->
                when (direction) {
                    MoveDirection.LEFT -> grid[i * 4 + j]
                    MoveDirection.RIGHT -> grid[i * 4 + (3 - j)]
                    MoveDirection.UP -> grid[j * 4 + i]
                    MoveDirection.DOWN -> grid[(3 - j) * 4 + i]
                }
            }
            val (mergedLine, linePoints) = slideAndMergeLine(line)
            pointsGained += linePoints
            for (j in 0 until 4) {
                val idx = when (direction) {
                    MoveDirection.LEFT -> i * 4 + j
                    MoveDirection.RIGHT -> i * 4 + (3 - j)
                    MoveDirection.UP -> j * 4 + i
                    MoveDirection.DOWN -> (3 - j) * 4 + i
                }
                if (grid[idx] != mergedLine[j]) {
                    changed = true
                    grid[idx] = mergedLine[j]
                }
            }
        }

        if (!changed) return false

        prevGrid = before
        prevScore = beforeScore
        score += pointsGained
        spawnTile()

        if (grid.any { it >= 2048 }) {
            hasWon2048 = true
        }
        isGameOver = !hasAvailableMoves()
        return true
    }

    fun hasAvailableMoves(): Boolean {
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val v = grid[r * 4 + c]
                if (v == 0) return true
                if (r < 3 && grid[(r + 1) * 4 + c] == v) return true
                if (c < 3 && grid[r * 4 + c + 1] == v) return true
            }
        }
        return false
    }

    private fun slideAndMergeLine(line: IntArray): Pair<IntArray, Int> {
        val nonZero = ArrayList<Int>(4)
        for (v in line) {
            if (v != 0) nonZero.add(v)
        }
        val out = IntArray(4)
        var outIdx = 0
        var idx = 0
        var points = 0
        while (idx < nonZero.size) {
            if (idx + 1 < nonZero.size && nonZero[idx] == nonZero[idx + 1]) {
                val merged = nonZero[idx] * 2
                out[outIdx++] = merged
                points += merged
                idx += 2
            } else {
                out[outIdx++] = nonZero[idx]
                idx += 1
            }
        }
        return out to points
    }

    private fun spawnTile(): Boolean {
        val emptyIndices = ArrayList<Int>(16)
        for (i in 0 until 16) {
            if (grid[i] == 0) emptyIndices.add(i)
        }
        if (emptyIndices.isEmpty()) return false
        val target = emptyIndices[random.nextInt(emptyIndices.size)]
        grid[target] = if (random.nextFloat() < 0.90f) 2 else 4
        return true
    }
}

/**
 * Pure step helper for Brick Breaker ball-vs-brick collision physics.
 */
object BrickBreakerPhysics {
    data class Brick(
        val id: Int,
        val row: Int,
        val col: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        var hp: Int,
        val maxHp: Int,
        val points: Int,
        val colorArgb: Long
    )

    data class Ball(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float
    )

    fun resolveCircleRectCollision(
        ball: Ball,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Boolean {
        val closestX = ball.x.coerceIn(left, right)
        val closestY = ball.y.coerceIn(top, bottom)
        val dx = ball.x - closestX
        val dy = ball.y - closestY
        if (dx * dx + dy * dy > ball.radius * ball.radius) {
            return false
        }
        val overlapLeft = abs(ball.x - left)
        val overlapRight = abs(right - ball.x)
        val overlapTop = abs(ball.y - top)
        val overlapBottom = abs(bottom - ball.y)
        val minHoriz = min(overlapLeft, overlapRight)
        val minVert = min(overlapTop, overlapBottom)

        if (minHoriz < minVert) {
            ball.vx = -ball.vx
            ball.x = if (overlapLeft < overlapRight) left - ball.radius - 0.5f else right + ball.radius + 0.5f
        } else {
            ball.vy = -ball.vy
            ball.y = if (overlapTop < overlapBottom) top - ball.radius - 0.5f else bottom + ball.radius + 0.5f
        }
        return true
    }
}

@Composable
fun OfflineArcadeScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) {
        context.getSharedPreferences(ARCADE_PREFS, Context.MODE_PRIVATE)
    }

    var activeGame by remember { mutableStateOf(ArcadeGameMode.DAG_DASH) }
    var bestRunner by remember { mutableIntStateOf(prefs.getInt(KEY_BEST_RUNNER, 0)) }
    var best2048 by remember { mutableIntStateOf(prefs.getInt(KEY_BEST_2048, 0)) }
    var bestBreaker by remember { mutableIntStateOf(prefs.getInt(KEY_BEST_BREAKER, 0)) }

    var showRulesDialog by remember {
        mutableStateOf(!prefs.getBoolean("has_seen_arcade_rules_v1", false))
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = {
                prefs.edit().putBoolean("has_seen_arcade_rules_v1", true).apply()
                showRulesDialog = false
            },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "🎮 Kazium Offline Arcade · Game Rules",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Welcome to Kazium Offline Arcade! Here are the rules for each game:\n\n" +
                            "1. BlockDAG Runner (DAG Dash):\n" +
                            "   • Tap or press Jump to clear incoming obstacles and spikes.\n" +
                            "   • Collect Kaspa blocks to boost your score multiplier and distance.\n" +
                            "   • Avoid crashing into obstacles or game over!\n\n" +
                            "2. Kaspa 2048+:\n" +
                            "   • Swipe (or use directional buttons) to slide tiles across the 4x4 grid.\n" +
                            "   • Tiles with the same number merge into one when they collide.\n" +
                            "   • Combine tiles to reach 2048 and beyond!\n\n" +
                            "3. Prism Breaker:\n" +
                            "   • Drag paddle or tap left/right to keep the energy ball in play.\n" +
                            "   • Smash through glowing crystal blocks to rack up points.\n" +
                            "   • Don't let the ball drop below the paddle!",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit().putBoolean("has_seen_arcade_rules_v1", true).apply()
                        showRulesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea, contentColor = ObsidianBg)
                ) {
                    Text("Let's Play!", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBg, Color(0xFF0E1420), ObsidianBg)
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("offline_arcade_root")
    ) {
        // Compact Arcade Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackHome()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("arcade_back_home")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return to Browser Home",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Offline Arcade",
                            tint = KaspaTea,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kaspa Offline Arcade",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Native 60fps Engine • Zero Internet Required",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = KaspaTea.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.4f))
            ) {
                val activeBest = when (activeGame) {
                    ArcadeGameMode.DAG_DASH -> bestRunner
                    ArcadeGameMode.KASPA_2048 -> best2048
                    ArcadeGameMode.PRISM_BREAKER -> bestBreaker
                }
                Text(
                    text = "BEST $activeBest",
                    color = KaspaTea,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Game Switcher Pills (48dp touch height for accessibility)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArcadeGameMode.entries.forEach { mode ->
                val selected = activeGame == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) KaspaTea.copy(alpha = 0.18f) else SurfaceCard,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) KaspaTea else SurfaceCardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeGame = mode
                        }
                        .testTag(mode.tag)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = mode.title,
                            color = if (selected) KaspaTea else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Active Full-Viewport Game Arena
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (activeGame) {
                ArcadeGameMode.DAG_DASH -> {
                    DagDashGameView(
                        bestScore = bestRunner,
                        onUpdateBest = { newBest ->
                            if (newBest > bestRunner) {
                                bestRunner = newBest
                                prefs.edit().putInt(KEY_BEST_RUNNER, newBest).apply()
                            }
                        }
                    )
                }

                ArcadeGameMode.KASPA_2048 -> {
                    Kaspa2048GameView(
                        bestScore = best2048,
                        onUpdateBest = { newBest ->
                            if (newBest > best2048) {
                                best2048 = newBest
                                prefs.edit().putInt(KEY_BEST_2048, newBest).apply()
                            }
                        }
                    )
                }

                ArcadeGameMode.PRISM_BREAKER -> {
                    PrismBreakerGameView(
                        bestScore = bestBreaker,
                        onUpdateBest = { newBest ->
                            if (newBest > bestBreaker) {
                                bestBreaker = newBest
                                prefs.edit().putInt(KEY_BEST_BREAKER, newBest).apply()
                            }
                        }
                    )
                }
            }
        }
    }
}

private enum class RunnerObstacleKind {
    CRYSTAL_SPIKE,
    HOVER_DRONE,
    LASER_GATE
}

private enum class RunnerPickupKind {
    KAS_SHARD,
    SHIELD_ORB,
    MAGNET_ORB,
    OVERDRIVE_ORB
}

private class RunnerObstacle(
    var x: Float,
    var baseY: Float,
    var width: Float,
    var height: Float,
    val kind: RunnerObstacleKind,
    var phase: Float = 0f
)

private class RunnerPickup(
    var x: Float,
    var y: Float,
    val kind: RunnerPickupKind,
    var collected: Boolean = false
)

private class ArcadeParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val radius: Float,
    val color: Color
)

@Composable
private fun DagDashGameView(
    bestScore: Int,
    onUpdateBest: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var isPaused by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var comboMultiplier by remember { mutableIntStateOf(1) }
    var hasShield by remember { mutableStateOf(false) }
    var magnetSec by remember { mutableFloatStateOf(0f) }
    var overdriveSec by remember { mutableFloatStateOf(0f) }
    var restartSignal by remember { mutableIntStateOf(0) }

    // Canvas simulation coordinates
    var arenaWidth by remember { mutableFloatStateOf(1080f) }
    var arenaHeight by remember { mutableFloatStateOf(1080f) }
    var groundY by remember { mutableFloatStateOf(860f) }

    var playerY by remember { mutableFloatStateOf(780f) }
    var playerVy by remember { mutableFloatStateOf(0f) }
    var jumpsUsed by remember { mutableIntStateOf(0) }
    var isDiving by remember { mutableStateOf(false) }
    var invulnTimer by remember { mutableFloatStateOf(0f) }
    var shakeTimer by remember { mutableFloatStateOf(0f) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var distanceAccum by remember { mutableFloatStateOf(0f) }
    var spawnCooldown by remember { mutableFloatStateOf(0.8f) }
    var pickupCooldown by remember { mutableFloatStateOf(1.1f) }
    var streakTimer by remember { mutableFloatStateOf(0f) }

    val obstacles = remember { mutableStateListOf<RunnerObstacle>() }
    val pickups = remember { mutableStateListOf<RunnerPickup>() }
    val particles = remember { mutableStateListOf<ArcadeParticle>() }
    val stars = remember {
        List(44) { idx ->
            Triple(
                (idx * 7919 % 1000) / 1000f,
                (idx * 3571 % 750) / 1000f,
                1.5f + (idx % 3) * 1.1f
            )
        }
    }

    var renderTick by remember { mutableLongStateOf(0L) }

    fun spawnBurst(cx: Float, cy: Float, color: Color, count: Int) {
        repeat(count) { i ->
            val angle = (i.toFloat() / count) * 6.283f + Random.nextFloat() * 0.4f
            val speed = 110f + Random.nextFloat() * 240f
            particles.add(
                ArcadeParticle(
                    x = cx,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 0.45f,
                    maxLife = 0.45f,
                    radius = 4f + Random.nextFloat() * 5f,
                    color = color
                )
            )
        }
    }

    fun triggerJump() {
        if (isGameOver || isPaused) return
        val playerSize = (min(arenaWidth, arenaHeight) * 0.068f).coerceIn(44f, 76f)
        val floorY = groundY - playerSize / 2f
        if (jumpsUsed < 2) {
            isDiving = false
            playerVy = if (jumpsUsed == 0) -arenaHeight * 0.88f else -arenaHeight * 0.78f
            jumpsUsed += 1
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            spawnBurst(
                cx = arenaWidth * 0.18f,
                cy = playerY + playerSize * 0.4f,
                color = if (jumpsUsed == 1) KaspaTea else ElectricCyan,
                count = if (jumpsUsed == 2) 12 else 7
            )
        } else if (playerY < floorY - 20f) {
            // 3rd air tap does a fast slam down
            isDiving = true
            playerVy = max(playerVy, arenaHeight * 1.15f)
        }
    }

    fun triggerDive() {
        if (isGameOver || isPaused) return
        val playerSize = (min(arenaWidth, arenaHeight) * 0.068f).coerceIn(44f, 76f)
        if (playerY < groundY - playerSize / 2f - 6f) {
            isDiving = true
            playerVy = max(playerVy, arenaHeight * 1.35f)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun resetGame() {
        val playerSize = (min(arenaWidth, arenaHeight) * 0.068f).coerceIn(44f, 76f)
        isGameOver = false
        isPaused = false
        score = 0
        level = 1
        comboMultiplier = 1
        hasShield = false
        magnetSec = 0f
        overdriveSec = 0f
        playerY = groundY - playerSize / 2f
        playerVy = 0f
        jumpsUsed = 0
        isDiving = false
        invulnTimer = 0f
        shakeTimer = 0f
        distanceAccum = 0f
        spawnCooldown = 0.65f
        pickupCooldown = 0.9f
        streakTimer = 0f
        obstacles.clear()
        pickups.clear()
        particles.clear()
        restartSignal += 1
    }

    LaunchedEffect(restartSignal, isPaused, isGameOver) {
        if (isPaused || isGameOver) return@LaunchedEffect
        var lastNanos = 0L
        while (!isPaused && !isGameOver) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.045f)
                lastNanos = now

                val playerSize = (min(arenaWidth, arenaHeight) * 0.068f).coerceIn(44f, 76f)
                val playerX = arenaWidth * 0.18f
                val floorY = groundY - playerSize / 2f

                // Level progression
                level = 1 + (score / 250)
                val overdriveMult = if (overdriveSec > 0f) 1.55f else 1f
                val baseSpeed = arenaWidth * (0.44f + (level - 1) * 0.045f).coerceAtMost(0.95f) * overdriveMult

                scrollOffset += baseSpeed * dt
                distanceAccum += dt * 18f * comboMultiplier * (if (overdriveSec > 0f) 2f else 1f)
                if (distanceAccum >= 1f) {
                    val gained = distanceAccum.toInt()
                    distanceAccum -= gained
                    score += gained
                    onUpdateBest(score)
                }

                // Timers
                if (invulnTimer > 0f) invulnTimer = max(0f, invulnTimer - dt)
                if (shakeTimer > 0f) shakeTimer = max(0f, shakeTimer - dt)
                if (magnetSec > 0f) magnetSec = max(0f, magnetSec - dt)
                if (overdriveSec > 0f) overdriveSec = max(0f, overdriveSec - dt)
                if (streakTimer > 0f) {
                    streakTimer = max(0f, streakTimer - dt)
                    if (streakTimer == 0f && comboMultiplier > 1) {
                        comboMultiplier = 1
                    }
                }

                // Physics
                val gravity = arenaHeight * (if (isDiving) 4.4f else 2.25f)
                playerVy += gravity * dt
                playerY += playerVy * dt
                if (playerY >= floorY) {
                    if (isDiving) {
                        spawnBurst(playerX, groundY, KaspaTea.copy(alpha = 0.8f), 8)
                    }
                    playerY = floorY
                    playerVy = 0f
                    jumpsUsed = 0
                    isDiving = false
                }
                val topCeiling = playerSize * 0.8f
                if (playerY < topCeiling) {
                    playerY = topCeiling
                    if (playerVy < 0f) playerVy = 0f
                }

                // Spawning obstacles
                spawnCooldown -= dt
                if (spawnCooldown <= 0f) {
                    val roll = Random.nextFloat()
                    val kind = when {
                        level >= 2 && roll > 0.68f -> RunnerObstacleKind.HOVER_DRONE
                        level >= 3 && roll < 0.25f -> RunnerObstacleKind.LASER_GATE
                        else -> RunnerObstacleKind.CRYSTAL_SPIKE
                    }
                    val obs = when (kind) {
                        RunnerObstacleKind.CRYSTAL_SPIKE -> {
                            val w = playerSize * (0.72f + Random.nextFloat() * 0.35f)
                            val h = playerSize * (0.95f + Random.nextFloat() * 0.55f)
                            RunnerObstacle(
                                x = arenaWidth + w,
                                baseY = groundY - h,
                                width = w,
                                height = h,
                                kind = kind
                            )
                        }
                        RunnerObstacleKind.HOVER_DRONE -> {
                            val w = playerSize * 0.95f
                            val h = playerSize * 0.72f
                            RunnerObstacle(
                                x = arenaWidth + w,
                                baseY = groundY - playerSize * (1.8f + Random.nextFloat() * 1.15f),
                                width = w,
                                height = h,
                                kind = kind,
                                phase = Random.nextFloat() * 6.28f
                            )
                        }
                        RunnerObstacleKind.LASER_GATE -> {
                            val w = playerSize * 0.62f
                            val h = playerSize * 1.55f
                            RunnerObstacle(
                                x = arenaWidth + w,
                                baseY = groundY - playerSize * 1.15f - h,
                                width = w,
                                height = h,
                                kind = kind
                            )
                        }
                    }
                    obstacles.add(obs)
                    spawnCooldown = (1.32f - (level - 1) * 0.08f).coerceAtLeast(0.58f) + Random.nextFloat() * 0.48f
                }

                // Spawning KAS shards & power-ups
                pickupCooldown -= dt
                if (pickupCooldown <= 0f) {
                    val roll = Random.nextFloat()
                    val pKind = when {
                        roll < 0.76f -> RunnerPickupKind.KAS_SHARD
                        roll < 0.85f -> RunnerPickupKind.SHIELD_ORB
                        roll < 0.94f -> RunnerPickupKind.MAGNET_ORB
                        else -> RunnerPickupKind.OVERDRIVE_ORB
                    }
                    val heightTier = playerSize * (0.7f + Random.nextFloat() * 2.4f)
                    pickups.add(
                        RunnerPickup(
                            x = arenaWidth + 60f,
                            y = (groundY - heightTier).coerceAtLeast(playerSize * 1.1f),
                            kind = pKind
                        )
                    )
                    pickupCooldown = 0.65f + Random.nextFloat() * 0.65f
                }

                // Update obstacles & collision
                val hitShrink = playerSize * 0.24f
                val pLeft = playerX - playerSize * 0.5f + hitShrink
                val pRight = playerX + playerSize * 0.5f - hitShrink
                val pTop = playerY - playerSize * 0.5f + hitShrink
                val pBottom = playerY + playerSize * 0.5f - hitShrink

                val obsIter = obstacles.iterator()
                while (obsIter.hasNext()) {
                    val o = obsIter.next()
                    o.x -= baseSpeed * dt
                    if (o.kind == RunnerObstacleKind.HOVER_DRONE) {
                        o.phase += dt * 4.2f
                    }
                    val actualY = if (o.kind == RunnerObstacleKind.HOVER_DRONE) {
                        o.baseY + sin(o.phase) * playerSize * 0.45f
                    } else {
                        o.baseY
                    }
                    val oLeft = o.x + o.width * 0.14f
                    val oRight = o.x + o.width * 0.86f
                    val oTop = actualY + o.height * 0.12f
                    val oBottom = actualY + o.height * 0.92f

                    val overlaps = pRight > oLeft && pLeft < oRight && pBottom > oTop && pTop < oBottom
                    if (overlaps) {
                        if (overdriveSec > 0f) {
                            spawnBurst(o.x + o.width / 2f, actualY + o.height / 2f, AmberCentral, 14)
                            score += 25
                            onUpdateBest(score)
                            obsIter.remove()
                            continue
                        } else if (invulnTimer <= 0f) {
                            if (hasShield) {
                                hasShield = false
                                invulnTimer = 1.25f
                                shakeTimer = 0.28f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                spawnBurst(playerX, playerY, ElectricCyan, 18)
                                obsIter.remove()
                                continue
                            } else {
                                shakeTimer = 0.35f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                spawnBurst(playerX, playerY, RedTamper, 22)
                                onUpdateBest(score)
                                isGameOver = true
                                break
                            }
                        }
                    }

                    if (o.x + o.width < -60f) {
                        obsIter.remove()
                    }
                }

                // Update pickups
                val pickIter = pickups.iterator()
                val magnetRadius = arenaWidth * 0.38f
                val collectDistSq = (playerSize * 0.72f) * (playerSize * 0.72f)
                while (pickIter.hasNext()) {
                    val pk = pickIter.next()
                    if (magnetSec > 0f) {
                        val dx = playerX - pk.x
                        val dy = playerY - pk.y
                        val d2 = dx * dx + dy * dy
                        if (d2 < magnetRadius * magnetRadius && d2 > 1f) {
                            val inv = 1f / kotlin.math.sqrt(d2)
                            pk.x += dx * inv * baseSpeed * 1.45f * dt
                            pk.y += dy * inv * baseSpeed * 1.45f * dt
                        } else {
                            pk.x -= baseSpeed * dt
                        }
                    } else {
                        pk.x -= baseSpeed * dt
                    }

                    val dx = playerX - pk.x
                    val dy = playerY - pk.y
                    if (dx * dx + dy * dy <= collectDistSq) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (pk.kind) {
                            RunnerPickupKind.KAS_SHARD -> {
                                comboMultiplier = (comboMultiplier + 1).coerceAtMost(5)
                                streakTimer = 4.0f
                                score += 15 * comboMultiplier
                                onUpdateBest(score)
                                spawnBurst(pk.x, pk.y, KaspaTea, 10)
                            }
                            RunnerPickupKind.SHIELD_ORB -> {
                                hasShield = true
                                score += 20
                                onUpdateBest(score)
                                spawnBurst(pk.x, pk.y, ElectricCyan, 14)
                            }
                            RunnerPickupKind.MAGNET_ORB -> {
                                magnetSec = 7.0f
                                score += 20
                                onUpdateBest(score)
                                spawnBurst(pk.x, pk.y, VioletBridge, 14)
                            }
                            RunnerPickupKind.OVERDRIVE_ORB -> {
                                overdriveSec = 4.5f
                                score += 35
                                onUpdateBest(score)
                                spawnBurst(pk.x, pk.y, AmberCentral, 16)
                            }
                        }
                        pickIter.remove()
                    } else if (pk.x < -80f) {
                        pickIter.remove()
                    }
                }

                // Update particles
                val pIter = particles.iterator()
                while (pIter.hasNext()) {
                    val pt = pIter.next()
                    pt.x += pt.vx * dt
                    pt.y += pt.vy * dt
                    pt.vy += arenaHeight * 0.45f * dt
                    pt.life -= dt
                    if (pt.life <= 0f) {
                        pIter.remove()
                    }
                }

                renderTick = now
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // HUD Row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudMetric(label = "SCORE", value = "$score", accent = TextPrimary)
                    HudMetric(label = "LVL", value = "$level", accent = KaspaTea)
                    HudMetric(label = "COMBO", value = "x$comboMultiplier", accent = AmberCentral)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasShield) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, ElectricCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield Active",
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(15.dp)
                            )
                        }
                    }
                    if (overdriveSec > 0f) {
                        Surface(
                            shape = CircleShape,
                            color = AmberCentral.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AmberCentral)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Overdrive Active",
                                tint = AmberCentral,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(15.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!isGameOver) {
                                isPaused = !isPaused
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("arcade_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume Game" else "Pause Game",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }

        // Main Playable Arena Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, SurfaceCardBorder, RoundedCornerShape(18.dp))
                .clipToBounds()
                .onSizeChanged { intSize ->
                    val w = intSize.width.toFloat()
                    val h = intSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        arenaWidth = w
                        arenaHeight = h
                        groundY = h * 0.82f
                    }
                }
                .pointerInput(isGameOver, isPaused) {
                    detectTapGestures(
                        onTap = { triggerJump() }
                    )
                }
                .pointerInput(isGameOver, isPaused) {
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = { dragY = 0f },
                        onDrag = { _, dragAmount ->
                            dragY += dragAmount.y
                            if (dragY > 56f) {
                                triggerDive()
                                dragY = 0f
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val _tick = renderTick
                val gY = size.height * 0.82f

                val shakeOffset = if (shakeTimer > 0f) {
                    Offset(
                        x = (Random.nextFloat() - 0.5f) * 16f,
                        y = (Random.nextFloat() - 0.5f) * 16f
                    )
                } else Offset.Zero

                // Sky & DAG Grid Backdrop
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF070B12), Color(0xFF0D1726), Color(0xFF101C2E))
                    )
                )

                // Parallax stars
                stars.forEach { (sx, sy, r) ->
                    val px = ((sx * size.width - scrollOffset * (r * 0.08f)) % size.width + size.width) % size.width
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f + (r * 0.1f)),
                        radius = r,
                        center = Offset(px, sy * groundY) + shakeOffset
                    )
                }

                // Scrolling BlockDAG grid lines
                val gridSpacing = size.width * 0.14f
                val gridShift = scrollOffset * 0.35f % gridSpacing
                var gx = -gridShift
                while (gx < size.width + gridSpacing) {
                    drawLine(
                        color = KaspaTea.copy(alpha = 0.08f),
                        start = Offset(gx, groundY),
                        end = Offset(gx - gridSpacing * 0.45f, size.height),
                        strokeWidth = 2f
                    )
                    gx += gridSpacing
                }

                // Ground surface
                drawRect(
                    color = Color(0xFF0B111C),
                    topLeft = Offset(0f, groundY) + shakeOffset,
                    size = Size(size.width, size.height - groundY)
                )
                drawLine(
                    color = if (overdriveSec > 0f) AmberCentral else KaspaTea,
                    start = Offset(0f, groundY) + shakeOffset,
                    end = Offset(size.width, groundY) + shakeOffset,
                    strokeWidth = 4f
                )

                val playerSize = (min(size.width, size.height) * 0.068f).coerceIn(44f, 76f)
                val playerX = size.width * 0.18f
                val effectivePlayerY = playerY.coerceIn(playerSize * 0.6f, groundY - playerSize / 2f)

                // Draw pickups
                pickups.forEach { pk ->
                    val center = Offset(pk.x, pk.y) + shakeOffset
                    when (pk.kind) {
                        RunnerPickupKind.KAS_SHARD -> {
                            val r = playerSize * 0.27f
                            val diamond = Path().apply {
                                moveTo(center.x, center.y - r)
                                lineTo(center.x + r * 0.85f, center.y)
                                lineTo(center.x, center.y + r)
                                lineTo(center.x - r * 0.85f, center.y)
                                close()
                            }
                            drawCircle(color = KaspaTea.copy(alpha = 0.22f), radius = r * 1.45f, center = center)
                            drawPath(path = diamond, color = KaspaTea)
                            drawPath(path = diamond, color = Color.White, style = Stroke(width = 2f))
                        }
                        RunnerPickupKind.SHIELD_ORB -> {
                            val r = playerSize * 0.3f
                            drawCircle(color = ElectricCyan.copy(alpha = 0.26f), radius = r * 1.4f, center = center)
                            drawCircle(color = ElectricCyan, radius = r, center = center)
                            drawCircle(color = Color.White, radius = r * 0.55f, center = center, style = Stroke(width = 2.5f))
                        }
                        RunnerPickupKind.MAGNET_ORB -> {
                            val r = playerSize * 0.3f
                            drawCircle(color = VioletBridge.copy(alpha = 0.26f), radius = r * 1.4f, center = center)
                            drawCircle(color = VioletBridge, radius = r, center = center)
                        }
                        RunnerPickupKind.OVERDRIVE_ORB -> {
                            val r = playerSize * 0.32f
                            drawCircle(color = AmberCentral.copy(alpha = 0.28f), radius = r * 1.5f, center = center)
                            drawCircle(color = AmberCentral, radius = r, center = center)
                        }
                    }
                }

                // Draw obstacles
                obstacles.forEach { o ->
                    val actualY = if (o.kind == RunnerObstacleKind.HOVER_DRONE) {
                        o.baseY + sin(o.phase) * playerSize * 0.45f
                    } else {
                        o.baseY
                    }
                    when (o.kind) {
                        RunnerObstacleKind.CRYSTAL_SPIKE -> {
                            val path = Path().apply {
                                moveTo(o.x + o.width / 2f + shakeOffset.x, actualY + shakeOffset.y)
                                lineTo(o.x + o.width + shakeOffset.x, actualY + o.height + shakeOffset.y)
                                lineTo(o.x + shakeOffset.x, actualY + o.height + shakeOffset.y)
                                close()
                            }
                            drawPath(path = path, color = RedTamper)
                            drawPath(path = path, color = Color(0xFFFFA8A8), style = Stroke(width = 2.5f))
                        }
                        RunnerObstacleKind.HOVER_DRONE -> {
                            val topLeft = Offset(o.x, actualY) + shakeOffset
                            drawRoundRect(
                                color = Color(0xFFE05252),
                                topLeft = topLeft,
                                size = Size(o.width, o.height),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                            drawCircle(
                                color = AmberCentral,
                                radius = o.height * 0.22f,
                                center = Offset(o.x + o.width * 0.32f, actualY + o.height * 0.5f) + shakeOffset
                            )
                        }
                        RunnerObstacleKind.LASER_GATE -> {
                            val topLeft = Offset(o.x, actualY) + shakeOffset
                            drawRoundRect(
                                color = Color(0xFF9E2A2B),
                                topLeft = topLeft,
                                size = Size(o.width, o.height),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            drawRoundRect(
                                color = Color(0xFFFF6B6B),
                                topLeft = Offset(o.x + o.width * 0.25f, actualY + 6f) + shakeOffset,
                                size = Size(o.width * 0.5f, o.height - 12f),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                }

                // Draw particles
                particles.forEach { pt ->
                    val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = pt.color.copy(alpha = alpha),
                        radius = pt.radius * alpha,
                        center = Offset(pt.x, pt.y) + shakeOffset
                    )
                }

                // Draw Player Runner Craft
                val pCenter = Offset(playerX, effectivePlayerY) + shakeOffset
                val pitch = (playerVy / arenaHeight * 24f).coerceIn(-24f, 28f)
                val playerBlink = invulnTimer > 0f && ((invulnTimer * 18f).toInt() % 2 == 0)
                if (!playerBlink) {
                    if (hasShield) {
                        drawCircle(
                            color = ElectricCyan.copy(alpha = 0.28f),
                            radius = playerSize * 0.78f,
                            center = pCenter,
                            style = Stroke(width = 3.5f)
                        )
                    }
                    if (overdriveSec > 0f) {
                        drawCircle(
                            color = AmberCentral.copy(alpha = 0.24f),
                            radius = playerSize * 0.9f,
                            center = pCenter
                        )
                    }
                    // Thruster trail
                    drawOval(
                        color = (if (overdriveSec > 0f) AmberCentral else KaspaTea).copy(alpha = 0.55f),
                        topLeft = Offset(pCenter.x - playerSize * 0.9f, pCenter.y - playerSize * 0.14f),
                        size = Size(playerSize * 0.55f, playerSize * 0.28f)
                    )
                    rotate(degrees = pitch, pivot = pCenter) {
                        val hull = Path().apply {
                            moveTo(pCenter.x + playerSize * 0.52f, pCenter.y)
                            lineTo(pCenter.x - playerSize * 0.42f, pCenter.y - playerSize * 0.42f)
                            lineTo(pCenter.x - playerSize * 0.22f, pCenter.y)
                            lineTo(pCenter.x - playerSize * 0.42f, pCenter.y + playerSize * 0.42f)
                            close()
                        }
                        drawPath(
                            path = hull,
                            color = if (overdriveSec > 0f) AmberCentral else ElectricCyan
                        )
                        drawPath(
                            path = hull,
                            color = Color.White,
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }

            // Bottom subtle live control hints
            if (!isGameOver && !isPaused) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianBg.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Tap to Jump / Double-Jump • Swipe Down to Dive",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Pause or Game Over Overlay
            if (isPaused || isGameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.5.dp, if (isGameOver) RedTamper else KaspaTea),
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isGameOver) "Run Complete" else "Game Paused",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Score: $score  •  Best: $bestScore",
                                color = KaspaTea,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (isGameOver) resetGame() else isPaused = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KaspaTea,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("arcade_runner_primary_action")
                            ) {
                                Text(
                                    text = if (isGameOver) "Play Again" else "Resume",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isPaused && !isGameOver) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { resetGame() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text("Restart Run", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Kaspa2048GameView(
    bestScore: Int,
    onUpdateBest: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val engine = remember { Kaspa2048Engine() }
    var boardVersion by remember { mutableIntStateOf(0) }
    var boardSnapshot by remember { mutableStateOf(engine.grid.copyOf()) }
    var currentScore by remember { mutableIntStateOf(engine.score) }
    var highestTile by remember { mutableIntStateOf(engine.highestTile) }
    var canUndo by remember { mutableStateOf(engine.canUndo) }
    var showWinBanner by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    fun syncState() {
        boardVersion += 1
        boardSnapshot = engine.grid.copyOf()
        currentScore = engine.score
        highestTile = engine.highestTile
        canUndo = engine.canUndo
        onUpdateBest(currentScore)
        showWinBanner = engine.hasWon2048 && !engine.continueAfterWin
        isGameOver = engine.isGameOver
    }

    fun performMove(dir: MoveDirection) {
        if (engine.move(dir)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            syncState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // HUD Row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HudMetric(label = "SCORE", value = "$currentScore", accent = TextPrimary)
                    HudMetric(label = "MAX TILE", value = "$highestTile", accent = KaspaTea)
                    HudMetric(label = "BEST", value = "$bestScore", accent = AmberCentral)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            if (engine.undo()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                syncState()
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("arcade_2048_undo")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo Move",
                            tint = if (canUndo) TextPrimary else TextMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            engine.reset()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            syncState()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("arcade_2048_new")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New 2048 Game",
                            tint = KaspaTea
                        )
                    }
                }
            }
        }

        // Responsive 4x4 Tile Board with Swipe Input
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val boardEdge = minOf(maxWidth, maxHeight).coerceAtMost(420.dp)
            var dragX by remember { mutableFloatStateOf(0f) }
            var dragY by remember { mutableFloatStateOf(0f) }
            var swipeConsumed by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .size(boardEdge)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF141824))
                    .border(1.5.dp, SurfaceCardBorder, RoundedCornerShape(18.dp))
                    .padding(10.dp)
                    .pointerInput(boardVersion) {
                        detectDragGestures(
                            onDragStart = {
                                dragX = 0f
                                dragY = 0f
                                swipeConsumed = false
                            },
                            onDrag = { _, dragAmount ->
                                if (swipeConsumed) return@detectDragGestures
                                dragX += dragAmount.x
                                dragY += dragAmount.y
                                val threshold = 42f
                                if (abs(dragX) > threshold || abs(dragY) > threshold) {
                                    swipeConsumed = true
                                    if (abs(dragX) > abs(dragY)) {
                                        performMove(if (dragX > 0) MoveDirection.RIGHT else MoveDirection.LEFT)
                                    } else {
                                        performMove(if (dragY > 0) MoveDirection.DOWN else MoveDirection.UP)
                                    }
                                }
                            }
                        )
                    }
                    .testTag("arcade_2048_board")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (r in 0 until 4) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (c in 0 until 4) {
                                val value = boardSnapshot[r * 4 + c]
                                val (bg, fg) = tileColorsFor2048(value)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (value > 0) {
                                        Text(
                                            text = value.toString(),
                                            color = fg,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = when {
                                                value >= 1024 -> 18.sp
                                                value >= 128 -> 22.sp
                                                else -> 26.sp
                                            },
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showWinBanner || isGameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBg.copy(alpha = 0.84f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (showWinBanner) "2048 Block Forged!" else "No Moves Remaining",
                                color = if (showWinBanner) KaspaTea else TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Score $currentScore  •  Peak Tile $highestTile",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (showWinBanner && !isGameOver) {
                                    FilledTonalButton(
                                        onClick = {
                                            engine.continueEndless()
                                            syncState()
                                        },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Continue Endless")
                                    }
                                }
                                Button(
                                    onClick = {
                                        engine.reset()
                                        syncState()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KaspaTea,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Play Again", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tactile D-Pad Controls (accessible fallback alongside swipe gestures)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DpadIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                description = "Move Left",
                onClick = { performMove(MoveDirection.LEFT) }
            )
            DpadIconButton(
                icon = Icons.Default.KeyboardArrowUp,
                description = "Move Up",
                onClick = { performMove(MoveDirection.UP) }
            )
            DpadIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                description = "Move Down",
                onClick = { performMove(MoveDirection.DOWN) }
            )
            DpadIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                description = "Move Right",
                onClick = { performMove(MoveDirection.RIGHT) }
            )
        }
    }
}

private class BreakerPowerUp(
    var x: Float,
    var y: Float,
    val vy: Float,
    val isMultiBall: Boolean
)

@Composable
private fun PrismBreakerGameView(
    bestScore: Int,
    onUpdateBest: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var isPaused by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var level by remember { mutableIntStateOf(1) }
    var wideTimer by remember { mutableFloatStateOf(0f) }
    var restartSignal by remember { mutableIntStateOf(0) }

    var arenaWidth by remember { mutableFloatStateOf(0f) }
    var arenaHeight by remember { mutableFloatStateOf(0f) }
    var paddleCenterX by remember { mutableFloatStateOf(0f) }

    val bricks = remember { mutableStateListOf<BrickBreakerPhysics.Brick>() }
    val balls = remember { mutableStateListOf<BrickBreakerPhysics.Ball>() }
    val powerUps = remember { mutableStateListOf<BreakerPowerUp>() }
    val particles = remember { mutableStateListOf<ArcadeParticle>() }
    var initializedLevel by remember { mutableIntStateOf(0) }
    var renderTick by remember { mutableLongStateOf(0L) }

    fun spawnBricksForLevel(lvl: Int, w: Float, h: Float) {
        bricks.clear()
        val cols = 7
        val rows = (4 + (lvl - 1)).coerceAtMost(7)
        val sidePad = w * 0.045f
        val gap = w * 0.014f
        val brickW = (w - sidePad * 2f - gap * (cols - 1)) / cols
        val brickH = (h * 0.044f).coerceIn(24f, 40f)
        val topOffset = h * 0.06f

        val rowColors = listOf(
            0xFF63C3B5L, // Kaspa teal
            0xFF7C83E8L, // Violet
            0xFFE5A43BL, // Amber
            0xFF5BB9AAL, // Emerald
            0xFF38BDF8L, // Sky
            0xFFF472B6L, // Rose
            0xFFA78BFAL  // Lavender
        )
        var nextId = 1
        for (r in 0 until rows) {
            val hp = if (r < (lvl / 2).coerceAtLeast(1) && r % 2 == 0 && lvl > 1) 2 else 1
            for (c in 0 until cols) {
                // Create interesting cutout patterns on higher levels
                if (lvl % 2 == 0 && r == 1 && (c == 1 || c == cols - 2)) continue
                val left = sidePad + c * (brickW + gap)
                val top = topOffset + r * (brickH + gap)
                bricks.add(
                    BrickBreakerPhysics.Brick(
                        id = nextId++,
                        row = r,
                        col = c,
                        left = left,
                        top = top,
                        right = left + brickW,
                        bottom = top + brickH,
                        hp = hp,
                        maxHp = hp,
                        points = (rows - r) * 15 * hp,
                        colorArgb = rowColors[r % rowColors.size]
                    )
                )
            }
        }
    }

    fun spawnServeBall(w: Float, h: Float) {
        balls.clear()
        val baseSpeed = min(w, h) * (0.62f + (level - 1) * 0.04f).coerceAtMost(0.95f)
        val radius = (min(w, h) * 0.022f).coerceIn(11f, 18f)
        balls.add(
            BrickBreakerPhysics.Ball(
                x = paddleCenterX.coerceIn(w * 0.15f, w * 0.85f),
                y = h * 0.80f - radius * 1.5f,
                vx = baseSpeed * 0.48f,
                vy = -baseSpeed * 0.88f,
                radius = radius
            )
        )
    }

    fun resetGame() {
        score = 0
        lives = 3
        level = 1
        wideTimer = 0f
        isPaused = false
        isGameOver = false
        powerUps.clear()
        particles.clear()
        if (arenaWidth > 0f && arenaHeight > 0f) {
            paddleCenterX = arenaWidth / 2f
            spawnBricksForLevel(1, arenaWidth, arenaHeight)
            spawnServeBall(arenaWidth, arenaHeight)
            initializedLevel = 1
        } else {
            initializedLevel = 0
        }
        restartSignal += 1
    }

    LaunchedEffect(restartSignal, isPaused, isGameOver) {
        if (isPaused || isGameOver) return@LaunchedEffect
        var lastNanos = 0L
        while (!isPaused && !isGameOver) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
                lastNanos = now

                if (arenaWidth <= 10f || arenaHeight <= 10f) return@withFrameNanos
                if (initializedLevel == 0) {
                    paddleCenterX = arenaWidth / 2f
                    spawnBricksForLevel(level, arenaWidth, arenaHeight)
                    spawnServeBall(arenaWidth, arenaHeight)
                    initializedLevel = level
                }

                if (wideTimer > 0f) wideTimer = max(0f, wideTimer - dt)

                val paddleWidth = arenaWidth * (if (wideTimer > 0f) 0.34f else 0.24f)
                val paddleHeight = (arenaHeight * 0.028f).coerceIn(18f, 28f)
                val paddleY = arenaHeight * 0.86f
                paddleCenterX = paddleCenterX.coerceIn(paddleWidth / 2f + 6f, arenaWidth - paddleWidth / 2f - 6f)

                val pLeft = paddleCenterX - paddleWidth / 2f
                val pRight = paddleCenterX + paddleWidth / 2f
                val pTop = paddleY
                val pBottom = paddleY + paddleHeight

                // Update powerups
                val puIter = powerUps.iterator()
                while (puIter.hasNext()) {
                    val pu = puIter.next()
                    pu.y += pu.vy * dt
                    val hitsPaddle = pu.x in pLeft..pRight && pu.y + 14f >= pTop && pu.y - 14f <= pBottom
                    if (hitsPaddle) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pu.isMultiBall && balls.size < 3 && balls.isNotEmpty()) {
                            val src = balls.first()
                            balls.add(
                                BrickBreakerPhysics.Ball(
                                    x = src.x,
                                    y = src.y,
                                    vx = -src.vx * 1.04f,
                                    vy = src.vy,
                                    radius = src.radius
                                )
                            )
                        } else {
                            wideTimer = 10f
                        }
                        puIter.remove()
                    } else if (pu.y > arenaHeight + 30f) {
                        puIter.remove()
                    }
                }

                // Update balls
                val bIter = balls.iterator()
                while (bIter.hasNext()) {
                    val ball = bIter.next()
                    ball.x += ball.vx * dt
                    ball.y += ball.vy * dt

                    // Wall bounces
                    if (ball.x - ball.radius <= 0f) {
                        ball.x = ball.radius
                        ball.vx = abs(ball.vx)
                    } else if (ball.x + ball.radius >= arenaWidth) {
                        ball.x = arenaWidth - ball.radius
                        ball.vx = -abs(ball.vx)
                    }
                    if (ball.y - ball.radius <= 0f) {
                        ball.y = ball.radius
                        ball.vy = abs(ball.vy)
                    }

                    // Paddle bounce with spin factor
                    if (ball.vy > 0f &&
                        ball.x + ball.radius >= pLeft &&
                        ball.x - ball.radius <= pRight &&
                        ball.y + ball.radius >= pTop &&
                        ball.y - ball.radius <= pBottom
                    ) {
                        val hitRatio = ((ball.x - paddleCenterX) / (paddleWidth * 0.5f)).coerceIn(-1f, 1f)
                        val speed = kotlin.math.sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
                        val angle = hitRatio * 1.15f // up to ~66 deg
                        ball.vx = sin(angle) * speed
                        ball.vy = -cos(angle) * speed
                        ball.y = pTop - ball.radius - 1f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    // Brick collision (one brick per substep per ball)
                    for (idx in bricks.indices) {
                        val br = bricks[idx]
                        if (BrickBreakerPhysics.resolveCircleRectCollision(ball, br.left, br.top, br.right, br.bottom)) {
                            br.hp -= 1
                            if (br.hp <= 0) {
                                score += br.points
                                onUpdateBest(score)
                                val bc = Color(br.colorArgb)
                                repeat(9) { pIdx ->
                                    val ang = (pIdx / 9f) * 6.283f
                                    particles.add(
                                        ArcadeParticle(
                                            x = (br.left + br.right) / 2f,
                                            y = (br.top + br.bottom) / 2f,
                                            vx = cos(ang) * 190f,
                                            vy = sin(ang) * 190f,
                                            life = 0.4f,
                                            maxLife = 0.4f,
                                            radius = 5f,
                                            color = bc
                                        )
                                    )
                                }
                                if (Random.nextFloat() < 0.18f) {
                                    powerUps.add(
                                        BreakerPowerUp(
                                            x = (br.left + br.right) / 2f,
                                            y = br.bottom,
                                            vy = arenaHeight * 0.28f,
                                            isMultiBall = Random.nextBoolean()
                                        )
                                    )
                                }
                                bricks.removeAt(idx)
                            } else {
                                bricks[idx] = br.copy()
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            break
                        }
                    }

                    if (ball.y - ball.radius > arenaHeight) {
                        bIter.remove()
                    }
                }

                // Update particles
                val pIter = particles.iterator()
                while (pIter.hasNext()) {
                    val pt = pIter.next()
                    pt.x += pt.vx * dt
                    pt.y += pt.vy * dt
                    pt.life -= dt
                    if (pt.life <= 0f) pIter.remove()
                }

                if (bricks.isEmpty()) {
                    level += 1
                    score += 100 * level
                    onUpdateBest(score)
                    spawnBricksForLevel(level, arenaWidth, arenaHeight)
                    spawnServeBall(arenaWidth, arenaHeight)
                    initializedLevel = level
                } else if (balls.isEmpty()) {
                    lives -= 1
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (lives <= 0) {
                        onUpdateBest(score)
                        isGameOver = true
                    } else {
                        spawnServeBall(arenaWidth, arenaHeight)
                    }
                }

                renderTick = now
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HudMetric(label = "SCORE", value = "$score", accent = TextPrimary)
                    HudMetric(label = "LEVEL", value = "$level", accent = KaspaTea)
                    HudMetric(label = "LIVES", value = "$lives", accent = if (lives <= 1) RedTamper else EmeraldMesh)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (wideTimer > 0f) {
                        Text(
                            text = "WIDE ${wideTimer.toInt() + 1}s",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (!isGameOver) isPaused = !isPaused },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume Breaker" else "Pause Breaker",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, SurfaceCardBorder, RoundedCornerShape(18.dp))
                .clipToBounds()
                .onSizeChanged { intSize ->
                    val w = intSize.width.toFloat()
                    val h = intSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        arenaWidth = w
                        arenaHeight = h
                    }
                }
                .pointerInput(isGameOver, isPaused) {
                    detectDragGestures { change, _ ->
                        paddleCenterX = change.position.x
                    }
                }
                .pointerInput(isGameOver, isPaused) {
                    detectTapGestures { offset ->
                        paddleCenterX = offset.x
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val _tick = renderTick

                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF070B12), Color(0xFF0E1525), Color(0xFF090D16))
                    )
                )

                // Bricks
                bricks.forEach { br ->
                    val base = Color(br.colorArgb)
                    val color = if (br.hp > 1) base.copy(alpha = 0.95f) else base.copy(alpha = 0.85f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(br.left, br.top),
                        size = Size(br.right - br.left, br.bottom - br.top),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    if (br.hp > 1) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.65f),
                            topLeft = Offset(br.left + 2f, br.top + 2f),
                            size = Size(br.right - br.left - 4f, br.bottom - br.top - 4f),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }

                // Falling powerups
                powerUps.forEach { pu ->
                    val puColor = if (pu.isMultiBall) AmberCentral else ElectricCyan
                    drawRoundRect(
                        color = puColor,
                        topLeft = Offset(pu.x - 16f, pu.y - 10f),
                        size = Size(32f, 20f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }

                // Paddle
                val paddleWidth = size.width * (if (wideTimer > 0f) 0.34f else 0.24f)
                val paddleHeight = (size.height * 0.028f).coerceIn(18f, 28f)
                val paddleY = size.height * 0.86f
                val safePaddleCenter = if (paddleCenterX <= 0f) size.width / 2f else paddleCenterX
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(CyanGlow, KaspaTea, ElectricCyan)),
                    topLeft = Offset(safePaddleCenter - paddleWidth / 2f, paddleY),
                    size = Size(paddleWidth, paddleHeight),
                    cornerRadius = CornerRadius(14f, 14f)
                )

                // Balls
                balls.forEach { b ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = b.radius * 1.4f,
                        center = Offset(b.x, b.y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = b.radius,
                        center = Offset(b.x, b.y)
                    )
                }

                // Particles
                particles.forEach { pt ->
                    val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = pt.color.copy(alpha = alpha),
                        radius = pt.radius * alpha,
                        center = Offset(pt.x, pt.y)
                    )
                }
            }

            if (!isGameOver && !isPaused) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianBg.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Drag Paddle to Deflect & Break Bricks",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (isPaused || isGameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.5.dp, if (isGameOver) RedTamper else KaspaTea),
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isGameOver) "Game Over" else "Paused",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Score: $score  •  Best: $bestScore",
                                color = KaspaTea,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { if (isGameOver) resetGame() else isPaused = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KaspaTea,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isGameOver) "Play Again" else "Resume",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isPaused && !isGameOver) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { resetGame() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text("Restart Level 1", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetric(
    label: String,
    value: String,
    accent: Color
) {
    Column {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Text(
            text = value,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DpadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun tileColorsFor2048(value: Int): Pair<Color, Color> {
    return when (value) {
        0 -> Color(0xFF1B2030) to TextMuted
        2 -> Color(0xFF232B3D) to TextPrimary
        4 -> Color(0xFF2A364F) to TextPrimary
        8 -> Color(0xFF2B6F68) to Color.White
        16 -> Color(0xFF3E9488) to Color.White
        32 -> Color(0xFF4FB3A5) to Color(0xFF061311)
        64 -> Color(0xFF63C3B5) to Color(0xFF061311)
        128 -> Color(0xFF5D66D4) to Color.White
        256 -> Color(0xFF7C83E8) to Color.White
        512 -> Color(0xFFD9822B) to Color(0xFF140B02)
        1024 -> Color(0xFFE5A43B) to Color(0xFF140B02)
        else -> Color(0xFFF59E0B) to Color(0xFF140B02)
    }
}
