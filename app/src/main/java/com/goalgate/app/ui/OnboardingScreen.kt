package com.goalgate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalgate.app.GoalGateApp
import com.goalgate.app.detection.Permissions
import com.goalgate.app.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import com.goalgate.app.data.Goal
import com.goalgate.app.data.activeGoals
import com.goalgate.app.data.canAddGoal
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.R

/** The screens of the first-run setup, in order. */
private enum class Step { WELCOME, OVERLAY, USAGE, NOTIFICATIONS, BATTERY, GOALS, APPS, READY }

/**
 * First-run setup. One thing per screen, so nobody needs other instructions:
 *
 *   WELCOME        what GoalGate does
 *   OVERLAY        "Display over other apps" (system settings, comes back by itself)
 *   USAGE          "Usage access"            (system settings, comes back by itself)
 *   NOTIFICATIONS  one tap on a system pop-up (Android 13+ only)
 *   BATTERY        one tap on a system pop-up
 *   GOALS          write at least one goal (up to 5), each with an optional "why"
 *   APPS           choose at least one app
 *   READY          what to do next
 *
 * OVERLAY comes before USAGE on purpose: once GoalGate may display over other
 * apps, Android also lets it bring itself back to the front, which is how the
 * USAGE step can return here automatically.
 * Steps that are already done are skipped. Back goes to the previous screen.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val steps = remember {
        Step.entries.filter { it != Step.NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    // Moves on from [from] to the following step. Several things can notice
    // that a step is finished (the automatic check, coming back to the app);
    // only the first one counts, so a step is never skipped by accident.
    val advanceFrom = { from: Step ->
        if (steps[index] == from && index < steps.lastIndex) index++
    }

    BackHandler(enabled = index > 0) { index-- }

    // Skip a permission step that is already done (for example after reinstalling).
    LaunchedEffect(step) {
        val done = when (step) {
            Step.OVERLAY -> Permissions.canDrawOverlays(context)
            Step.USAGE -> Permissions.hasUsageAccess(context)
            Step.NOTIFICATIONS -> Permissions.canPostNotifications(context)
            Step.BATTERY -> Permissions.isIgnoringBatteryOptimizations(context)
            else -> false
        }
        if (done) advanceFrom(step)
    }

    // "Step 2 of 5" counts only the steps that ask something of the user.
    val numbered = steps.filter { it != Step.WELCOME && it != Step.READY }
    val number = numbered.indexOf(step) + 1

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { direction * it / 6 }) togetherWith
                fadeOut(tween(120))
        },
        label = "onboardingStep",
    ) { current ->
        when (current) {
            Step.WELCOME -> WelcomeStep(onNext = { advanceFrom(Step.WELCOME) })
            Step.OVERLAY -> SettingsStep(
                number, numbered.size,
                title = stringResource(R.string.ob_overlay_title),
                body = stringResource(R.string.ob_overlay_body),
                intents = listOf(Permissions.overlayIntent(context)),
                isGranted = { Permissions.canDrawOverlays(context) },
                onGranted = { advanceFrom(Step.OVERLAY) },
            )
            Step.USAGE -> SettingsStep(
                number, numbered.size,
                title = stringResource(R.string.ob_usage_title),
                body = stringResource(R.string.ob_usage_body),
                // GoalGate's own switch where the phone supports it, otherwise the list.
                intents = listOf(Permissions.usageAccessIntent(context), Permissions.usageAccessIntent()),
                isGranted = { Permissions.hasUsageAccess(context) },
                onGranted = { advanceFrom(Step.USAGE) },
            )
            Step.NOTIFICATIONS -> NotificationStep(number, numbered.size, onDone = { advanceFrom(Step.NOTIFICATIONS) })
            Step.BATTERY -> BatteryStep(number, numbered.size, onDone = { advanceFrom(Step.BATTERY) })
            Step.GOALS -> GoalsStep(number, numbered.size, onDone = { advanceFrom(Step.GOALS) })
            Step.APPS -> AppsStep(number, numbered.size, onDone = { advanceFrom(Step.APPS) })
            Step.READY -> ReadyStep(onFinished)
        }
    }
}

/** The layout every setup step shares: progress at the top, text, one button at the bottom. */
@Composable
private fun StepLayout(
    number: Int,
    total: Int,
    title: String,
    body: String,
    how: String?,
    button: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .readableWidth()
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        StepProgress(number, total)
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (how != null) {
                Spacer(Modifier.height(24.dp))
                // What to do on the next screen, in a card so it stands out.
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().card().padding(18.dp),
                ) {
                    Box(Modifier.padding(top = 9.dp).size(7.dp).background(LocalAccent.current.accent, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(how, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        button()
    }
}

/** "Step 2 of 5" and a row of short bars, one per step. */
@Composable
private fun StepProgress(number: Int, total: Int) {
    val accent = LocalAccent.current.accent
    Column(Modifier.padding(top = 12.dp)) {
        SectionLabel(stringResource(R.string.ob_step, number, total))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 1..total) {
                val color by animateColorAsState(
                    if (i <= number) accent else MaterialTheme.colorScheme.outlineVariant,
                    label = "stepBar",
                )
                Box(Modifier.weight(1f).height(4.dp).background(color, CircleShape))
            }
        }
    }
}

/**
 * A step that needs the user to switch GoalGate on in a system settings page.
 * While they are there, GoalGate checks twice a second; as soon as the switch
 * is on, it brings itself back to the front and moves to the next step.
 */
@Composable
private fun SettingsStep(
    number: Int,
    total: Int,
    title: String,
    body: String,
    intents: List<Intent>,
    isGranted: () -> Boolean,
    onGranted: () -> Unit,
) {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf(false) }

    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        while (!isGranted()) delay(500)
        bringGoalGateBack(context)
        onGranted()
    }
    // Also check whenever the user comes back by pressing Back themselves.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (isGranted()) onGranted() }

    StepLayout(number, total, title, body, how = stringResource(R.string.ob_find_switch)) {
        PrimaryButton(stringResource(R.string.ob_open_settings), onClick = {
            waiting = true
            openSettings(context, *intents.toTypedArray())
        })
    }
}

