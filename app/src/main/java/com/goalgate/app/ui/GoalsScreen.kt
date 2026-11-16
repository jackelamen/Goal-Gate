package com.goalgate.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goalgate.app.data.AppDatabase
import com.goalgate.app.data.GoalStep
import com.goalgate.app.data.MAX_STEPS
import com.goalgate.app.data.STEP_MAX_CHARS
import com.goalgate.app.data.moveItem
import com.goalgate.app.data.nextSteps
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalgate.app.GoalGateApp
import com.goalgate.app.R
import com.goalgate.app.data.GOAL_MAX_CHARS
import com.goalgate.app.data.Goal
import com.goalgate.app.data.MAX_ACTIVE_GOALS
import com.goalgate.app.data.WHY_MAX_CHARS
import com.goalgate.app.data.achievedGoals
import com.goalgate.app.data.activeGoals
import com.goalgate.app.data.canAddGoal
import com.goalgate.app.data.cleanGoalText
import com.goalgate.app.data.moveGoal
import com.goalgate.app.data.nextPosition
import com.goalgate.app.data.renumber
import com.goalgate.app.ui.theme.LocalAccent
import com.goalgate.app.ui.theme.goalColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The Goals tab: the user's goals in order. Edit, reorder, add a "why",
 * or mark one achieved (a calm, brief celebration). Achieved goals move to
 * a quiet list underneath, where they can be brought back.
 *
 * Each goal shows its next step (the first one not checked off), and this is
 * the one place where a step is checked off. The pause only shows it.
 */
