package com.goalgate.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalgate.app.GoalGateApp
import com.goalgate.app.R
import com.goalgate.app.data.Answer
import com.goalgate.app.data.AppWeek
import com.goalgate.app.data.DayPart
import com.goalgate.app.data.Goal
import com.goalgate.app.data.GoalCount
import com.goalgate.app.data.Week
import com.goalgate.app.data.WeekSentence
import com.goalgate.app.data.computeWeek
import com.goalgate.app.data.nextSteps
import com.goalgate.app.ui.theme.LocalAccent
import com.goalgate.app.ui.theme.SkyViolet
import com.goalgate.app.ui.theme.goalColor

/**
 * The Reflections tab: the last seven days, described plainly.
 * How often each goal was picked, how often the answer was Yes, Maybe or No,
 * how often the user chose Close, and each app's week. No scores, no grades,
 * no streaks: just what happened, so the user can notice their own patterns.
 * All numbers come from the pause log (see data/Stats.kt).
 */
@Composable
fun ReflectionsScreen() {
    val context = LocalContext.current
    val database = (context.applicationContext as GoalGateApp).database
    // null until the database has answered, so we never flash "0" by mistake.
    val logs by database.triggerLogDao().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val goals by database.goalDao().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val steps by database.goalStepDao().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val loadedLogs = logs ?: return
    val loadedGoals = goals ?: return
    val loadedSteps = steps ?: return
    val week = remember(loadedLogs) { computeWeek(loadedLogs, System.currentTimeMillis()) }
    ReflectionsContent(week, loadedGoals, nextSteps(loadedSteps).mapValues { it.value.text })
}