/** Notifications: one tap on Android's own pop-up. The step continues either way. */
@Composable
private fun NotificationStep(number: Int, total: Int, onDone: () -> Unit) {
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    StepLayout(
        number, total,
        title = stringResource(R.string.ob_notifications_title),
        body = stringResource(R.string.ob_notifications_body),
        how = stringResource(R.string.ob_tap_allow),
    ) {
        PrimaryButton(stringResource(R.string.ob_continue), onClick = {
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
    }
}

/** Battery: one tap on Android's own pop-up. The step continues either way. */
@Composable
private fun BatteryStep(number: Int, total: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    // The pop-up covers GoalGate; when it closes, GoalGate resumes and the step ends.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (asked) onDone() }
    StepLayout(
        number, total,
        title = stringResource(R.string.ob_battery_title),
        body = stringResource(R.string.ob_battery_body),
        how = stringResource(R.string.ob_tap_allow),
    ) {
        PrimaryButton(stringResource(R.string.ob_continue), onClick = {
            asked = true
            openSettings(context, Permissions.batteryIntent(context))
        })
    }
}

/** Choose the apps. "Done" stays off until at least one app is switched on. */
@Composable
private fun AppsStep(number: Int, total: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as GoalGateApp).database.selectedAppDao()
    val selected by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.readableWidth().padding(horizontal = 28.dp)) { StepProgress(number, total) }
        Box(Modifier.weight(1f)) { AppPickerScreen() }
        Box(Modifier.readableWidth().padding(horizontal = 28.dp, vertical = 16.dp)) {
            PrimaryButton(
                if (selected.isEmpty()) stringResource(R.string.ob_apps_pick_one) else stringResource(R.string.ob_apps_done),
                onClick = onDone,
                enabled = selected.isNotEmpty(),
            )
        }
    }
}