@Composable
fun GoalsScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val database = (context.applicationContext as GoalGateApp).database
    val dao = database.goalDao()
    val stepDao = database.goalStepDao()
    val all by dao.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val steps by stepDao.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var justAchieved by remember { mutableStateOf<Goal?>(null) }

    val loaded = all ?: return
    val loadedSteps = steps ?: return
    val active = activeGoals(loaded)
    val achieved = achievedGoals(loaded)
    val next = nextSteps(loadedSteps)
    val checkedOffText = stringResource(R.string.step_checked_off)
    val undoText = stringResource(R.string.step_undo)

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().readableWidth(),
    ) {
        item {
            Text(
                stringResource(R.string.goals_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (active.isEmpty()) R.string.goals_empty else R.string.goals_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The calm celebration: a small dawn-sky card that rises in, with a short burst of confetti for a few seconds.
            AnimatedVisibility(
                visible = justAchieved != null,
                enter = fadeIn(tween(300)) + slideInVertically(tween(420)) { it / 3 },
                exit = fadeOut(tween(500)),
            ) {
                AchievedBanner(justAchieved?.text ?: "", Modifier.padding(top = 18.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        items(active, key = { it.id }) { goal ->
            val index = active.indexOf(goal)
            GoalCard(
                goal = goal,
                nextStep = next[goal.id],
                canMoveUp = index > 0,
                canMoveDown = index < active.lastIndex,
                onMove = { delta -> scope.launch { dao.update(moveGoal(loaded, goal.id, delta)) } },
                onEdit = { editing = goal },
                onCheckStep = { step ->
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    scope.launch {
                        stepDao.update(step.copy(doneAt = System.currentTimeMillis()))
                        GoalGateWidget.refresh(context)
                        // A quiet way back, in case of a mis-tap.
                        val result = snackbar.showSnackbar(
                            message = checkedOffText.format(step.text),
                            actionLabel = undoText,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            stepDao.update(step.copy(doneAt = null))
                            GoalGateWidget.refresh(context)
                        }
                    }
                },
                onAchieved = {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                        else HapticFeedbackConstants.LONG_PRESS
                    )
                    justAchieved = goal
                    scope.launch {
                        dao.update(listOf(goal.copy(achievedAt = System.currentTimeMillis())))
                        dao.update(renumber(active.filter { it.id != goal.id }))
                        GoalGateWidget.refresh(context)
                    }
                },
                modifier = Modifier.animateItem(),
            )
        }

        item {
            if (canAddGoal(loaded)) {
                Spacer(Modifier.height(6.dp))
                PrimaryButton(stringResource(R.string.goals_add), onClick = { adding = true })
            } else {
                Text(
                    pluralStringResource(R.plurals.goals_limit, MAX_ACTIVE_GOALS, MAX_ACTIVE_GOALS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (achieved.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.goals_achieved))
            }
            items(achieved, key = { "a" + it.id }) { goal ->
                AchievedRow(
                    goal = goal,
                    canRestore = canAddGoal(loaded),
                    onRestore = {
                        scope.launch {
                            dao.update(listOf(goal.copy(achievedAt = null, position = nextPosition(loaded))))
                            GoalGateWidget.refresh(context)
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).readableWidth().padding(horizontal = 12.dp))
    }

    LaunchedEffect(justAchieved) {
        if (justAchieved != null) {
            delay(3200)
            justAchieved = null
        }
    }

    if (adding) {
        GoalEditor(
            initial = null,
            initialSteps = emptyList(),
            onSave = { draft -> scope.saveGoal(database, null, loaded, draft) { GoalGateWidget.refresh(context) }; adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { goal ->
        GoalEditor(
            initial = goal,
            initialSteps = loadedSteps.filter { it.goalId == goal.id }.sortedBy { it.position },
            onSave = { draft ->
                scope.saveGoal(database, goal, loaded, draft) { GoalGateWidget.refresh(context) }
                editing = null
            },
            onDelete = {
                scope.deleteGoal(database, goal, active) { GoalGateWidget.refresh(context) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** A goal as edited in [GoalEditor], before it is saved. */
data class GoalDraft(val text: String, val why: String?, val schedule: String, val steps: List<StepDraft>)

/** One step in the editor. [key] keeps rows stable while they are moved. */
data class StepDraft(val key: Long, val text: String, val doneAt: Long?)

/** Saves a new goal (at the end of the list) or an edited one, with its steps. Used here and in onboarding. */
fun CoroutineScope.saveGoal(database: AppDatabase, existing: Goal?, current: List<Goal>, draft: GoalDraft, then: () -> Unit = {}) {
    launch {
        val id = if (existing == null) {
            database.goalDao().insert(
                Goal(
                    text = draft.text, why = draft.why, position = nextPosition(current),
                    createdAt = System.currentTimeMillis(), schedule = draft.schedule,
                )
            )
        } else {
            database.goalDao().update(listOf(existing.copy(text = draft.text, why = draft.why, schedule = draft.schedule)))
            existing.id
        }
        database.goalStepDao().replaceForGoal(id, draft.steps.map { GoalStep(goalId = id, text = it.text, position = 0, doneAt = it.doneAt) })
        then()
    }
}

/** Deletes a goal and its steps, and closes the gap in the order. */
fun CoroutineScope.deleteGoal(database: AppDatabase, goal: Goal, active: List<Goal>, then: () -> Unit = {}) {
    launch {
        database.goalStepDao().deleteForGoal(goal.id)
        database.goalDao().delete(goal)
        database.goalDao().update(renumber(active.filter { it.id != goal.id }))
        then()
    }
}

/**
 * One active goal: the user's words, their "why", the next step with a
 * check-off circle, and four small actions.
 */
@Composable
private fun GoalCard(
    goal: Goal,
    nextStep: GoalStep?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onCheckStep: (GoalStep) -> Unit,
    onAchieved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The stripe on the left is the goal's own colour, the same one it has on the pause.
    Column(
        modifier.fillMaxWidth().card().clip(MaterialTheme.shapes.medium).goalStripe(goalColor(goal.id))
            .padding(start = 24.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
    ) {
        if (goal.schedule == Goal.SCHEDULE_WORKDAY) {
            SectionLabel(stringResource(R.string.goal_when_workday), modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.padding(end = 12.dp)) {
            FitBlock(goal.text, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
        val why = goal.why
        if (!why.isNullOrBlank()) {
            Text(
                userText(why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, end = 12.dp),
            )
        }
        if (nextStep != null) NextStepRow(nextStep, goalColor(goal.id), onCheck = { onCheckStep(nextStep) })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallAction("↑", stringResource(R.string.goals_move_up), enabled = canMoveUp) { onMove(-1) }
            SmallAction("↓", stringResource(R.string.goals_move_down), enabled = canMoveDown) { onMove(1) }
            Spacer(Modifier.weight(1f))
            SmallAction(stringResource(R.string.goals_edit), null, onClick = onEdit)
            SmallAction(stringResource(R.string.goals_mark_achieved), null, accent = true, onClick = onAchieved)
        }
    }
}

/**
 * "Next step: Buy running shoes", with an empty circle in front. Tapping the
 * circle checks the step off; the following step then takes its place.
 */
@Composable
private fun NextStepRow(step: GoalStep, accent: Color, onCheck: () -> Unit) {
    val description = stringResource(R.string.step_check_off, step.text)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp, end = 12.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(start = 4.dp, end = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Checkbox, onClick = onCheck)
                .semantics { contentDescription = description },
        ) {
            Box(Modifier.size(22.dp).border(2.2.dp, accent, CircleShape))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            SectionLabel(stringResource(R.string.step_next_label))
            Text(userText(step.text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A text button sized for a row of actions. [description] is read by screen readers for arrows. */
@Composable
private fun SmallAction(
    text: String,
    description: String?,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 44.dp).then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier
        ),
    ) {
        // One line that shrinks a little on narrow screens, never breaking a word.
        FitText(
            text,
            MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.outlineVariant
                accent -> LocalAccent.current.accent
                else -> MaterialTheme.colorScheme.onSurface
            },
            minFontSize = 11.sp,
        )
    }
}

/** An achieved goal: struck neither through nor hidden, just set aside with its date. */
@Composable
private fun AchievedRow(goal: Goal, canRestore: Boolean, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val date = remember(goal.achievedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(goal.achievedAt ?: 0L))
    }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            SummitMark(lineColor = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(userText(goal.text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    stringResource(R.string.goals_achieved_on, date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canRestore) SmallAction(stringResource(R.string.goals_restore), null, onClick = onRestore)
        }
    }
}

/** The celebration card: the summit draws itself in, the dot lifts once. */
@Composable
private fun AchievedBanner(goalText: String, modifier: Modifier = Modifier) {
    HeroGround(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
        fullScreen = false,
    ) {
        // A short burst in the five goal colours, from the mark.
        var origin by remember { mutableStateOf(Offset.Zero) }
        ConfettiBurst(burstKey = goalText.takeIf { it.isNotEmpty() && origin != Offset.Zero }, origin = origin, modifier = Modifier.matchParentSize())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp).onGloballyPositioned { origin = Offset(it.size.height / 2f + 40f, it.size.height / 2f + 60f) },
        ) {
            SummitMark(drawIn = true, celebrate = true, lineColor = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(14.dp))
            Column {
                SectionLabel(stringResource(R.string.goals_achieved_banner), color = LocalAccent.current.accent)
                Spacer(Modifier.height(2.dp))
                Text(userText(goalText), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * Add or edit a goal, full screen: the goal (up to 60 characters), an
 * optional one-line "why", when it applies (always, or only on workdays),
 * and optional steps in order. [onDelete] is null when adding.
 *
 * Steps are plain text: add, edit, delete and reorder here. They are checked
 * off in the Goals tab. A step already checked off can be marked not done.
 */
@Composable
fun GoalEditor(
    initial: Goal?,
    initialSteps: List<GoalStep>,
    onSave: (GoalDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var why by remember { mutableStateOf(initial?.why ?: "") }
    var schedule by remember { mutableStateOf(initial?.schedule ?: Goal.SCHEDULE_ALWAYS) }
    var nextKey by remember { mutableLongStateOf(initialSteps.size.toLong()) }
    var steps by remember {
        mutableStateOf(initialSteps.mapIndexed { i, step -> StepDraft(i.toLong(), step.text, step.doneAt) })
    }
    val cleaned = cleanGoalText(text)
    var confirmDelete by remember { mutableStateOf(false) }
    val save = {
        if (cleaned != null) {
            onSave(
                GoalDraft(
                    text = cleaned,
                    why = cleanGoalText(why, WHY_MAX_CHARS),
                    schedule = schedule,
                    // Empty steps are dropped; the rest keep their order.
                    steps = steps.mapNotNull { d -> cleanGoalText(d.text, STEP_MAX_CHARS)?.let { d.copy(text = it) } },
                )
            )
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Top bar: Cancel on the left, Save on the right.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.readableWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.reset_cancel), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = save, enabled = cleaned != null) {
                        Text(
                            stringResource(R.string.goal_save),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (cleaned != null) LocalAccent.current.accent else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .readableWidth()
                        .padding(horizontal = 28.dp),
                ) {
                    Text(
                        stringResource(if (initial == null) R.string.goal_new_title else R.string.goal_edit_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(20.dp))
                    GoalField(
                        value = text,
                        onValueChange = { text = it.replace("\n", "").take(GOAL_MAX_CHARS) },
                        label = stringResource(R.string.goal_field),
                        hint = stringResource(R.string.goal_field_hint),
                        max = GOAL_MAX_CHARS,
                    )
                    Spacer(Modifier.height(10.dp))
                    GoalField(
                        value = why,
                        onValueChange = { why = it.replace("\n", "").take(WHY_MAX_CHARS) },
                        label = stringResource(R.string.why_field),
                        hint = stringResource(R.string.why_field_hint),
                        max = WHY_MAX_CHARS,
                    )

                    // --- When it applies ---
                    Spacer(Modifier.height(24.dp))
                    SectionLabel(stringResource(R.string.goal_when))
                    Spacer(Modifier.height(10.dp))
                    ChoiceRow(
                        options = listOf(Goal.SCHEDULE_ALWAYS, Goal.SCHEDULE_WORKDAY),
                        selected = schedule,
                        label = { stringResource(if (it == Goal.SCHEDULE_WORKDAY) R.string.goal_when_workday else R.string.goal_when_always) },
                        onSelect = { schedule = it },
                    )
                    Text(
                        stringResource(if (schedule == Goal.SCHEDULE_WORKDAY) R.string.goal_when_workday_hint else R.string.goal_when_always_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    // --- Steps (optional) ---
                    Spacer(Modifier.height(28.dp))
                    SectionLabel(stringResource(R.string.steps_title))
                    Text(
                        stringResource(R.string.steps_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                    )
                    steps.forEachIndexed { index, step ->
                        key(step.key) {
                            StepEditRow(
                                number = index + 1,
                                step = step,
                                canMoveUp = index > 0,
                                canMoveDown = index < steps.lastIndex,
                                onText = { value -> steps = steps.map { if (it.key == step.key) it.copy(text = value.replace("\n", "").take(STEP_MAX_CHARS)) else it } },
                                onMove = { delta -> steps = moveItem(steps, index, delta) },
                                onDelete = { steps = steps.filter { it.key != step.key } },
                                onNotDone = { steps = steps.map { if (it.key == step.key) it.copy(doneAt = null) else it } },
                            )
                        }
                    }
                    if (steps.size < MAX_STEPS) {
                        QuietButton(
                            "+ " + stringResource(R.string.steps_add),
                            onClick = {
                                steps = steps + StepDraft(nextKey, "", null)
                                nextKey++
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    if (onDelete != null) {
                        Spacer(Modifier.height(28.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(if (confirmDelete) R.string.goal_delete_confirm else R.string.goal_delete),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { if (confirmDelete) onDelete() else confirmDelete = true }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * One step in the editor: its number, the text, and small actions to move or
 * remove it. A step already checked off shows "Done" and can be marked not done.
 */
@Composable
private fun StepEditRow(
    number: Int,
    step: StepDraft,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onText: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onNotDone: () -> Unit,
) {
    val done = step.doneAt != null
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$number",
                style = MaterialTheme.typography.titleLarge,
                color = if (done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            OutlinedTextField(
                value = step.text,
                onValueChange = onText,
                placeholder = { Text(stringResource(R.string.steps_field_hint), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalAccent.current.accent,
                    cursorColor = LocalAccent.current.accent,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp)) {
            if (done) {
                SectionLabel(stringResource(R.string.step_done), color = LocalAccent.current.accent, modifier = Modifier.padding(start = 8.dp))
                SmallAction(stringResource(R.string.step_not_done), null, onClick = onNotDone)
            }
            Spacer(Modifier.weight(1f))
            SmallAction("↑", stringResource(R.string.goals_move_up), enabled = canMoveUp) { onMove(-1) }
            SmallAction("↓", stringResource(R.string.goals_move_down), enabled = canMoveDown) { onMove(1) }
            SmallAction(stringResource(R.string.step_remove), null, onClick = onDelete)
        }
    }
}

@Composable
private fun GoalField(value: String, onValueChange: (String) -> Unit, label: String, hint: String, max: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
        supportingText = { Text("${value.length}/$max", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LocalAccent.current.accent,
            focusedLabelColor = LocalAccent.current.accent,
            cursorColor = LocalAccent.current.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
