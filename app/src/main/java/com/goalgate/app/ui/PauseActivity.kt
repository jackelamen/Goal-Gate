package com.goalgate.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.GoalGateApp
import com.goalgate.app.R
import com.goalgate.app.data.wrapWithLanguage
import com.goalgate.app.data.Answer
import com.goalgate.app.data.Engine
import com.goalgate.app.data.Goal
import com.goalgate.app.data.ReflectionBank
import com.goalgate.app.data.ReflectionLine
import com.goalgate.app.data.Tone
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.data.BREAK_LENGTHS
import com.goalgate.app.data.DOWNTIME_LENGTHS
import com.goalgate.app.data.RestKind
import com.goalgate.app.data.breaksLeft
import com.goalgate.app.data.startOfDayMillis
import com.goalgate.app.data.ScheduleNow
import com.goalgate.app.data.goalsForNow
import com.goalgate.app.data.nextSteps
import com.goalgate.app.data.continueDelaySeconds
import com.goalgate.app.data.opensThisHour
import com.goalgate.app.data.pickLine
import com.goalgate.app.detection.GateController
import com.goalgate.app.ui.theme.GoalGateTheme
import com.goalgate.app.ui.theme.LocalAccent
import com.goalgate.app.ui.theme.goalColor
import kotlinx.coroutines.delay

/** The parts of the pause, in order. DOWNTIME replaces QUESTION and CHOICE when chosen. */
enum class PauseStep { GOALS, QUESTION, CHOICE, DOWNTIME }

/**
 * THE PAUSE: the full-screen check-in shown before a chosen app.
 *
 *   1. GOALS     the user's goals; they tap the one they are working on, or "None right now".
 *   2. QUESTION  that goal on its own card, the app's name as a label, and one
 *                fixed question: "Does opening [app] help this goal?" (Yes / Maybe / No).
 *   3. CHOICE    a short line that fits the answer, then Close or Continue.
 *
 * Or, on step 1, a rest, which comes in two kinds:
 *   "It's my downtime"  only inside the user's scheduled downtime (Settings),
 *                       15, 30 or 60 min.
 *   "Take a break"      at any other time, 10, 20 or 30 min, at most 3 a day;
 *                       when they are used up, none until the next day.
 *   DOWNTIME     how long, then Close or Continue with no wait. GoalGate then leaves every chosen app alone for that
 *                time, and shows one gentle check when it is up (reason
 *                DOWNTIME_END or BREAK_END).
 *
 * In scheduled downtime (Settings), "It's my downtime" is offered first and
 * breaks are not offered (downtime is already rest).
 * Goals marked "Workday" only appear during the workday. Each goal shows its
 * next step, read-only: steps are checked off in the Goals tab, never here.
 *
 * Nothing is ever blocked. "No" only changes the wording of step 3.
 *
 * This file has two parts:
 *  1. PauseActivity - loads the goals and saves the results (no layout code).
 *  2. PauseScreen   - draws everything (no database code).
 */
class PauseActivity : ComponentActivity() {

    private val app get() = application as GoalGateApp

    // What triggered this screen (read from the Intent).
    private var targetPackage = ""
    private var openedAt = 0L
    private var engine = Engine.USAGE
    private var reason = TriggerLog.REASON_OPEN
    private var minutesUsed by mutableIntStateOf(0)

    // Gentle escalation: opening the same app again and again within an hour
    // makes "Continue" wait a few seconds (see continueDelaySeconds).
    private var opensThisHour by mutableIntStateOf(0)
    private var continueDelay by mutableIntStateOf(0)

    // What the screen is showing. Compose redraws when these change.
    private var goals by mutableStateOf<List<Goal>?>(null) // null while loading
    private var step by mutableStateOf(PauseStep.GOALS)
    private var chosenGoal by mutableStateOf<Goal?>(null)
    private var answer by mutableStateOf<Answer?>(null)
    private var line by mutableStateOf<ReflectionLine?>(null)
    private var downtimeMinutes by mutableStateOf<Int?>(null)
    private var nextSteps by mutableStateOf<Map<Long, String>>(emptyMap())
    private var scheduledDowntime by mutableStateOf(false)
    private var restKind by mutableStateOf(RestKind.DOWNTIME)
    private var breaksLeft by mutableIntStateOf(0)
    private var tone = Tone.GENTLE
    private var koreanToo = false

    /** The database row for this pause. Only touched on app.dbThread. */
    private var log: TriggerLog? = null
    private var shownLogged = false

    // Restored after a rotation, applied once the goals have loaded.
    private var restoredGoalId: Long? = null
    private var restoredLineId: String? = null

