package com.goalgate.app

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.goalgate.app.data.Engine
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.detection.GateController
import com.goalgate.app.detection.SwitchGuard
import com.goalgate.app.ui.PauseActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

/**
 * "GoalGate pops up when I'm closing the app": replays the reports a phone
 * sends while switching apps, through the real GateController, and counts
 * the pauses that open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SwitchGuardTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val own = app.packageName
    private val insta = "com.instagram.android"
    private val home = "com.sec.android.app.launcher"

    @Before
    fun setUp() {
        GateController.resetForTest(app, setOf(insta))
        // A clock that moves with the simulated time (the real one stands still in tests).
        val base = 1_800_000_000_000L
        GateController.clock = { base + android.os.SystemClock.uptimeMillis() }
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        drainPauses()
    }

    private fun report(packageName: String) =
        GateController.onForegroundApp(app, packageName, GateController.clock(), Engine.USAGE)

    private fun advance(ms: Long) = ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS)

    /** The pauses opened since the last call, as their "reason" extras. */
    private fun drainPauses(): List<String> {
        val reasons = mutableListOf<String>()
        while (true) {
            val intent = shadowOf(app).nextStartedActivity ?: break
            if (intent.component?.className == PauseActivity::class.java.name) {
                reasons += intent.getStringExtra("reason") ?: TriggerLog.REASON_OPEN
            }
        }
        return reasons
    }

    // --- The rule on its own ---

    @Test
    fun testClock_movesWithSimulatedTime() {
        val before = GateController.clock()
        advance(120_000)
        assertEquals(120_000L, GateController.clock() - before)
    }

    @Test
    fun guard_flagsOnlyRecentLeavesAndCloses() {
        val guard = SwitchGuard()
        assertFalse(guard.isSuspicious(insta, 10_000))       // never left: a normal open
        guard.left(insta, 10_000)
        assertTrue(guard.isSuspicious(insta, 10_300))        // back 0.3 s later: maybe a flicker
        assertFalse(guard.isSuspicious(insta, 12_000))       // 2 s later: a real open
        guard.closed(insta, 20_000)
        assertTrue(guard.isSuspicious(insta, 22_500))        // within 3 s of Close
        assertFalse(guard.isSuspicious(insta, 23_000))
        assertFalse(guard.isSuspicious(home, 20_100))        // other apps are not affected
    }

    // --- Real sequences through the controller ---

    @Test
    fun normalOpen_isStillInstant() {
        report(home)
        advance(5_000)
        report(insta)
        assertEquals(listOf("OPEN"), drainPauses()) // no waiting at all
    }

    @Test
    fun close_thenTheAppFlickersBehindTheHomeScreen_noSecondPause() {
        report(home); advance(5_000)
        report(insta)
        assertEquals(1, drainPauses().size)          // the pause appears
        report(own); advance(10_000)                    // the user reads it, then taps Close
        GateController.noteClosed(insta)
        report(insta); advance(80)                      // Instagram shows for a split second...
        report(home)                                 // ...then the home screen
        advance(2_000)
        assertEquals(emptyList<String>(), drainPauses())
    }

    @Test
    fun close_thenReallyReopening_stillPauses() {
        report(home); advance(5_000)
        report(insta); drainPauses()
        report(own); advance(10_000)
        GateController.noteClosed(insta)
        report(home); advance(500)
        report(insta)                                // the user really opens it again, and stays
        assertEquals(emptyList<String>(), drainPauses())
        advance(1_000)
        assertEquals(listOf("OPEN"), drainPauses())  // shown after the second look
    }

    @Test
    fun leavingWithHome_whenTheAppReportsOnceMore_noPause() {
        report(home); advance(5_000)
        report(insta); drainPauses()
        report(own); advance(3_000)
        GateController.startCooldown(insta, 1)       // Continue
        report(insta); advance(120_000)                 // the cooldown runs out while using it
        report(home); advance(150)                      // Home...
        report(insta); advance(100)                     // ...a late report from the closing app...
        report(home)                                 // ...and home again
        advance(2_000)
        assertEquals(emptyList<String>(), drainPauses())
    }

    @Test
    fun timeCheck_firingWhileTheUserLeaves_noPause_butWhileStaying_pauses() {
        (app as GoalGateApp).settings.sessionLimitMinutes = 5
        report(home); advance(5_000)
        report(insta); drainPauses()
        report(own); advance(2_000)
        GateController.startCooldown(insta, 1)
        report(insta)
        advance(5 * 60_000L)                            // 5 minutes: the time check is due...
        report(home)                                 // ...just as the user leaves
        advance(2_000)
        assertEquals(emptyList<String>(), drainPauses())

        // Staying in the app: the time check does appear.
        report(insta); advance(1_000); drainPauses()
        report(own); advance(1_000)
        GateController.startCooldown(insta, 1)
        report(insta)
        advance(5 * 60_000L + 1_000)
        assertEquals(listOf("TIME_CHECK"), drainPauses())
        (app as GoalGateApp).settings.sessionLimitMinutes = 0
    }
}
