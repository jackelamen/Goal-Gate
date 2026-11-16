package com.goalgate.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.GoalGateApp
import com.goalgate.app.R
import com.goalgate.app.data.HolidayBank
import com.goalgate.app.data.MAX_DOWNTIME_WINDOWS
import com.goalgate.app.data.TimeWindow
import com.goalgate.app.ui.theme.LocalAccent
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/*
 * The schedule parts of Settings: the workday (for goals marked "Workday"),
 * scheduled downtime (daily hours, weekends, public holidays) and whose
 * public holidays to use. Each value is saved the moment it changes.
 */

/** "Workday": which days and which hours goals marked "Workday" appear. */
@Composable
fun WorkdaySection() {
    val settings = (LocalContext.current.applicationContext as GoalGateApp).settings
    var days by remember { mutableStateOf(settings.workdayDays) }
    var hours by remember { mutableStateOf(settings.workdayHours) }

    SectionLabel(stringResource(R.string.settings_workday))
    Spacer(Modifier.height(12.dp))
    DayToggles(
        selected = days,
        onToggle = { day ->
            // At least one workday stays on.
            val next = if (day in days) days - day else days + day
            if (next.isNotEmpty()) {
                days = next
                settings.workdayDays = next
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    WindowRow(hours, onChange = { hours = it; settings.workdayHours = it })
    Text(
        stringResource(R.string.settings_workday_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** "Downtime": daily hours, weekends and public holidays when "It's my downtime" is offered first. */
@Composable
fun DowntimeSection() {
    val context = LocalContext.current
    val settings = (context.applicationContext as GoalGateApp).settings
    var windows by remember { mutableStateOf(settings.downtimeWindows) }
    var weekends by remember { mutableStateOf(settings.downtimeWeekends) }
    var holidays by remember { mutableStateOf(settings.downtimeHolidays) }
    var country by remember { mutableStateOf(settings.holidayCountry) }
    var choosingCountry by remember { mutableStateOf(false) }
    val save = { list: List<TimeWindow> -> windows = list; settings.downtimeWindows = list }

    SectionLabel(stringResource(R.string.settings_downtime))
    Text(
        stringResource(R.string.settings_downtime_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
    windows.forEachIndexed { index, window ->
        Spacer(Modifier.height(8.dp))
        WindowRow(
            window,
            onChange = { changed -> save(windows.mapIndexed { i, w -> if (i == index) changed else w }) },
            onRemove = { save(windows.filterIndexed { i, _ -> i != index }) },
        )
    }
    if (windows.size < MAX_DOWNTIME_WINDOWS) {
        QuietButton(
            "+ " + stringResource(R.string.settings_downtime_add),
            onClick = { save(windows + TimeWindow(21 * 60, 23 * 60)) },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    SwitchRow(stringResource(R.string.settings_downtime_weekends), weekends) { weekends = it; settings.downtimeWeekends = it }
    SwitchRow(
        stringResource(R.string.settings_downtime_holidays),
        checked = holidays && country.isNotEmpty(),
        enabled = country.isNotEmpty(),
    ) { holidays = it; settings.downtimeHolidays = it }

    // Whose public holidays: also used to keep "Workday" goals out of public holidays.
    Spacer(Modifier.height(20.dp))
    SectionLabel(stringResource(R.string.settings_holidays))
    Spacer(Modifier.height(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .clip(MaterialTheme.shapes.medium)
            .clickable { choosingCountry = true }
            .padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 16.dp),
    ) {
        Text(
            countryName(country),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val table = remember { HolidayBank.load(context) }
    Text(
        stringResource(R.string.settings_holidays_hint, table.firstYear, table.lastYear),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )

    if (choosingCountry) {
        val options = listOf("") + table.countries.keys.sortedBy { Locale("", it).getDisplayCountry(Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { choosingCountry = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_holidays)) },
            text = {
                Column {
                    for (code in options) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = code == country, role = Role.RadioButton) {
                                    country = code
                                    settings.holidayCountry = code
                                    choosingCountry = false
                                },
                        ) {
                            RadioButton(
                                selected = code == country,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = LocalAccent.current.accent),
                            )
                            Spacer(Modifier.padding(start = 12.dp))
                            Text(countryName(code), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingCountry = false }) {
                    Text(stringResource(R.string.reset_cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }
}

/** "South Korea", "대한민국"... in the app's language; "None" for no country. */
@Composable
private fun countryName(code: String): String =
    if (code.isEmpty()) stringResource(R.string.settings_holidays_none)
    else Locale("", code).getDisplayCountry(Locale.getDefault())

/** Seven round toggles, Monday first, with the day names in the phone's language. */
@Composable
private fun DayToggles(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val accent = LocalAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (day in DayOfWeek.entries) {
            val on = day in selected
            val fill by animateColorAsState(if (on) accent.accent else MaterialTheme.colorScheme.surfaceVariant, label = "day")
            val text by animateColorAsState(if (on) accent.onAccent else MaterialTheme.colorScheme.onSurfaceVariant, label = "dayText")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(fill)
                    .toggleable(value = on, role = Role.Checkbox) { onToggle(day) },
            ) {
                FitText(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), MaterialTheme.typography.labelLarge, text, minFontSize = 9.sp)
            }
        }
    }
}

/** "21:00 to 23:30" as two tappable times, and an optional Remove. */
@Composable
private fun WindowRow(window: TimeWindow, onChange: (TimeWindow) -> Unit, onRemove: (() -> Unit)? = null) {
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().card().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        TimeChip(window.startMinute) { editingStart = true }
        Text(
            stringResource(R.string.settings_time_to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TimeChip(window.endMinute) { editingStart = false }
        Spacer(Modifier.weight(1f))
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.step_remove), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    editingStart?.let { start ->
        TimeDialog(
            minute = if (start) window.startMinute else window.endMinute,
            onDone = { m ->
                onChange(if (start) window.copy(startMinute = m) else window.copy(endMinute = m))
                editingStart = null
            },
            onDismiss = { editingStart = null },
        )
    }
}

@Composable
private fun TimeChip(minute: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(formatTime(minute), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 1290 -> "21:30" or "9:30 PM", following the phone's clock style. */
fun formatTime(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = minute / 60,
        initialMinute = minute % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    val accent = LocalAccent.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = accent.accent,
                    timeSelectorSelectedContainerColor = accent.accent.copy(alpha = 0.16f),
                    periodSelectorSelectedContainerColor = accent.accent.copy(alpha = 0.16f),
                    clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.goal_save), color = accent.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reset_cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

/** A label with a switch on the right. The whole row is tappable. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LocalAccent.current.accent,
                checkedThumbColor = Color.White,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