    /** The language chosen in Settings applies to this screen (see wrapWithLanguage). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapWithLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tone = app.settings.tone
        koreanToo = app.settings.koreanExplanations
        readIntent(intent)
        savedInstanceState?.let { restore(it) }
        load()

        setContent {
            GoalGateTheme {
                val label = appLabel(targetPackage)
                PauseScreen(
                    // Why this screen appeared, shown small at the top.
                    header = when {
                        reason == TriggerLog.REASON_DOWNTIME_END -> stringResource(R.string.downtime_end_header, label)
                        reason == TriggerLog.REASON_BREAK_END -> stringResource(R.string.break_end_header, label)
                        minutesUsed > 0 -> stringResource(R.string.question_header_time, minutesUsed, label)
                        opensThisHour >= 3 -> stringResource(R.string.question_header_repeat, label, opensThisHour)
                        else -> stringResource(R.string.question_header, label)
                    },
                    appLabel = label,
                    goals = goals,
                    step = step,
                    chosenGoal = chosenGoal,
                    answer = answer,
                    line = line,
                    tone = tone,
                    koreanToo = koreanToo,
                    continueDelaySeconds = continueDelay,
                    onShown = ::onPauseShown,
                    onGoal = ::onGoalPicked,
                    onAnswer = ::onAnswer,
                    onClose = ::onClose,
                    onContinue = ::onContinue,
                    nextSteps = nextSteps,
                    scheduledDowntime = scheduledDowntime,
                    breaksLeft = breaksLeft,
                    restKind = restKind,
                    downtimeMinutes = downtimeMinutes,
                    downtimeEndMinutes = if (reason == TriggerLog.REASON_DOWNTIME_END || reason == TriggerLog.REASON_BREAK_END) minutesUsed else null,
                    onDowntime = { onRestChosen(RestKind.DOWNTIME) },
                    onBreak = { onRestChosen(RestKind.BREAK) },
                    onDowntimeLength = ::onDowntimeLength,
                )
            }
        }
    }

    /** Called if a new trigger arrives while this screen already exists: start over. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        goals = null
        step = PauseStep.GOALS
        chosenGoal = null
        answer = null
        line = null
        downtimeMinutes = null
        shownLogged = false
        app.dbThread.execute { log = null }
        load()
    }

    /** Keeps the user's place if the phone is rotated. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_STEP, step.name)
        chosenGoal?.let { outState.putLong(STATE_GOAL, it.id) }
        answer?.let { outState.putString(STATE_ANSWER, it.name) }
        line?.let { outState.putString(STATE_LINE, it.id) }
        downtimeMinutes?.let { outState.putInt(STATE_DOWNTIME, it) }
        outState.putString(STATE_REST, restKind.name)
    }

    private fun restore(state: Bundle) {
        step = PauseStep.entries.firstOrNull { it.name == state.getString(STATE_STEP) } ?: PauseStep.GOALS
        restoredGoalId = if (state.containsKey(STATE_GOAL)) state.getLong(STATE_GOAL) else null
        answer = Answer.entries.firstOrNull { it.name == state.getString(STATE_ANSWER) }
        restoredLineId = state.getString(STATE_LINE)
        downtimeMinutes = if (state.containsKey(STATE_DOWNTIME)) state.getInt(STATE_DOWNTIME) else null
        restKind = RestKind.entries.firstOrNull { it.name == state.getString(STATE_REST) } ?: RestKind.DOWNTIME
    }

    private fun readIntent(intent: Intent) {
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        openedAt = intent.getLongExtra(EXTRA_OPENED_AT, System.currentTimeMillis())
        engine = Engine.valueOf(intent.getStringExtra(EXTRA_ENGINE) ?: Engine.USAGE.name)
        reason = intent.getStringExtra(EXTRA_REASON) ?: TriggerLog.REASON_OPEN
        minutesUsed = intent.getIntExtra(EXTRA_MINUTES, 0)
        // The check at the end of a downtime has nothing to answer: Close and Continue show at once.
        if (reason == TriggerLog.REASON_DOWNTIME_END || reason == TriggerLog.REASON_BREAK_END) {
            step = PauseStep.CHOICE
            restKind = if (reason == TriggerLog.REASON_BREAK_END) RestKind.BREAK else RestKind.DOWNTIME
        }
    }

    /** Reads the goals, their next steps, today's schedule and the escalation count on the background thread. */
    private fun load() {
        app.dbThread.execute {
            val schedule = ScheduleNow.of(this, app.settings)
            // Only the goals that apply now: "Workday" goals stay out of the evening.
            val active = goalsForNow(app.database.goalDao().getAll(), schedule.workTime)
            val steps = nextSteps(app.database.goalStepDao().getAll()).mapValues { it.value.text }
            // Breaks started since midnight; when all are used, none until tomorrow.
            val breaksUsed = app.database.triggerLogDao().breakTimesSince(startOfDayMillis(System.currentTimeMillis())).size
            ReflectionBank.load(this) // warm the cache, so answering is instant

            // Escalation, only for "app opened" triggers (see opensThisHour).
            val opens = if (reason == TriggerLog.REASON_OPEN) {
                opensThisHour(
                    app.database.triggerLogDao().openTimesSince(targetPackage, openedAt - 60 * 60_000L),
                    openedAt,
                )
            } else {
                0
            }
            val restoredGoal = restoredGoalId?.let { id -> active.firstOrNull { it.id == id } }
            val restoredLine = restoredLineId?.let { id -> ReflectionBank.load(this).firstOrNull { it.id == id } }
            runOnUiThread {
                goals = active
                nextSteps = steps
                scheduledDowntime = schedule.downtime
                breaksLeft = breaksLeft(breaksUsed)
                opensThisHour = opens
                continueDelay = continueDelaySeconds(opens)
                chosenGoal = restoredGoal
                line = restoredLine
                restoredGoalId = null
                restoredLineId = null
                // Step 1 always shows, even with no goals for now: "It's my downtime" lives there.
            }
        }
    }

