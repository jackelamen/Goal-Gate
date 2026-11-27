package com.goalgate.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.goalgate.app.GoalGateApp
import com.goalgate.app.data.Engine
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.ui.PauseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The brain shared by both detection engines.
 *
 * Each engine has only one job: tell this object "package X is now in the
 * foreground". This object then decides whether to show the pause.
 *
 * The rules for OPENING an app:
 *  1. Only react when the foreground app CHANGES. While the user stays in the
 *     same app, nothing happens (no re-trigger).
 *  2. Only react to apps the user picked.
 *  3. Respect the cooldown: after "Continue", the same app is left alone
 *     for the number of minutes chosen in Settings.
 *
 * The TIME CHECK (optional, set in Settings):
 *  While the user is in a chosen app (after its pause), a clock
 *  counts how long they stay. When it reaches the limit, they get another
 *  pause. Leaving the app, or turning the screen off, pauses the clock.
 *  A break shorter than one minute carries on where it left off; a longer
 *  break starts it from zero. See SessionTimer for the exact rules.
 *
 * MEMORY: cooldowns and the time-check clocks are saved on the phone after
 * every change (see saveState), so they survive Android restarting GoalGate
 * in the background. Without this, a restart would forget a recent
 * "Continue" and pop up a pause in the middle of using an app.
 */
object GateController {

    private const val TAG = "GoalGate"
    private const val KEY_COOLDOWNS = "cooldowns"
    private const val KEY_CLOCKS = "clocks"
    private const val KEY_DOWNTIME_UNTIL = "downtimeUntil"
    private const val KEY_DOWNTIME_MINUTES = "downtimeMinutes"
    private const val KEY_REST_IS_BREAK = "restIsBreak"

    /** The packages the user picked. Kept up to date by [watchSelectedApps]. */
    @Volatile
    private var selectedPackages: Set<String> = emptySet()

    /** The app that was in the foreground the last time an engine reported. */
    private var lastForeground: String? = null

    /** For each package: the time (ms) until which we must not ask again. */
    private val cooldownUntil = HashMap<String, Long>()

    // ---- Time check state ----

    /** Counts how long each chosen app has been used (see SessionTimer). */
    private val timer = SessionTimer()

    /** Runs the time check on the main thread after a delay. */
    private val handler = Handler(Looper.getMainLooper())

    /** For tests only: a clean controller for [context], with [packages] as the chosen apps. */
    @androidx.annotation.VisibleForTesting
    @Synchronized
    internal fun resetForTest(context: Context, packages: Set<String>) {
        forgetEverything()
        appContext = context.applicationContext
        initialized = true
        selectedPackages = packages
    }

    /** Needed to open the pause screen when the time is up. */
    private var appContext: Context? = null

    /** The app whose clock is running right now, or null. */
    private var runningPackage: String? = null

    /** The engine that last reported, so the time-check pause is logged under it. */
    private var lastEngine = Engine.USAGE

    private val timeUp = Runnable { onTimeUp() }

    // ---- Downtime ----

    /**
     * "It's my downtime": until this time (ms), no chosen app is paused and no
     * time check runs. 0 when there is no downtime.
     */
    private var downtimeUntil = 0L

    /** The length the user chose, for the message when it ends ("Your 30 minutes are up"). */
    private var downtimeMinutes = 0

    /** True when the rest in progress is a break, false for downtime (the end message differs). */
    private var restIsBreak = false

    private val downtimeOver = Runnable { onDowntimeOver() }

    // ---- Telling real opens from app-switching flicker (see SwitchGuard) ----

    private val guard = SwitchGuard()

    /** The time now, in ms since 1970. Tests replace it with a clock they can move forward. */
    @androidx.annotation.VisibleForTesting
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * A pause waiting to be confirmed: shown only if its app is still in front
     * after SwitchGuard.CONFIRM_MS. Cancelled as soon as another app comes to the front.
     */
    private var pendingConfirm: Runnable? = null

    private fun cancelPendingConfirm() {
        pendingConfirm?.let { handler.removeCallbacks(it) }
        pendingConfirm = null
    }

