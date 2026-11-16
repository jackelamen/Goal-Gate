package com.goalgate.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * DAO = "data access object". Each interface lists the database operations
 * for one table. Room writes the real code for us at build time.
 *
 * Functions that return a Flow keep sending new results whenever the table changes,
 * so the screens update by themselves.
 *
 * "suspend" functions are called from coroutines. The plain (non-suspend) ones
 * block until the database is done, so they must only be called from a
 * background thread. The pause screen uses GoalGateApp.dbThread for that.
 */

@Dao
interface SelectedAppDao {
    @Query("SELECT * FROM selected_apps")
    fun observeAll(): Flow<List<SelectedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: SelectedApp)

    @Delete
    suspend fun delete(app: SelectedApp)
}

@Dao
interface GoalDao {
    /** Every goal, achieved ones included. Updates by itself. */
    @Query("SELECT * FROM goals ORDER BY position, id")
    fun observeAll(): Flow<List<Goal>>

    /** Every goal, for background work (the pause screen, the widget). */
    @Query("SELECT * FROM goals ORDER BY position, id")
    fun getAll(): List<Goal>

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goals: List<Goal>)

    @Delete
    suspend fun delete(goal: Goal)
}

@Dao
interface TriggerLogDao {
    /** Newest first. */
    @Query("SELECT * FROM trigger_logs ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<TriggerLog>>

    /** Returns the id of the new row, so we can update it later. */
    @Insert
    fun insert(log: TriggerLog): Long

    @Update
    fun update(log: TriggerLog)

    /** Every row, for background work such as the widget. */
    @Query("SELECT * FROM trigger_logs")
    fun getAll(): List<TriggerLog>

    /**
     * When [packageName] was opened (not time checks) since [since], one entry
     * per row. See [com.goalgate.app.data.opensThisHour] for how these are counted.
     */
    @Query("SELECT openedAt FROM trigger_logs WHERE packageName = :packageName AND reason = 'OPEN' AND openedAt >= :since")
    fun openTimesSince(packageName: String, since: Long): List<Long>

    /**
     * The row already written for this exact open, if any. The pause screen can be
     * created twice for one open (the phone is rotated, or Android rebuilds the
     * screen), and it must not log the same open twice.
     */
    @Query("SELECT * FROM trigger_logs WHERE packageName = :packageName AND openedAt = :openedAt AND reason = :reason LIMIT 1")
    fun findTrigger(packageName: String, openedAt: Long, reason: String): TriggerLog?

    /** The reflection lines shown most recently, newest first, so they are not repeated soon. */
    @Query("SELECT questionId FROM trigger_logs WHERE questionId != '' ORDER BY openedAt DESC LIMIT :limit")
    fun recentLineIds(limit: Int): List<String>

    @Query("DELETE FROM trigger_logs")
    suspend fun deleteAll()
}

/** The database itself: a list of tables and a way to reach each DAO. */
@Database(
    entities = [SelectedApp::class, TriggerLog::class, Goal::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedAppDao(): SelectedAppDao
    abstract fun triggerLogDao(): TriggerLogDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val FILE_NAME = "goalgate.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /**
         * Version 2 added the "reason" column to the trigger log.
         * A migration keeps the user's existing data when they update the app.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trigger_logs ADD COLUMN reason TEXT NOT NULL DEFAULT 'OPEN'")
            }
        }

        /**
         * Version 3 (GoalGate): the goals table, and two new log columns for the
         * goal picked and the answer given. Existing log rows keep everything they
         * had; the new columns start empty (null). The question-progress table from
         * WordGate is no longer used, so it is removed.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS goals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "why TEXT, " +
                        "position INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "achievedAt INTEGER)"
                )
                db.execSQL("ALTER TABLE trigger_logs ADD COLUMN goalId INTEGER")
                db.execSQL("ALTER TABLE trigger_logs ADD COLUMN answer TEXT")
                db.execSQL("DROP TABLE IF EXISTS question_progress")
            }
        }
    }
}
