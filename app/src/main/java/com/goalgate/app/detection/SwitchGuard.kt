package com.goalgate.app.detection

/**
 * Tells a real "the user opened this app" apart from the flicker Android
 * produces while it switches apps.
 *
 * While an app is closing, the phone can report it in front one more time,
 * a split second after the home screen (Home, the back gesture, or the
 * pause's Close button, which sends the user home while the app is still
 * underneath). Taken at face value, that flicker looks like a fresh open and
 * the pause pops up as the user is leaving.
 *
 * An app reported again soon after it was left, or soon after the user tapped
 * Close on its pause, is "suspicious": GateController then waits a moment and
 * shows the pause only if the app is really still in front. Every other open
 * is shown at once, so normal detection stays as fast as before.
 *
 * Plain Kotlin with no Android code, so the rule can be unit-tested.
 */
class SwitchGuard {

    private val leftAt = HashMap<String, Long>()
    private val closedAt = HashMap<String, Long>()

    /** [packageName] stopped being the app in front at [now]. */
    fun left(packageName: String, now: Long) {
        leftAt[packageName] = now
    }

    /** The user tapped Close (or Back) on [packageName]'s pause at [now]. */
    fun closed(packageName: String, now: Long) {
        closedAt[packageName] = now
    }

    /** True if [packageName] showing up in front at [now] may be a flicker, not an open. */
    fun isSuspicious(packageName: String, now: Long): Boolean {
        val left = leftAt[packageName]
        val closed = closedAt[packageName]
        return (left != null && now - left in 0 until BOUNCE_MS) ||
            (closed != null && now - closed in 0 until AFTER_CLOSE_MS)
    }

    fun clear() {
        leftAt.clear()
        closedAt.clear()
    }

    companion object {
        /** An app back in front this soon after it was left may be a flicker. */
        const val BOUNCE_MS = 2_000L

        /** The same, for this long after Close on its pause (the home screen can be slow to appear). */
        const val AFTER_CLOSE_MS = 3_000L

        /** How long to wait before checking again that the app is really still in front. */
        const val CONFIRM_MS = 800L
    }
}