    /**
     * Called once, right after the pause's first frame is drawn.
     * This is the moment we measure the delay and create the log row.
     */
    private fun onPauseShown() {
        if (shownLogged) return
        shownLogged = true
        val delayMs = (System.currentTimeMillis() - openedAt).coerceAtLeast(0)
        app.dbThread.execute {
            // Rotated or rebuilt screen: keep using the row already written for this open.
            app.database.triggerLogDao().findTrigger(targetPackage, openedAt, reason)?.let {
                log = it
                return@execute
            }
            val row = TriggerLog(
                packageName = targetPackage,
                openedAt = openedAt,
                delayMs = delayMs,
                engine = engine.name,
                questionId = "",
                reason = reason,
            )
            log = row.copy(id = app.database.triggerLogDao().insert(row))
        }
    }

    /** Step 1: a goal was tapped, or "None right now" (null). */
    private fun onGoalPicked(goal: Goal?) {
        if (step != PauseStep.GOALS) return
        chosenGoal = goal
        step = PauseStep.QUESTION
    }

    /** Step 1: "It's my downtime" or "Take a break". Next, the user chooses how long. */
    private fun onRestChosen(kind: RestKind) {
        if (step != PauseStep.GOALS) return
        // Downtime only inside the scheduled hours; a break only while one is left today.
        if (kind == RestKind.BREAK && breaksLeft <= 0) return
        if (kind == RestKind.DOWNTIME && !scheduledDowntime) return
        restKind = kind
        chosenGoal = null
        step = PauseStep.DOWNTIME
    }

    /** The downtime length was chosen (it can be changed until Close or Continue). */
    private fun onDowntimeLength(minutes: Int) {
        if (step != PauseStep.DOWNTIME) return
        downtimeMinutes = minutes
        app.dbThread.execute {
            log?.let {
                val answer = if (restKind == RestKind.BREAK) TriggerLog.ANSWER_BREAK else TriggerLog.ANSWER_DOWNTIME
                log = it.copy(correct = true, answer = answer, downtimeMinutes = minutes, goalId = null)
                app.database.triggerLogDao().update(log!!)
            }
        }
    }

    /** Step 2: the question was answered. Pick a line for that answer and save it all. */
    private fun onAnswer(choice: Answer) {
        if (step != PauseStep.QUESTION) return
        answer = choice
        step = PauseStep.CHOICE
        val goalId = chosenGoal?.id
        app.dbThread.execute {
            val logDao = app.database.triggerLogDao()
            val picked = pickLine(ReflectionBank.load(this), choice, logDao.recentLineIds(8))
            runOnUiThread { line = picked }
            log?.let {
                log = it.copy(correct = true, answer = choice.name, goalId = goalId, questionId = picked?.id ?: "")
                logDao.update(log!!)
            }
            // "Today's goal" on the widget may have changed.
            GoalGateWidget.refresh(this)
        }
    }