/** The last screen: a short summary, and a note that goals can be edited any time. */
@Composable
private fun ReadyStep(onFinished: () -> Unit) {
    val context = LocalContext.current
    val database = (context.applicationContext as GoalGateApp).database
    val selected by database.selectedAppDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by database.goalDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val firstApp = selected.firstOrNull()?.packageName?.let { appLabel(context, it) } ?: ""

    HeroGround(Modifier.fillMaxSize()) {
        val softWhite = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .readableWidth()
                .padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                SummitMark(breathing = true, drawIn = true, lineColor = softWhite)
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.ob_ready_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(20.dp))
                // The summary: how many goals and apps, in one plain line each.
                SectionLabel(
                    pluralStringResource(R.plurals.ob_ready_goals, goals.size, goals.size),
                    color = softWhite,
                )
                Spacer(Modifier.height(6.dp))
                for (goal in activeGoals(goals)) {
                    Text(
                        userText(goal.text),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                if (firstApp.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ob_ready_body, firstApp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(stringResource(R.string.ob_ready_tip), style = MaterialTheme.typography.bodyLarge, color = softWhite)
            }
            PrimaryButton(stringResource(R.string.ob_finish), onClick = onFinished, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

/**
 * Write your goals: at least one, up to five, each with an optional "why".
 * The examples are only hints. Goals can be edited later in the Goals tab.
 */
@Composable
private fun GoalsStep(number: Int, total: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as GoalGateApp).database.goalDao()
    val all by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val goals = activeGoals(all)
    val scope = rememberCoroutineScope()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Goal?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .readableWidth()
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        StepProgress(number, total)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.ob_goals_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.ob_goals_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            if (goals.isEmpty()) {
                // Example goals: hints only, never required.
                SectionLabel(stringResource(R.string.ob_goals_examples_label))
                Spacer(Modifier.height(8.dp))
                for (example in listOf(R.string.goal_example_1, R.string.goal_example_2, R.string.goal_example_3)) {
                    Text(
                        stringResource(example),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            for (goal in goals) {
                Column(
                    Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .card()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { editing = goal }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    FitBlock(goal.text, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface)
                    goal.why?.let {
                        Text(userText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (goals.isNotEmpty() && canAddGoal(all)) {
                Spacer(Modifier.height(6.dp))
                QuietButton("+ " + stringResource(R.string.goals_add), onClick = { adding = true })
            }
        }
        Spacer(Modifier.height(16.dp))
        // With no goal yet, the main button opens the editor: one is required.
        PrimaryButton(
            stringResource(if (goals.isEmpty()) R.string.ob_goals_add_first else R.string.ob_apps_done),
            onClick = { if (goals.isEmpty()) adding = true else onDone() },
        )
    }

    if (adding) {
        GoalEditDialog(
            initial = null,
            onSave = { text, why -> scope.addGoal(dao, all, text, why); adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { goal ->
        GoalEditDialog(
            initial = goal,
            onSave = { text, why -> scope.launch { dao.update(listOf(goal.copy(text = text, why = why))) }; editing = null },
            onDelete = { scope.launch { dao.delete(goal) }; editing = null },
            onDismiss = { editing = null },
        )
    }
}

/**
 * Opens the first of [intents] that this phone supports. If none works,
 * opens the main Settings app, so the button never does nothing.
 */
private fun openSettings(context: Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // This phone does not have that page; try the next one.
        }
    }
    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
}

/**
 * Brings GoalGate back in front of the system settings page, closing that
 * page. Android allows this once "display over other apps" is on.
 */
private fun bringGoalGateBack(context: Context) {
    try {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    } catch (e: Exception) {
        // Not allowed on this phone: the user presses Back, and ON_RESUME continues.
    }
}

/** "com.instagram.android" -> "Instagram". */
private fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (e: Exception) {
    ""
}

/**
 * Step 0: the first thing a new user sees. One sentence on the pine ground,
 * with the summit mark drawing itself in above it.
 */
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    HeroGround(Modifier.fillMaxSize(), peak = Offset(0.8f, 0.22f)) {
        val softWhite = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .readableWidth()
                .padding(horizontal = 28.dp),
        ) {
            // The name of the app, small, at the top.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(64.dp)) {
                SummitMark(breathing = true, drawIn = true, lineColor = softWhite)
                Spacer(Modifier.width(10.dp))
                SectionLabel(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.onBackground)
            }

            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                // The headline is a little smaller on narrow screens (Z Fold cover, small phones).
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val narrow = maxWidth < 360.dp
                    val korean = LocalConfiguration.current.locales[0].language == "ko"
                    Text(
                        stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = if (narrow) 38.sp else if (korean) 40.sp else 48.sp,
                            lineHeight = if (narrow) 44.sp else if (korean) 52.sp else 54.sp,
                            letterSpacing = if (korean) 0.sp else (-0.8).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(28.dp))
                // Three short paragraphs: what happens, what you decide, where the data lives.
                for (text in listOf(R.string.onboarding_body_1, R.string.onboarding_body_2, R.string.onboarding_body_3)) {
                    Text(
                        stringResource(text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = softWhite,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            PrimaryButton(stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}
