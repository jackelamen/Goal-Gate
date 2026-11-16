package com.goalgate.app.data

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

/** The three answers to the pause question "Does opening [app] help this goal?". */
enum class Answer { YES, MAYBE, NO }

/** Reads a log row's answer column ("YES", "MAYBE", "NO"); null if missing or unknown. */
fun answerOf(value: String?): Answer? = Answer.entries.firstOrNull { it.name == value }

/**
 * One short reflection line, shown after the user answers. Each line belongs
 * to one answer, so a "No" is followed by a line written for a "No".
 * Lines are original (written for GoalGate), in English and Korean.
 */
data class ReflectionLine(val id: String, val answer: Answer, val en: String, val ko: String)

/** Reads the JSON written by tools/make_reflections.py. */
fun parseReflections(json: String): List<ReflectionLine> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        ReflectionLine(
            id = o.getString("id"),
            answer = Answer.valueOf(o.getString("answer")),
            en = o.getString("en"),
            ko = o.getString("ko"),
        )
    }
}

/** Loads assets/reflections.json once and keeps it in memory. */
object ReflectionBank {
    @Volatile private var cache: List<ReflectionLine>? = null

    fun load(context: Context): List<ReflectionLine> =
        cache ?: synchronized(this) {
            cache ?: parseReflections(
                context.assets.open("reflections.json").bufferedReader().use { it.readText() }
            ).also { cache = it }
        }
}

/**
 * Picks a line for [answer], avoiding the ids in [recentIds] (lines shown
 * lately) for as long as there are others to choose from.
 */
fun pickLine(
    lines: List<ReflectionLine>,
    answer: Answer,
    recentIds: Collection<String>,
    random: Random = Random.Default,
): ReflectionLine? {
    val pool = lines.filter { it.answer == answer }
    if (pool.isEmpty()) return null
    val fresh = pool.filter { it.id !in recentIds }
    return (fresh.ifEmpty { pool }).random(random)
}