    /** "Continue to [app]": start the cooldown (or the downtime) and step out of the way. */
    private fun onContinue() {
        val downtime = downtimeMinutes
        if (step == PauseStep.DOWNTIME && downtime != null) {
            GateController.startDowntime(targetPackage, downtime, isBreak = restKind == RestKind.BREAK)
        } else {
            GateController.startCooldown(targetPackage, app.settings.cooldownMinutes)
        }
        saveAction(TriggerLog.ACTION_CONTINUE)
        finish() // the chosen app is right underneath this screen
    }

    /** "Close": send the user to the home screen instead of the app. */
    private fun onClose() {
        // While the home screen appears, the app underneath can flicker into view for a
        // split second; tell the controller so that flicker does not bring this pause back.
        if (targetPackage.isNotEmpty()) GateController.noteClosed(targetPackage)
        saveAction(TriggerLog.ACTION_CLOSE)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun saveAction(action: String) {
        app.dbThread.execute {
            log?.let {
                log = it.copy(action = action)
                app.database.triggerLogDao().update(log!!)
            }
        }
    }

    /**
     * If the user leaves another way (Home button, recent apps), close this
     * screen so a stale pause is not waiting later. We keep it when the
     * screen simply turned off, so the pause is still there after unlocking.
     */
    override fun onStop() {
        super.onStop()
        val screenIsOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!isFinishing && !isChangingConfigurations && screenIsOn) finish()
    }

    /** "com.instagram.android" -> "Instagram". */
    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_OPENED_AT = "openedAt"
        private const val EXTRA_ENGINE = "engine"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_MINUTES = "minutesUsed"

        private const val STATE_STEP = "step"
        private const val STATE_GOAL = "goal"
        private const val STATE_ANSWER = "answer"
        private const val STATE_LINE = "line"
        private const val STATE_DOWNTIME = "downtime"
        private const val STATE_REST = "rest"

        /** Builds the Intent the detection engines use to open this screen. */
        fun createIntent(
            context: Context,
            packageName: String,
            openedAt: Long,
            engine: Engine,
            reason: String = TriggerLog.REASON_OPEN,
            minutesUsed: Int = 0,
        ): Intent =
            Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_OPENED_AT, openedAt)
                .putExtra(EXTRA_ENGINE, engine.name)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_MINUTES, minutesUsed)
                // NEW_TASK is required when starting a screen from a service.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

// ---------------------------------------------------------------------------
// The layout
// ---------------------------------------------------------------------------

/**
 * The whole pause. Internal (not private) so the screenshot tests can draw it.
 */
