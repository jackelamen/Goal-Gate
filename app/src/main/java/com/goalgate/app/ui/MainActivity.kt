package com.goalgate.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.R
import com.goalgate.app.GoalGateApp
import com.goalgate.app.detection.Engines
import com.goalgate.app.detection.Permissions
import com.goalgate.app.ui.theme.LocalAccent
import com.goalgate.app.ui.theme.GoalGateTheme

/**
 * The main window of the app. It holds every screen except the pause
 * screen (that one is PauseActivity, because it opens on top of other apps).
 */
class MainActivity : ComponentActivity() {

    /** Set when the widget's "Check in" asks for the Goals tab; AppRoot clears it once used. */
    private var openGoals by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openGoals = intent?.getBooleanExtra(EXTRA_OPEN_GOALS, false) == true
        setContent {
            GoalGateTheme { AppRoot(openGoals = openGoals, onGoalsOpened = { openGoals = false }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_GOALS, false)) openGoals = true
    }

    companion object {
        private const val EXTRA_OPEN_GOALS = "openGoals"

        /** Opens GoalGate on the Goals tab (used by the widget's "Check in"). */
        fun goalsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_GOALS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** Every time the app comes back to the front, make sure the right engine is running. */
    override fun onResume() {
        super.onResume()
        Engines.apply(this)
    }
}

/** The screens that take over the whole window. */
private enum class Screen { ONBOARDING, HOME, DISCLOSURE, DEBUG, MANUAL, LEGAL }

/** The four tabs inside HOME. */
private enum class Tab { GOALS, APPS, REFLECTIONS, SETTINGS }

/**
 * Decides which screen is visible. Navigation here is just one variable:
 * change [screen] and Compose shows the matching screen.
 */
@Composable
private fun AppRoot(openGoals: Boolean, onGoalsOpened: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GoalGateApp

    var screen by rememberSaveable {
        mutableStateOf(if (app.settings.onboardingDone) Screen.HOME else Screen.ONBOARDING)
    }
    // Where to return to after the accessibility disclosure.
    var screenBeforeDisclosure by rememberSaveable { mutableStateOf(Screen.HOME) }
    var tab by rememberSaveable { mutableStateOf(Tab.GOALS) }

    // The widget's "Check in" opens the Goals tab (once setup is finished).
    LaunchedEffect(openGoals) {
        if (openGoals) {
            if (screen != Screen.ONBOARDING) {
                tab = Tab.GOALS
                screen = Screen.HOME
            }
            onGoalsOpened()
        }
    }

    // Shows the disclosure first; only after "I agree" do we open system settings.
    val requestAccessibility = {
        screenBeforeDisclosure = screen
        screen = Screen.DISCLOSURE
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.ONBOARDING -> OnboardingScreen(
                    onFinished = {
                        app.settings.onboardingDone = true
                        Engines.apply(context)
                        tab = Tab.GOALS
                        screen = Screen.HOME
                    },
                )

                Screen.HOME -> HomeScreen(
                    tab = tab,
                    onTabChange = { tab = it },
                    onRequestAccessibility = requestAccessibility,
                    onOpenDebug = { screen = Screen.DEBUG },
                    onOpenManual = { screen = Screen.MANUAL },
                    onOpenLegal = { screen = Screen.LEGAL },
                    onReset = {
                        // Everything is deleted: start again from the first setup screen.
                        tab = Tab.GOALS
                        screen = Screen.ONBOARDING
                    },
                )

                Screen.DISCLOSURE -> {
                    BackHandler { screen = screenBeforeDisclosure }
                    DisclosureScreen(
                        onAgree = {
                            app.settings.accessibilityDisclosureAccepted = true
                            screen = screenBeforeDisclosure
                            context.startActivity(Permissions.accessibilityIntent())
                        },
                        onDecline = { screen = screenBeforeDisclosure },
                    )
                }

                Screen.DEBUG -> {
                    BackHandler { screen = Screen.HOME }
                    DebugScreen(onBack = { screen = Screen.HOME })
                }

                Screen.MANUAL -> {
                    BackHandler { screen = Screen.HOME }
                    ManualScreen(onBack = { screen = Screen.HOME })
                }

                // LegalScreen handles Back itself (it has pages inside it).
                Screen.LEGAL -> LegalScreen(onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenLegal: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // The current tab. A new tab slides in from the side it lives on.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(260)) { direction * it / 12 }) togetherWith
                    fadeOut(tween(100))
            },
            label = "tab",
            modifier = Modifier.weight(1f).statusBarsPadding(),
        ) { current ->
            when (current) {
                Tab.GOALS -> GoalsScreen()
                Tab.APPS -> AppPickerScreen()
                Tab.REFLECTIONS -> ReflectionsScreen()
                Tab.SETTINGS -> SettingsScreen(onRequestAccessibility, onOpenDebug, onOpenManual, onOpenLegal, onReset)
            }
        }

        // The bottom bar: four words, no icons to decode.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
        ) {
            Row(Modifier.readableWidth()) {
                TabButton(stringResource(R.string.tab_goals), tab == Tab.GOALS, Modifier.weight(1f)) { onTabChange(Tab.GOALS) }
                TabButton(stringResource(R.string.tab_apps), tab == Tab.APPS, Modifier.weight(1f)) { onTabChange(Tab.APPS) }
                TabButton(stringResource(R.string.tab_reflections), tab == Tab.REFLECTIONS, Modifier.weight(1f)) { onTabChange(Tab.REFLECTIONS) }
                TabButton(stringResource(R.string.tab_settings), tab == Tab.SETTINGS, Modifier.weight(1f)) { onTabChange(Tab.SETTINGS) }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabColor",
    )
    // The selected tab is marked by a tiny peak (the summit mark, reduced to its
    // ridge) that rises from nothing.
    val rise by animateFloatAsState(if (selected) 1f else 0f, label = "tabPeak")
    val accent = LocalAccent.current.accent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(top = 18.dp, bottom = 12.dp),
    ) {
        FitText(text.uppercase(), MaterialTheme.typography.labelMedium, color, minFontSize = 8.sp)
        Spacer(Modifier.height(7.dp))
        Canvas(Modifier.size(width = 14.dp, height = 6.dp)) {
            if (rise > 0f) {
                val h = size.height * rise
                val stroke = 1.6.dp.toPx()
                drawLine(accent, Offset(0f, size.height), Offset(size.width / 2, size.height - h), stroke, StrokeCap.Round)
                drawLine(accent, Offset(size.width / 2, size.height - h), Offset(size.width, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}