    /** Runs [action] after a short wait, unless another app comes to the front first. */
    private fun confirmLater(action: () -> Unit) {
        cancelPendingConfirm()
        val runnable = Runnable { synchronized(this) { pendingConfirm = null; action() } }
        pendingConfirm = runnable
        handler.postDelayed(runnable, SwitchGuard.CONFIRM_MS)
    }

    /** True if the screen is on and [packageName] is still the app in front. */
    private fun stillInFront(context: Context, packageName: String): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isInteractive && lastForeground == packageName
    }

    /**
     * After a restart: the app whose clock was running when GoalGate stopped.
     * Kept only if that same app is the first one reported in front again.
     */
    private var restoredRunning: String? = null

    private var initialized = false

    /**
     * Called once when the app process starts (from GoalGateApp).
     * Loads the cooldowns and clocks saved before the last restart.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        val prefs = statePrefs(context)
        val now = clock()
        try {
            val saved = JSONObject(prefs.getString(KEY_COOLDOWNS, null) ?: "{}")
            for (packageName in saved.keys()) {
                val until = saved.getLong(packageName)
                if (until > now) cooldownUntil[packageName] = until
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read saved cooldowns", e)
        }
        timer.restore(prefs.getString(KEY_CLOCKS, null))
        restoredRunning = timer.runningPackage()
        downtimeUntil = prefs.getLong(KEY_DOWNTIME_UNTIL, 0L)
        downtimeMinutes = prefs.getInt(KEY_DOWNTIME_MINUTES, 0)
        restIsBreak = prefs.getBoolean(KEY_REST_IS_BREAK, false)
        // Still in downtime after a restart: schedule its end again.
        if (downtimeUntil > now) handler.postDelayed(downtimeOver, downtimeUntil - now) else downtimeUntil = 0L
    }

    /** Writes the cooldowns and clocks to the phone. Called after every change. */
    private fun saveState() {
        val context = appContext ?: return
        val now = clock()
        val cooldowns = JSONObject()
        for ((packageName, until) in cooldownUntil) {
            if (until > now) cooldowns.put(packageName, until) // drop expired ones
        }
        statePrefs(context).edit()
            .putString(KEY_COOLDOWNS, cooldowns.toString())
            .putString(KEY_CLOCKS, timer.toJson())
            .putLong(KEY_DOWNTIME_UNTIL, downtimeUntil)
            .putInt(KEY_DOWNTIME_MINUTES, downtimeMinutes)
            .putBoolean(KEY_REST_IS_BREAK, restIsBreak)
            .apply()
    }

    private fun statePrefs(context: Context) =
        context.getSharedPreferences("gate_state", Context.MODE_PRIVATE)

    private var screenReceiverRegistered = false

    /**
     * Keeps [selectedPackages] in sync with the database for as long as [scope] lives,
     * and starts listening for the screen turning off and on (for the time check).
     * Both services call this when they start.
     */
    fun watchSelectedApps(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        init(app)
        registerScreenReceiver(app)
        val dao = (app as GoalGateApp).database.selectedAppDao()
        scope.launch {
            dao.observeAll().collect { apps ->
                selectedPackages = apps.map { it.packageName }.toSet()
            }
        }
    }

    /**
     * Called by an engine when it sees an app come to the foreground.
     *
     * @param openedAt when the app came to the foreground (ms since 1970).
     *                 Used later to measure how long the pause took to appear.
     */
    @Synchronized
    fun onForegroundApp(context: Context, packageName: String, openedAt: Long, engine: Engine) {
        lastEngine = engine

        // Rule 1: nothing changed, the user is still in the same app.
        if (packageName == lastForeground) return
        // The app that was in front has just been left (remembered for SwitchGuard).
        lastForeground?.let { guard.left(it, clock()) }
        lastForeground = packageName
        // A pause waiting for confirmation belonged to the app that was just left.
        cancelPendingConfirm()

        try {
            handleNewForeground(context, packageName, openedAt, engine)
        } finally {
            saveState()
        }
    }

    private fun handleNewForeground(context: Context, packageName: String, openedAt: Long, engine: Engine) {
        val now = clock()

        // First report after a restart: if the user is still in the app whose
        // clock was running, keep counting. Otherwise that clock is out of date.
        restoredRunning?.let { restored ->
            restoredRunning = null
            if (restored == packageName) runningPackage = restored else timer.reset(restored)
        }

        // Whatever app was being timed has just been left: pause its clock.
        pauseClock(now)

        // GoalGate's own screens never trigger a pause.
        if (packageName == context.packageName) return

        // Rule 2: not one of the chosen apps.
        if (packageName !in selectedPackages) return

        // Downtime the user chose: every chosen app is left alone until it ends.
        if (now < downtimeUntil) return

        // Rule 3: still cooling down after a recent "Continue".
        // No pause, but the time check keeps counting.
        val until = cooldownUntil[packageName] ?: 0L
        if (now < until) {
            resumeClock(packageName, now)
            return
        }

        // Back in front right after it was left, or right after Close on its pause:
        // possibly a flicker while the phone switches apps. Check again shortly.
        if (guard.isSuspicious(packageName, now)) {
            Log.d(TAG, "Possible flicker of $packageName; checking again shortly")
            confirmLater { openIfStillThere(context, packageName, engine) }
            return
        }

        Log.d(TAG, "Trigger for $packageName via $engine")
        timer.reset(packageName) // a pause resets the time check
        openPause(context, PauseActivity.createIntent(context, packageName, openedAt, engine))
    }

    /** The second look after a possible flicker: the pause shows only if the app really stayed. */
    private fun openIfStillThere(context: Context, packageName: String, engine: Engine) {
        val now = clock()
        if (!stillInFront(context, packageName)) return
        if (packageName !in selectedPackages || now < downtimeUntil || now < (cooldownUntil[packageName] ?: 0L)) return
        Log.d(TAG, "Trigger for $packageName via $engine (confirmed)")
        timer.reset(packageName)
        saveState()
        // Measured from now, so the wait is not counted as detection delay on the debug screen.
        openPause(context, PauseActivity.createIntent(context, packageName, now, engine))
    }

    /**
     * Called when the user taps Close (or Back) on the pause for [packageName].
     * For a few seconds after, a report of that app in front is checked twice
     * before a new pause is shown (see SwitchGuard).
     */
    @Synchronized
    fun noteClosed(packageName: String) {
        guard.closed(packageName, clock())
    }

    /**
     * Called when the user taps "Continue": leave this app alone for a while,
     * and start its time check from zero.
     */
    @Synchronized
    fun startCooldown(packageName: String, minutes: Int) {
        val now = clock()
        cooldownUntil[packageName] = now + minutes * 60_000L
        restoredRunning = null
        pauseClock(now)
        timer.start(packageName, now)
        resumeClock(packageName, now)
        saveState()
    }

    /**
     * Called when the user chooses "It's my downtime" or "Take a break" and goes on: for
     * [minutes], no chosen app is paused and no time check runs. When the time
     * is up and the user is still in a chosen app, one gentle check appears.
     */
    @Synchronized
    fun startDowntime(packageName: String, minutes: Int, isBreak: Boolean = false) {
        val now = clock()
        downtimeUntil = now + minutes * 60_000L
        downtimeMinutes = minutes
        restIsBreak = isBreak
        restoredRunning = null
        pauseClock(now)
        timer.reset(packageName)
        handler.removeCallbacks(downtimeOver)
        handler.postDelayed(downtimeOver, minutes * 60_000L)
        saveState()
    }

    /** True while a downtime the user chose is running. */
    fun inDowntime(): Boolean = clock() < downtimeUntil

    /** The downtime ended. If the user is still in a chosen app, show the gentle check. */
    @Synchronized
    private fun onDowntimeOver() {
        val context = appContext ?: return
        val minutes = downtimeMinutes
        val wasBreak = restIsBreak
        downtimeUntil = 0L
        saveState()
        val packageName = lastForeground ?: return
        if (packageName == context.packageName || packageName !in selectedPackages) return
        // The user may be closing the app at this very moment: look again shortly
        // and show the end check only if they are really still in it.
        confirmLater {
            if (!stillInFront(context, packageName)) return@confirmLater
            Log.d(TAG, "Downtime of $minutes min over in $packageName")
            openPause(
                context,
                PauseActivity.createIntent(
                    context, packageName, clock(), lastEngine,
                    reason = if (wasBreak) TriggerLog.REASON_BREAK_END else TriggerLog.REASON_DOWNTIME_END,
                    minutesUsed = minutes,
                ),
            )
        }
    }

    /** Forgets cooldowns, clocks and the last app seen (used by "Reset GoalGate"). */
    @Synchronized
    fun forgetEverything() {
        handler.removeCallbacks(timeUp)
        cancelPendingConfirm()
        guard.clear()
        handler.removeCallbacks(downtimeOver)
        downtimeUntil = 0L
        downtimeMinutes = 0
        cooldownUntil.clear()
        timer.clear()
        runningPackage = null
        restoredRunning = null
        lastForeground = null
    }

    // ---- Time check ----

    /** The time-check limit from Settings, in ms. 0 means the time check is off. */
    private fun limitMs(): Long =
        (appContext as? GoalGateApp)?.settings?.sessionLimitMinutes?.let { it * 60_000L } ?: 0L

    /** Starts or resumes the clock for [packageName] and schedules the check. */
    private fun resumeClock(packageName: String, now: Long) {
        val limit = limitMs()
        if (limit <= 0) return // the time check is switched off
        val remaining = timer.enter(packageName, now, limit)
        runningPackage = packageName
        handler.removeCallbacks(timeUp)
        handler.postDelayed(timeUp, remaining)
    }

    /** Pauses the running clock, if any, and cancels the scheduled check. */
    private fun pauseClock(now: Long) {
        handler.removeCallbacks(timeUp)
        runningPackage?.let { timer.leave(it, now) }
        runningPackage = null
    }

    /** The timer ran out. If the user is still in the app, show the pause again. */
    @Synchronized
    private fun onTimeUp() {
        val packageName = runningPackage ?: return
        val context = appContext ?: return
        if (limitMs() <= 0 || !stillInFront(context, packageName)) return
        // The user may be closing the app at this very moment: look again shortly.
        confirmLater { fireTimeCheck(context, packageName) }
    }

    /** The time check, once it is confirmed the user is still in [packageName]. */
    private fun fireTimeCheck(context: Context, packageName: String) {
        if (limitMs() <= 0 || !stillInFront(context, packageName) || runningPackage != packageName) return

        val now = clock()
        val minutesUsed = (timer.usedMs(packageName, now) / 60_000L).toInt().coerceAtLeast(1)
        // The pause resets the clock; "Continue" on it starts a fresh one.
        runningPackage = null
        timer.reset(packageName)
        saveState()
        Log.d(TAG, "Time check for $packageName after $minutesUsed min")
        openPause(
            context,
            PauseActivity.createIntent(
                context, packageName, now, lastEngine,
                reason = TriggerLog.REASON_TIME_CHECK,
                minutesUsed = minutesUsed,
            ),
        )
    }

    /**
     * Screen off counts as leaving the app: the clock pauses. When the user
     * unlocks back into the same app, the usual break rule applies (under a
     * minute carries on, longer starts from zero).
     */
    @Synchronized
    private fun onScreenChanged(screenOn: Boolean) {
        val now = clock()
        if (!screenOn) {
            pauseClock(now)
        } else if (now >= downtimeUntil) {
            val current = lastForeground ?: return
            if (runningPackage == null && timer.has(current)) resumeClock(current, now)
        }
        saveState()
    }

    private fun registerScreenReceiver(context: Context) {
        if (screenReceiverRegistered) return
        screenReceiverRegistered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT) // unlocked
            addAction(Intent.ACTION_SCREEN_ON)    // on without a lock screen
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenChanged(screenOn = false)
                    Intent.ACTION_USER_PRESENT -> onScreenChanged(screenOn = true)
                    Intent.ACTION_SCREEN_ON -> {
                        // With a lock screen, wait for ACTION_USER_PRESENT instead.
                        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                        if (!keyguard.isKeyguardLocked) onScreenChanged(screenOn = true)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun openPause(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Android refused to open the screen (usually a missing permission).
            Log.w(TAG, "Could not open the pause screen", e)
        }
    }
}