@Composable
internal fun PauseScreen(
    header: String,
    appLabel: String,
    goals: List<Goal>?,
    step: PauseStep,
    chosenGoal: Goal?,
    answer: Answer?,
    line: ReflectionLine?,
    tone: Tone,
    koreanToo: Boolean,
    continueDelaySeconds: Int,
    onShown: () -> Unit,
    onGoal: (Goal?) -> Unit,
    onAnswer: (Answer) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    nextSteps: Map<Long, String> = emptyMap(),
    scheduledDowntime: Boolean = false,
    breaksLeft: Int = 0,
    restKind: RestKind = RestKind.DOWNTIME,
    downtimeMinutes: Int? = null,
    downtimeEndMinutes: Int? = null,
    onBreak: () -> Unit = {},
    onDowntime: () -> Unit = {},
    onDowntimeLength: (Int) -> Unit = {},
) {
    val view = LocalView.current
    val downtimeEnd = downtimeEndMinutes != null
    // "Settled": an answer was given, or a downtime length was chosen.
    val settled = answer != null || (step == PauseStep.DOWNTIME && downtimeMinutes != null)
    // Close and Continue are ready once settled; the downtime check has them at once.
    val answered = settled || downtimeEnd
    // Downtime is rest the user chose: no escalation wait before Continue.
    val waitSeconds = if (step == PauseStep.DOWNTIME || downtimeEnd) 0 else continueDelaySeconds
    var burstOrigin by remember { mutableStateOf(Offset.Zero) }

    // The phone's Back button behaves like "Close".
    BackHandler(onBack = onClose)

    // Tell the Activity once the pause has really been drawn (for the delay log).
    LaunchedEffect(goals != null) {
        if (goals != null) {
            withFrameNanos { }
            onShown()
        }
    }

    // One soft tick when the answer lands. The same for every answer:
    // GoalGate does not judge, so No does not buzz differently.
    LaunchedEffect(settled) {
        if (settled) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            )
        }
    }

    HeroGround(Modifier.fillMaxSize()) {
        val softWhite = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)

        // A slow ring of light from the chosen answer: the moment it is settled.
        SettleRing(play = settled, origin = burstOrigin)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .readableWidth(maxWidth = if (isLandscapeWide()) 960.dp else 560.dp)
                .padding(horizontal = 28.dp),
        ) {
            // Top line: the summit mark and why this screen appeared. The line
            // has the whole width to itself and may wrap, so long app names fit.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = if (isLandscapeWide()) 44.dp else 64.dp)) {
                SummitMark(breathing = !answered, celebrate = settled, lineColor = softWhite)
                Spacer(Modifier.width(10.dp))
                SectionLabel(header, Modifier.weight(1f), color = softWhite, maxLines = 2)
            }

            // After answering, scroll the response into view on short screens.
            val scroll = rememberScrollState()
            LaunchedEffect(settled) {
                if (settled) {
                    delay(250)
                    scroll.animateScrollTo(scroll.maxValue, tween(400))
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
                verticalArrangement = Arrangement.Center,
            ) {
                if (downtimeEndMinutes != null) {
                    DowntimeEndPart(downtimeEndMinutes, restKind)
                } else if (goals != null) {
                    // 0 = goals, 1 = question and choice, 2 = downtime.
                    val part = when (step) {
                        PauseStep.GOALS -> 0
                        PauseStep.QUESTION, PauseStep.CHOICE -> 1
                        PauseStep.DOWNTIME -> 2
                    }
                    AnimatedContent(
                        targetState = part,
                        transitionSpec = { fadeIn(tween(280, delayMillis = 60)) togetherWith fadeOut(tween(140)) },
                        label = "pauseStep",
                    ) { shown ->
                        when (shown) {
                            0 -> GoalsPart(
                                goals = goals,
                                nextSteps = nextSteps,
                                scheduledDowntime = scheduledDowntime,
                                breaksLeft = breaksLeft,
                                onGoal = onGoal,
                                onDowntime = onDowntime,
                                onBreak = onBreak,
                            )
                            1 -> QuestionPart(
                                appLabel = appLabel,
                                goal = chosenGoal,
                                nextStep = chosenGoal?.let { nextSteps[it.id] },
                                answer = answer,
                                line = line,
                                tone = tone,
                                koreanToo = koreanToo,
                                onAnswer = onAnswer,
                                onChosenPosition = { burstOrigin = it },
                            )
                            else -> DowntimePart(
                                kind = restKind,
                                breaksLeft = breaksLeft,
                                minutes = downtimeMinutes,
                                onLength = onDowntimeLength,
                                onChosenPosition = { burstOrigin = it },
                            )
                        }
                    }
                }
            }

            // Bottom: the two ways out. They are always laid out (so nothing
            // jumps when they appear) but stay invisible and untappable until
            // the question is answered. Until then a quiet note sits in their place.
            val buttonsShown by animateFloatAsState(
                targetValue = if (answered) 1f else 0f,
                animationSpec = tween(320, delayMillis = if (answered) 420 else 0),
                label = "buttons",
            )
            val hintShown by animateFloatAsState(
                targetValue = if (answered || goals == null) 0f else 1f,
                animationSpec = tween(if (answered) 120 else 400, delayMillis = if (answered) 0 else 900),
                label = "hint",
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                Text(
                    stringResource(
                        when (step) {
                            PauseStep.GOALS -> R.string.pause_hint_goals
                            PauseStep.DOWNTIME -> R.string.pause_hint_downtime
                            else -> R.string.pause_hint_question
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = softWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = hintShown },
                )
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = buttonsShown
                            translationY = (1f - buttonsShown) * 16.dp.toPx()
                        }
                        .then(if (answered) Modifier else Modifier.clearAndSetSemantics { }),
                ) {
                    // The countdown starts when the user answers. Until it reaches
                    // zero, Continue shows the seconds left and does nothing.
                    var waitLeft by remember { mutableIntStateOf(waitSeconds) }
                    LaunchedEffect(answered, waitSeconds) {
                        if (answered) {
                            waitLeft = waitSeconds
                            while (waitLeft > 0) {
                                delay(1000)
                                waitLeft--
                            }
                        }
                    }
                    val continueLabel = when {
                        waitLeft > 0 -> stringResource(R.string.question_continue_wait, appLabel, waitLeft)
                        downtimeEnd -> stringResource(R.string.downtime_end_keep_going, appLabel)
                        else -> stringResource(R.string.question_continue, appLabel)
                    }
                    // "Close" is the solid orange button on purpose: it is the choice
                    // the user installed GoalGate to make more often.
                    if (isLandscapeWide()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SignalButton(stringResource(R.string.question_close), onClose, enabled = answered, modifier = Modifier.weight(1f))
                            QuietButton(continueLabel, onContinue, enabled = answered && waitLeft == 0, modifier = Modifier.weight(1f))
                        }
                    } else {
                        SignalButton(stringResource(R.string.question_close), onClose, enabled = answered)
                        QuietButton(continueLabel, onContinue, enabled = answered && waitLeft == 0)
                    }
                }
            }
        }
    }
}

