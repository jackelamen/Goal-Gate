package com.goalgate.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.goalgate.app.data.AppDatabase
import com.goalgate.app.data.Goal
import com.goalgate.app.data.GoalStep
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.data.nextStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * Database update test: builds a version 2 database exactly as WordGate's
 * Room wrote it, with real rows in it, then opens it with this version of
 * the app (version 3) and checks that nothing was lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val background = Executors.newSingleThreadExecutor()

    @After
    fun cleanUp() {
        context.deleteDatabase(AppDatabase.FILE_NAME)
        background.shutdown()
    }

    @Test
    fun version2To3_keepsTheLog_andAddsGoals() {
        // 1. A version 2 database with the old tables and a few rows.
        context.deleteDatabase(AppDatabase.FILE_NAME)
        val file = context.getDatabasePath(AppDatabase.FILE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS selected_apps (packageName TEXT NOT NULL, PRIMARY KEY(packageName))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS trigger_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "packageName TEXT NOT NULL, openedAt INTEGER NOT NULL, delayMs INTEGER NOT NULL, " +
                    "engine TEXT NOT NULL, questionId TEXT NOT NULL, correct INTEGER, action TEXT NOT NULL, " +
                    "reason TEXT NOT NULL DEFAULT 'OPEN')"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS question_progress (questionId TEXT NOT NULL, timesSeen INTEGER NOT NULL, " +
                    "timesWrong INTEGER NOT NULL, lastAnswerWrong INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(questionId))"
            )
            db.execSQL("INSERT INTO selected_apps VALUES ('com.instagram.android')")
            db.execSQL(
                "INSERT INTO trigger_logs (packageName, openedAt, delayMs, engine, questionId, correct, action, reason) " +
                    "VALUES ('com.instagram.android', 1000, 240, 'USAGE', 'ev01', 1, 'CLOSE', 'OPEN')"
            )
            db.execSQL(
                "INSERT INTO trigger_logs (packageName, openedAt, delayMs, engine, questionId, correct, action, reason) " +
                    "VALUES ('com.zhiliaoapp.musically', 2000, 310, 'ACCESSIBILITY', 'hg50', NULL, 'NONE', 'TIME_CHECK')"
            )
            db.execSQL("INSERT INTO question_progress VALUES ('ev01', 1, 0, 0, 1000)")
            db.version = 2
        }

        // 2. Open it with Room (off the main thread, as the app does).
        val database = AppDatabase.create(context)
        val logs = background.submit<List<TriggerLog>> {
            database.triggerLogDao().getAll().sortedBy { it.openedAt }
        }.get()

        // 3. The old rows are all there, unchanged, with the new columns empty.
        assertEquals(2, logs.size)
        val first = logs[0]
        assertEquals("com.instagram.android", first.packageName)
        assertEquals(240L, first.delayMs)
        assertEquals("ev01", first.questionId)
        assertEquals(true, first.correct)
        assertEquals("CLOSE", first.action)
        assertEquals("OPEN", first.reason)
        assertNull(first.goalId)
        assertNull(first.answer)
        assertEquals("TIME_CHECK", logs[1].reason)
        assertNull(logs[1].correct)

        // 4. The chosen apps survived, and the new goals table works.
        val apps = runBlocking { database.selectedAppDao().observeAll().first() }
        assertEquals(listOf("com.instagram.android"), apps.map { it.packageName })
        runBlocking { database.goalDao().insert(Goal(text = "Walk every morning", position = 0, createdAt = 5)) }
        val goals = background.submit<List<Goal>> { database.goalDao().getAll() }.get()
        assertEquals("Walk every morning", goals.single().text)
        assertNull(goals.single().achievedAt)

        // 5. WordGate's question table is gone.
        val tables = background.submit<List<String>> {
            database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }.get()
        assertTrue("goals" in tables)
        assertTrue("goal_steps" in tables)
        assertFalse("question_progress" in tables)
        assertEquals(Goal.SCHEDULE_ALWAYS, goals.single().schedule)
        database.close()
    }

    @Test
    fun version3To4_keepsGoalsAndAnswers_andAddsStepsAndDowntime() {
        // 1. A version 3 database (GoalGate before steps and downtime), with a goal and an answered pause.
        context.deleteDatabase(AppDatabase.FILE_NAME)
        val file = context.getDatabasePath(AppDatabase.FILE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS selected_apps (packageName TEXT NOT NULL, PRIMARY KEY(packageName))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS trigger_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "packageName TEXT NOT NULL, openedAt INTEGER NOT NULL, delayMs INTEGER NOT NULL, " +
                    "engine TEXT NOT NULL, questionId TEXT NOT NULL, correct INTEGER, action TEXT NOT NULL, " +
                    "reason TEXT NOT NULL DEFAULT 'OPEN', goalId INTEGER, answer TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT NOT NULL, " +
                    "why TEXT, position INTEGER NOT NULL, createdAt INTEGER NOT NULL, achievedAt INTEGER)"
            )
            db.execSQL("INSERT INTO goals (text, why, position, createdAt, achievedAt) VALUES ('Run a 5K', 'For my health', 0, 10, NULL)")
            db.execSQL(
                "INSERT INTO trigger_logs (packageName, openedAt, delayMs, engine, questionId, correct, action, reason, goalId, answer) " +
                    "VALUES ('com.instagram.android', 3000, 180, 'USAGE', 'n01', 1, 'CLOSE', 'OPEN', 1, 'NO')"
            )
            db.version = 3
        }

        // 2. Open it with this version of the app.
        val database = AppDatabase.create(context)
        val goals = background.submit<List<Goal>> { database.goalDao().getAll() }.get()
        val logs = background.submit<List<TriggerLog>> { database.triggerLogDao().getAll() }.get()

        // 3. The goal and the answer are unchanged; existing goals apply "always".
        val goal = goals.single()
        assertEquals("Run a 5K", goal.text)
        assertEquals("For my health", goal.why)
        assertEquals(Goal.SCHEDULE_ALWAYS, goal.schedule)
        val log = logs.single()
        assertEquals("NO", log.answer)
        assertEquals(1L, log.goalId)
        assertEquals("CLOSE", log.action)
        assertNull(log.downtimeMinutes)

        // 4. Steps work, in order, and the next step moves on when one is checked off.
        runBlocking {
            database.goalStepDao().replaceForGoal(
                goal.id,
                listOf("Buy running shoes", "Run 1 km without stopping", "Run 3 km").map {
                    GoalStep(goalId = goal.id, text = it, position = 0)
                },
            )
        }
        val steps = background.submit<List<GoalStep>> { database.goalStepDao().getAll() }.get()
        assertEquals(listOf(0, 1, 2), steps.map { it.position })
        assertEquals("Buy running shoes", nextStep(steps, goal.id)?.text)
        runBlocking { database.goalStepDao().update(steps.first().copy(doneAt = 99)) }
        val after = background.submit<List<GoalStep>> { database.goalStepDao().getAll() }.get()
        assertEquals("Run 1 km without stopping", nextStep(after, goal.id)?.text)
        database.close()
    }
}