/** The layout, separate from the database so screenshot tests can draw it with sample data. */
@Composable
internal fun ReflectionsContent(week: Week, goals: List<Goal>, nextSteps: Map<Long, String> = emptyMap()) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(R.string.reflections_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (week.pauses == 0) R.string.reflections_empty else R.string.reflections_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (week.pauses == 0) return@Column

        // --- The week card: one plain sentence and the three answers ---
        Spacer(Modifier.height(24.dp))
        HeroGround(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, MaterialTheme.shapes.large, ambientColor = SkyViolet, spotColor = SkyViolet)
                .clip(MaterialTheme.shapes.large),
            fullScreen = false,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SummitMark(lineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                    Spacer(Modifier.width(10.dp))
                    SectionLabel(stringResource(R.string.reflections_this_week), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    weekSentenceText(week.sentence),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(24.dp))
                AnswerSplit(week, started)
            }
        }

        // --- The ledger ---
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CountRow(stringResource(R.string.reflections_pauses), week.pauses, started)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CountRow(stringResource(R.string.reflections_closes), week.closes, started)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CountRow(stringResource(R.string.reflections_continues), week.continues, started)
        // Downtime is counted on its own: it is rest the user chose, not an answer.
        if (week.downtimes > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DowntimeRow(
                stringResource(R.string.reflections_downtime), week.downtimes,
                stringResource(R.string.reflections_downtime_minutes, week.downtimeMinutes / 60, week.downtimeMinutes % 60),
            )
        }
        if (week.breaks > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DowntimeRow(stringResource(R.string.reflections_breaks), week.breaks, stringResource(R.string.reflections_break_minutes, week.breakMinutes))
        }

        // --- Goals this week ---
        if (week.goals.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
            SectionLabel(stringResource(R.string.reflections_by_goal))
            Spacer(Modifier.height(4.dp))
            val most = week.goals.maxOf { it.times }
            for (count in week.goals) GoalWeekRow(count, goals, count.goalId?.let { nextSteps[it] }, most, started)
        }

        // --- This week, app by app ---
        if (week.apps.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
            SectionLabel(stringResource(R.string.reflections_by_app))
            Spacer(Modifier.height(4.dp))
            for (app in week.apps) AppWeekRow(app, started)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** "This week you answered No 6 times. Most of those were in the evening." */
@Composable
private fun weekSentenceText(sentence: WeekSentence?): String {
    if (sentence == null) return stringResource(R.string.reflections_no_answers)
    val first = pluralStringResource(
        when (sentence.answer) {
            Answer.YES -> R.plurals.week_said_yes
            Answer.MAYBE -> R.plurals.week_said_maybe
            Answer.NO -> R.plurals.week_said_no
        },
        sentence.times, sentence.times,
    )
    val second = when (sentence.mostly) {
        DayPart.MORNING -> stringResource(R.string.week_mostly_morning)
        DayPart.AFTERNOON -> stringResource(R.string.week_mostly_afternoon)
        DayPart.EVENING -> stringResource(R.string.week_mostly_evening)
        DayPart.NIGHT -> stringResource(R.string.week_mostly_night)
        null -> null
    }
    return if (second == null) first else "$first $second"
}

/**
 * Yes, Maybe and No as three numbers with a fine bar under each that fills
 * to its share of the week's answers. All three use the same white: none of
 * the answers is better than the others.
 */
@Composable
private fun AnswerSplit(week: Week, started: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        for ((label, value) in listOf(
            stringResource(R.string.answer_short_yes) to week.yes,
            stringResource(R.string.answer_short_maybe) to week.maybe,
            stringResource(R.string.answer_short_no) to week.no,
        )) {
            val shown by animateIntAsState(if (started) value else 0, tween(900), label = "count")
            val share = if (week.answered == 0) 0f else value.toFloat() / week.answered
            val fill by animateFloatAsState(if (started) share else 0f, tween(1000, easing = FastOutSlowInEasing), label = "share")
            Column(Modifier.weight(1f)) {
                Text(shown.toString(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                SectionLabel(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.16f))) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White.copy(alpha = 0.85f)))
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int, started: Boolean) {
    val shown by animateIntAsState(if (started) value else 0, tween(900), label = "count")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(shown.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** One goal's week: its words and how many times it was picked, with a fine line in its colour. */
@Composable
private fun GoalWeekRow(count: GoalCount, goals: List<Goal>, nextStep: String?, most: Int, started: Boolean) {
    val accent = LocalAccent.current.accent
    val name = when (val id = count.goalId) {
        null -> stringResource(R.string.reflections_no_goal)
        else -> goals.firstOrNull { it.id == id }?.text ?: stringResource(R.string.reflections_removed_goal)
    }
    val fill by animateFloatAsState(
        if (started) count.times.toFloat() / most.coerceAtLeast(1) else 0f,
        tween(900, easing = FastOutSlowInEasing),
        label = "goalShare",
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                userText(name),
                style = if (count.goalId == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                pluralStringResource(R.plurals.reflections_times, count.times, count.times),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
        if (nextStep != null) {
            Text(
                stringResource(R.string.pause_next_step, userText(nextStep)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
            // The goal's own colour ("None right now" has none).
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(count.goalId?.let { goalColor(it) } ?: accent))
        }
    }
}

/** "Downtime you chose: 3, 1 h 45 min in all" (also used for breaks). */
@Composable
private fun DowntimeRow(label: String, times: Int, detail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(times.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** The name and icon of an installed app, or the package name if it was uninstalled. */
@Composable
private fun rememberAppInfo(packageName: String): Pair<String, ImageBitmap?> {
    val context = LocalContext.current
    return remember(packageName) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString() to pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            packageName to null
        }
    }
}

/**
 * One app's week: icon, name, and "closed 9 of 23". The fine line underneath
 * fills to the share of pauses where the user chose Close.
 */
@Composable
private fun AppWeekRow(week: AppWeek, started: Boolean) {
    val (label, icon) = rememberAppInfo(week.packageName)
    val accent = LocalAccent.current.accent
    val share = if (week.pauses == 0) 0f else week.closes.toFloat() / week.pauses
    val fill by animateFloatAsState(if (started) share else 0f, tween(900, easing = FastOutSlowInEasing), label = "appShare")

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.stats_app_closed, week.pauses, week.closes, week.pauses),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(accent))
        }
    }
}