/** Step 1: "What are you working on right now?" and the goals as cards. */
@Composable
private fun GoalsPart(
    goals: List<Goal>,
    nextSteps: Map<Long, String>,
    scheduledDowntime: Boolean,
    breaksLeft: Int,
    onGoal: (Goal?) -> Unit,
    onDowntime: () -> Unit,
    onBreak: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val compact = isCompact()

    val title: @Composable () -> Unit = {
        Text(
            stringResource(R.string.pause_goals_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().enterAnimation(entered, 0),
        )
    }
    val list: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            // In scheduled downtime (Settings), rest is offered first, with a small label saying why.
            if (scheduledDowntime) {
                SectionLabel(
                    stringResource(R.string.pause_downtime_hours),
                    color = LocalAccent.current.accent,
                    modifier = Modifier.enterAnimation(entered, 1),
                )
                QuietRow(stringResource(R.string.pause_downtime), onClick = onDowntime, modifier = Modifier.enterAnimation(entered, 1))
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            }
            goals.forEachIndexed { index, goal ->
                GoalChoiceCard(
                    goal, nextSteps[goal.id], compact,
                    onClick = { onGoal(goal) },
                    modifier = Modifier.enterAnimation(entered, 2 + index),
                )
            }
            // "None right now" and the break: quiet outlined rows, not cards, so the goals lead.
            QuietRow(stringResource(R.string.pause_no_goal), onClick = { onGoal(null) }, modifier = Modifier.enterAnimation(entered, 2 + goals.size))
            // Outside downtime hours: a break, while any are left today. When they are used up the
            // row stays, dimmed, and says when they come back, so nobody wonders where it went.
            if (!scheduledDowntime) {
                QuietRow(
                    if (breaksLeft > 0) stringResource(R.string.pause_break, breaksLeft) else stringResource(R.string.pause_break_none),
                    onClick = onBreak,
                    enabled = breaksLeft > 0,
                    modifier = Modifier.enterAnimation(entered, 3 + goals.size),
                )
            }
        }
    }
    if (isLandscapeWide()) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f).padding(top = 12.dp)) { title() }
            Column(Modifier.weight(1f)) { list() }
        }
    } else {
        Column {
            title()
            Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
            list()
        }
    }
}

/**
 * One goal to tap on step 1: the user's words, large. Underneath: the goal's
 * next step if it has one (read-only), otherwise its "why".
 */
