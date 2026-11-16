package com.goalgate.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalgate.app.GoalGateApp
import com.goalgate.app.R
import com.goalgate.app.data.GOAL_MAX_CHARS
import com.goalgate.app.data.Goal
import com.goalgate.app.data.GoalDao
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The Goals tab: the user's goals in order. Edit, reorder, add a "why",
 * or mark one achieved (a calm, brief celebration). Achieved goals move to
 * a quiet list underneath, where they can be brought back.
 */
@Composable
fun GoalsScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = (context.applicationContext as GoalGateApp).database.goalDao()
    val all by dao.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var justAchieved by remember { mutableStateOf<Goal?>(null) }

    val loaded = all ?: return
    val active = activeGoals(loaded)
    val achieved = achievedGoals(loaded)

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
            // The calm celebration: a small pine card that rises in for a few seconds.
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
                canMoveUp = index > 0,
                canMoveDown = index < active.lastIndex,
                onMove = { delta -> scope.launch { dao.update(moveGoal(loaded, goal.id, delta)) } },
                onEdit = { editing = goal },
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

    LaunchedEffect(justAchieved) {
        if (justAchieved != null) {
            delay(3200)
            justAchieved = null
        }
    }

    if (adding) {
        GoalEditDialog(
            initial = null,
            onSave = { text, why -> scope.addGoal(dao, loaded, text, why) { GoalGateWidget.refresh(context) }; adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { goal ->
        GoalEditDialog(
            initial = goal,
            onSave = { text, why ->
                scope.launch {
                    dao.update(listOf(goal.copy(text = text, why = why)))
                    GoalGateWidget.refresh(context)
                }
                editing = null
            },
            onDelete = {
                scope.launch {
                    dao.delete(goal)
                    dao.update(renumber(active.filter { it.id != goal.id }))
                    GoalGateWidget.refresh(context)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Saves a new goal at the end of the list. Used here and in onboarding. */
fun CoroutineScope.addGoal(dao: GoalDao, current: List<Goal>, text: String, why: String?, then: () -> Unit = {}) {
    launch {
        dao.insert(Goal(text = text, why = why, position = nextPosition(current), createdAt = System.currentTimeMillis()))
        then()
    }
}

/** One active goal: the user's words, their "why", and four small actions. */
@Composable
private fun GoalCard(
    goal: Goal,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onAchieved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().card().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp)) {
        Box(Modifier.padding(end = 12.dp)) {
            FitBlock(goal.text, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
        val why = goal.why
        if (!why.isNullOrBlank()) {
            Text(
                why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, end = 12.dp),
            )
        }
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 44.dp).then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.outlineVariant
                accent -> LocalAccent.current.accent
                else -> MaterialTheme.colorScheme.onSurface
            },
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
                Text(goal.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
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
        peak = Offset(0.9f, 0.2f),
        fullScreen = false,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            SummitMark(drawIn = true, celebrate = true, lineColor = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(14.dp))
            Column {
                SectionLabel(stringResource(R.string.goals_achieved_banner), color = LocalAccent.current.accent)
                Spacer(Modifier.height(2.dp))
                Text(goalText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * Add or edit a goal: the goal (up to 60 characters) and an optional one-line
 * "why". The examples are hints only; the user's own words are what count.
 * [onDelete] is null when adding.
 */
@Composable
fun GoalEditDialog(
    initial: Goal?,
    onSave: (text: String, why: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var why by remember { mutableStateOf(initial?.why ?: "") }
    val cleaned = cleanGoalText(text)
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(if (initial == null) R.string.goal_new_title else R.string.goal_edit_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Column {
                GoalField(
                    value = text,
                    onValueChange = { text = it.replace("\n", "").take(GOAL_MAX_CHARS) },
                    label = stringResource(R.string.goal_field),
                    hint = stringResource(R.string.goal_field_hint),
                    max = GOAL_MAX_CHARS,
                )
                Spacer(Modifier.height(14.dp))
                GoalField(
                    value = why,
                    onValueChange = { why = it.replace("\n", "").take(WHY_MAX_CHARS) },
                    label = stringResource(R.string.why_field),
                    hint = stringResource(R.string.why_field_hint),
                    max = WHY_MAX_CHARS,
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(if (confirmDelete) R.string.goal_delete_confirm else R.string.goal_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { if (confirmDelete) onDelete() else confirmDelete = true }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (cleaned != null) onSave(cleaned, cleanGoalText(why, WHY_MAX_CHARS)) }, enabled = cleaned != null) {
                Text(stringResource(R.string.goal_save), color = if (cleaned != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reset_cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
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