@Composable
private fun GoalChoiceCard(goal: Goal, nextStep: String?, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f)),
    ) {
        // The stripe on the left is the goal's own colour.
        Column(Modifier.fillMaxWidth().goalStripe(goalColor(goal.id)).heightIn(min = if (compact) 50.dp else 60.dp).padding(start = 24.dp, end = 20.dp, top = if (compact) 10.dp else 14.dp, bottom = if (compact) 10.dp else 14.dp)) {
            FitBlock(goal.text, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface, maxLines = 3)
            val why = goal.why
            if (nextStep != null) {
                Text(
                    stringResource(R.string.pause_next_step, userText(nextStep)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // One line on short screens, so "None right now" and downtime stay in view.
                    maxLines = if (compact || isShort()) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (!why.isNullOrBlank() && !compact) {
                Text(
                    userText(why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun QuietRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.22f else 0.1f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/**
 * Steps 2 and 3: the chosen goal on its own card, the app name as a label,
 * the fixed question and its three answers. After answering, the other two
 * answers fade back and a short line that fits the answer appears below.
 */
@Composable
private fun QuestionPart(
    appLabel: String,
    goal: Goal?,
    nextStep: String?,
    answer: Answer?,
    line: ReflectionLine?,
    tone: Tone,
    koreanToo: Boolean,
    onAnswer: (Answer) -> Unit,
    onChosenPosition: (Offset) -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val compact = isCompact()

    val questionBlock: @Composable () -> Unit = {
        // The goal, exactly as the user wrote it. It is never put inside the
        // question, so any wording works (a phrase, a sentence, Korean...).
        if (goal != null) {
            Column(Modifier.fillMaxWidth().enterAnimation(entered, 0)) {
                // The label carries the goal's own colour, the same as its card.
                SectionLabel(stringResource(R.string.pause_your_goal), color = goalColor(goal.id))
                Spacer(Modifier.height(8.dp))
                FitBlock(
                    goal.text,
                    // Short screens (small phones, large text): a smaller size, so the
                    // answers and the response still fit above the buttons.
                    if (isShort()) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    minFontSize = 18.sp,
                )
                // The goal's next step, read-only. It is checked off in the Goals tab, not here.
                if (nextStep != null) {
                    Text(
                        stringResource(R.string.pause_next_step, userText(nextStep)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        // A hairline between the user's words and the app's question (only when there is a goal above it).
        if (goal != null) {
            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = 0.18f }.enterAnimation(entered, 1)) {
                Canvas(Modifier.fillMaxSize()) { drawRect(Color.White) }
            }
            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
        }
        // The app's name is only ever a label, so it never needs grammar.
        SectionLabel(appLabel, color = LocalAccent.current.accent, modifier = Modifier.enterAnimation(entered, 1))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (goal != null) R.string.pause_question else R.string.pause_question_no_goal, appLabel),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().enterAnimation(entered, 2),
        )
    }
    val answersBlock: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            Answer.entries.forEachIndexed { index, option ->
                AnswerCard(
                    option = option,
                    noGoal = goal == null,
                    compact = compact,
                    chosen = answer == option,
                    dimmed = answer != null && answer != option,
                    enabled = answer == null,
                    onClick = { onAnswer(option) },
                    modifier = Modifier
                        .enterAnimation(entered, 3 + index)
                        .onGloballyPositioned { if (answer == option) onChosenPosition(it.boundsInRoot().center) },
                )
            }
        }
    }
    val responseBlock: @Composable () -> Unit = {
        // The space for the response is reserved from the start (min height),
        // so nothing moves when it fades in.
        val shown by animateFloatAsState(
            targetValue = if (answer != null) 1f else 0f,
            animationSpec = tween(360, delayMillis = if (answer != null) 200 else 0),
            label = "response",
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 56.dp else 104.dp)
                .padding(top = 20.dp)
                .graphicsLayer { alpha = shown; translationY = (1f - shown) * 8.dp.toPx() }
                .then(if (answer != null) Modifier else Modifier.clearAndSetSemantics { }),
        ) {
            if (answer != null) {
                Text(
                    stringResource(responseFor(answer, tone)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (line != null) {
                    val korean = LocalConfiguration.current.locales[0].language == "ko"
                    Text(
                        if (korean) keepKoreanWordsTogether(line.ko) else line.en,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (!korean && koreanToo) {
                        Text(
                            keepKoreanWordsTogether(line.ko),
                            style = koreanStyle(),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (isLandscapeWide()) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f)) {
                questionBlock()
                responseBlock()
            }
            Column(Modifier.weight(1f).padding(top = 8.dp)) { answersBlock() }
        }
    } else {
        Column {
            questionBlock()
            Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
            answersBlock()
            responseBlock()
        }
    }
}

/**
 * "It's my downtime": how long? Choosing a length settles it (the same calm
 * ring as an answer), and a sentence says what GoalGate will do.
 */
@Composable
private fun DowntimePart(kind: RestKind, breaksLeft: Int, minutes: Int?, onLength: (Int) -> Unit, onChosenPosition: (Offset) -> Unit) {
    val isBreak = kind == RestKind.BREAK
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    Column {
        SectionLabel(
            stringResource(if (isBreak) R.string.pause_break_label else R.string.pause_downtime),
            color = LocalAccent.current.accent,
            modifier = Modifier.enterAnimation(entered, 0),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (isBreak) R.string.break_how_long else R.string.downtime_how_long),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().enterAnimation(entered, 1),
        )
        Spacer(Modifier.height(24.dp))
        ChoiceRow<Int?>(
            options = if (isBreak) BREAK_LENGTHS else DOWNTIME_LENGTHS,
            selected = minutes,
            label = { stringResource(R.string.cooldown_minutes, it ?: 0) },
            onSelect = { chosen -> chosen?.let(onLength) },
            modifier = Modifier
                .enterAnimation(entered, 2)
                .onGloballyPositioned { if (minutes != null) onChosenPosition(it.boundsInRoot().center) },
        )
        val shown by animateFloatAsState(if (minutes != null) 1f else 0f, tween(360, delayMillis = 150), label = "downtimeNote")
        Text(
            when {
                minutes == null -> ""
                // A break says how many are left after this one, so the limit is never a surprise.
                isBreak -> stringResource(R.string.break_note, minutes, breaksLeft - 1)
                else -> stringResource(R.string.downtime_note, minutes)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.heightIn(min = 72.dp).padding(top = 20.dp).graphicsLayer { alpha = shown },
        )
    }
}

/** The gentle check when a chosen downtime is up and the user is still in the app. */
@Composable
private fun DowntimeEndPart(minutes: Int, kind: RestKind) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    Column {
        Text(
            pluralStringResource(if (kind == RestKind.BREAK) R.plurals.break_end_title else R.plurals.downtime_end_title, minutes, minutes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().enterAnimation(entered, 0),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.downtime_end_question),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            modifier = Modifier.enterAnimation(entered, 1),
        )
    }
}

/** The sentence after answering, by answer and by the tone set in Settings. */
private fun responseFor(answer: Answer, tone: Tone): Int = when (answer) {
    Answer.YES -> if (tone == Tone.GENTLE) R.string.response_yes_gentle else R.string.response_yes_direct
    Answer.MAYBE -> if (tone == Tone.GENTLE) R.string.response_maybe_gentle else R.string.response_maybe_direct
    Answer.NO -> if (tone == Tone.GENTLE) R.string.response_no_gentle else R.string.response_no_direct
}

/**
 * One of the three answers: a white card. Once answered, the chosen one
 * settles into the ground (see-through, white text, a fine orange edge) and
 * the other two fade back, so the eye moves on to the response and to Close
 * instead of resting on what was already decided. All three answers look the
 * same before and after: none of them is "wrong".
 */
@Composable
private fun AnswerCard(
    option: Answer,
    noGoal: Boolean,
    compact: Boolean,
    chosen: Boolean,
    dimmed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val alpha by animateFloatAsState(if (dimmed) 0.14f else 1f, tween(260), label = "answerAlpha")
    val elevation by animateDpAsState(if (chosen || dimmed) 0.dp else 8.dp, label = "answerElevation")
    val container by animateColorAsState(
        if (chosen) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface, tween(360), label = "answerFill",
    )
    val content by animateColorAsState(
        if (chosen) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface, tween(360), label = "answerText",
    )
    val edge by animateFloatAsState(if (chosen) 1f else 0f, tween(300), label = "answerEdge")
    // With no goal, the question asks what the app is for, so the answers say that.
    val (main, example) = when (option) {
        Answer.YES -> if (noGoal) R.string.answer_yes_nogoal to R.string.answer_yes_nogoal_example else R.string.answer_yes to R.string.answer_yes_example
        Answer.MAYBE -> if (noGoal) R.string.answer_maybe_nogoal to null else R.string.answer_maybe to null
        Answer.NO -> if (noGoal) R.string.answer_no_nogoal to null else R.string.answer_no to null
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        interactionSource = interaction,
        border = if (edge > 0f) BorderStroke(2.dp, accent.accent.copy(alpha = edge)) else null,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .graphicsLayer { this.alpha = alpha }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            ),
    ) {
        Column(Modifier.heightIn(min = if (compact) 48.dp else 58.dp).padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 14.dp)) {
            Text(stringResource(main), style = MaterialTheme.typography.titleMedium, color = content)
            if (example != null && !compact) {
                Text(
                    stringResource(example),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A slow, faint ring of orange light that spreads once from [origin] when [play] turns true. */
@Composable
private fun SettleRing(play: Boolean, origin: Offset) {
    val accent = LocalAccent.current.accent
    val progress = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (play) progress.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) else progress.snapTo(0f)
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val radius = size.height * 1.1f * t
        val fade = 1f - t
        drawCircle(accent, radius = radius, center = origin, alpha = 0.08f * fade)
        drawCircle(accent, radius = radius, center = origin, alpha = 0.35f * fade, style = Stroke(1.5.dp.toPx()))
    }
}

/** True on a phone whose usable height is small for this layout (small phones, large text). */
@Composable
private fun isShort(): Boolean {
    val c = LocalConfiguration.current
    return c.screenHeightDp / c.fontScale < 600
}

/** True on a short screen, such as a phone turned sideways. */
@Composable
private fun isCompact(): Boolean = LocalConfiguration.current.screenHeightDp < 560

/** True on a wide, short screen, such as a phone turned sideways. */
@Composable
private fun isLandscapeWide(): Boolean {
    val c = LocalConfiguration.current
    return c.screenHeightDp < 560 && c.screenWidthDp > c.screenHeightDp && c.screenWidthDp >= 600
}

/** Fades an item in and slides it up a little. Items with a higher [index] start later. */
@Composable
private fun Modifier.enterAnimation(entered: Boolean, index: Int): Modifier {
    val amount by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = 60 + index * 65),
        label = "enter",
    )
    return this.graphicsLayer {
        alpha = amount
        translationY = (1f - amount) * 18.dp.toPx()
    }
}
