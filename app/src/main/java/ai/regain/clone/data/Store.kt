package ai.regain.clone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class TimerMode { FOCUS, SHORT_BREAK, LONG_BREAK, STOPWATCH }

enum class YoutubeMode { OFF, ALLOWLIST, BLOCK_ALL }

val SUBJECT_COLORS = listOf(
    0xFFC8F560L, 0xFF7C9DFF, 0xFFFF9F6B, 0xFFB68CFF,
    0xFF5FE3C0, 0xFFFF7CAB, 0xFFFFD166, 0xFF9AA6B2
)

data class Subject(val name: String, val color: Long, val emoji: String)

val defaultSubjects = listOf(
    Subject("Math", 0xFF7C9DFFL, "∑"),
    Subject("Science", 0xFF5FE3C0L, "⚛"),
    Subject("Coding", 0xFFC8F560L, "</>"),
    Subject("Reading", 0xFFFF9F6BL, "📖"),
    Subject("Work", 0xFFB68CFFL, "◆"),
    Subject("Other", 0xFF9AA6B2L, "•")
)

data class Session(
    val id: Long,
    val start: Long,
    val seconds: Int,
    val subject: String,
    val mode: String,
    val completed: Boolean
) {
    val minutes: Int get() = (seconds + 30) / 60
}

data class Settings(
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val roundsBeforeLong: Int = 4,
    val dailyGoalMin: Int = 120,
    val strictMode: Boolean = false,
    val lockSettingsDuringStrict: Boolean = true,
    val focusGuard: Boolean = true,
    val autoStartBreaks: Boolean = true,
    val autoStartFocus: Boolean = false,
    val ambience: String = "none",
    val ambienceVolume: Float = 0.55f,
    val theme: String = "midnight",
    val blockingEnabled: Boolean = false,
    val blockedApps: Set<String> = emptySet(),
    val blockReels: Boolean = false,
    val blockShorts: Boolean = false,
    val youtubeMode: String = "off",
    val allowedChannels: Set<String> = emptySet(),
    val showSystemApps: Boolean = false,
    val learnChannels: Boolean = true,
    val haptics: Boolean = true,
    val proUnlocked: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("focusMin", focusMin)
        put("shortBreakMin", shortBreakMin)
        put("longBreakMin", longBreakMin)
        put("roundsBeforeLong", roundsBeforeLong)
        put("dailyGoalMin", dailyGoalMin)
        put("strictMode", strictMode)
        put("lockSettingsDuringStrict", lockSettingsDuringStrict)
        put("focusGuard", focusGuard)
        put("autoStartBreaks", autoStartBreaks)
        put("autoStartFocus", autoStartFocus)
        put("ambience", ambience)
        put("ambienceVolume", ambienceVolume.toDouble())
        put("theme", theme)
        put("blockingEnabled", blockingEnabled)
        put("blockedApps", JSONArray(blockedApps.toList()))
        put("blockReels", blockReels)
        put("blockShorts", blockShorts)
        put("youtubeMode", youtubeMode)
        put("allowedChannels", JSONArray(allowedChannels.toList()))
        put("showSystemApps", showSystemApps)
        put("learnChannels", learnChannels)
        put("haptics", haptics)
        put("proUnlocked", proUnlocked)
    }

    companion object {
        fun fromJson(raw: String?): Settings {
            if (raw.isNullOrBlank()) return Settings()
            return try {
                val o = JSONObject(raw)
                Settings(
                    focusMin = o.optInt("focusMin", 25),
                    shortBreakMin = o.optInt("shortBreakMin", 5),
                    longBreakMin = o.optInt("longBreakMin", 15),
                    roundsBeforeLong = o.optInt("roundsBeforeLong", 4),
                    dailyGoalMin = o.optInt("dailyGoalMin", 120),
                    strictMode = o.optBoolean("strictMode", false),
                    lockSettingsDuringStrict = o.optBoolean("lockSettingsDuringStrict", true),
                    focusGuard = o.optBoolean("focusGuard", true),
                    autoStartBreaks = o.optBoolean("autoStartBreaks", true),
                    autoStartFocus = o.optBoolean("autoStartFocus", false),
                    ambience = o.optString("ambience", "none"),
                    ambienceVolume = o.optDouble("ambienceVolume", 0.55).toFloat(),
                    theme = o.optString("theme", "midnight"),
                    blockingEnabled = o.optBoolean("blockingEnabled", false),
                    blockedApps = o.optJSONArray("blockedApps").toStringSet(),
                    blockReels = o.optBoolean("blockReels", false),
                    blockShorts = o.optBoolean("blockShorts", false),
                    youtubeMode = o.optString("youtubeMode", "off"),
                    allowedChannels = o.optJSONArray("allowedChannels").toStringSet(),
                    showSystemApps = o.optBoolean("showSystemApps", false),
                    learnChannels = o.optBoolean("learnChannels", true),
                    haptics = o.optBoolean("haptics", true),
                    proUnlocked = o.optBoolean("proUnlocked", true)
                )
            } catch (t: Throwable) {
                Settings()
            }
        }

        private fun JSONArray?.toStringSet(): Set<String> {
            if (this == null) return emptySet()
            val out = linkedSetOf<String>()
            for (i in 0 until length()) {
                val v = optString(i, "")
                if (v.isNotBlank()) out.add(v)
            }
            return out
        }
    }
}

object Store {
    private var prefs: SharedPreferences? = null

    var settings by mutableStateOf(Settings())
        private set
    var sessions by mutableStateOf<List<Session>>(emptyList())
        private set
    var subjects by mutableStateOf(defaultSubjects)
        private set
    var recentChannels by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("regain_store", Context.MODE_PRIVATE)
        prefs = p
        settings = Settings.fromJson(p.getString("settings", null))
        sessions = readSessions(p.getString("sessions", null))
        subjects = readSubjects(p.getString("subjects", null))
        recentChannels = readStringList(p.getString("recent_channels", null))
    }

    fun saveSettings(value: Settings) {
        settings = value
        prefs?.edit()?.putString("settings", value.toJson().toString())?.apply()
    }

    fun update(block: (Settings) -> Settings) = saveSettings(block(settings))

    fun addSubject(subject: Subject) {
        if (subjects.any { it.name.equals(subject.name, ignoreCase = true) }) return
        subjects = subjects + subject
        prefs?.edit()?.putString("subjects", subjectsToJson(subjects).toString())?.apply()
    }

    fun removeSubject(name: String) {
        if (subjects.size <= 1) return
        subjects = subjects.filterNot { it.name == name }
        prefs?.edit()?.putString("subjects", subjectsToJson(subjects).toString())?.apply()
    }

    fun addSession(session: Session) {
        sessions = (sessions + session).sortedBy { it.start }
        prefs?.edit()?.putString("sessions", sessionsToJson(sessions).toString())?.apply()
    }

    fun clearSessions() {
        sessions = emptyList()
        prefs?.edit()?.remove("sessions")?.apply()
    }

    fun noteChannels(names: List<String>) {
        if (!settings.learnChannels || names.isEmpty()) return
        val merged = (names + recentChannels)
            .distinctBy { it.lowercase() }
            .take(12)
        recentChannels = merged
        prefs?.edit()?.putString("recent_channels", JSONArray(merged).toString())?.apply()
    }

    // ---------- stats ----------

    fun dayStart(time: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun addDays(time: Long, days: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    fun focusSessions(): List<Session> = sessions.filter { it.mode == TimerMode.FOCUS.name }

    fun minutesBetween(from: Long, to: Long): Int =
        focusSessions().filter { it.start in from until to }.sumOf { it.minutes }

    fun minutesOnDay(dayStart: Long): Int = minutesBetween(dayStart, addDays(dayStart, 1))

    fun todayMinutes(): Int = minutesOnDay(dayStart(System.currentTimeMillis()))

    fun totalMinutes(): Int = focusSessions().sumOf { it.minutes }

    fun totalSessions(): Int = focusSessions().count { it.completed }

    fun last7Days(): List<Pair<Long, Int>> {
        val today = dayStart(System.currentTimeMillis())
        return (6 downTo 0).map { offset ->
            val start = addDays(today, -offset)
            start to minutesOnDay(start)
        }
    }

    fun subjectTotals(days: Int): List<Pair<String, Int>> {
        val from = addDays(dayStart(System.currentTimeMillis()), -(days - 1))
        val focus = focusSessions().filter { it.start >= from }
        return focus.groupBy { it.subject }.map { (k, v) -> k to v.sumOf { it.minutes } }
            .sortedByDescending { it.second }
    }

    fun streak(): Int {
        var day = dayStart(System.currentTimeMillis())
        if (minutesOnDay(day) == 0) {
            day = addDays(day, -1)
            if (minutesOnDay(day) == 0) return 0
        }
        var streak = 0
        while (minutesOnDay(day) > 0) {
            streak++
            day = addDays(day, -1)
        }
        return streak
    }

    fun bestDayMinutes(): Int {
        val grouped = focusSessions().groupBy { dayStart(it.start) }
        return grouped.values.maxOfOrNull { list -> list.sumOf { it.minutes } } ?: 0
    }

    fun subjectColor(name: String): Long = subjects.firstOrNull { it.name == name }?.color ?: 0xFF9AA6B2L

    // ---------- persistence helpers ----------

    private fun sessionsToJson(list: List<Session>): JSONArray {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("start", it.start)
                    put("seconds", it.seconds)
                    put("subject", it.subject)
                    put("mode", it.mode)
                    put("completed", it.completed)
                }
            )
        }
        return arr
    }

    private fun readSessions(raw: String?): List<Session> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Session(
                    id = o.optLong("id", i.toLong()),
                    start = o.optLong("start", 0L),
                    seconds = o.optInt("seconds", 0),
                    subject = o.optString("subject", "Other"),
                    mode = o.optString("mode", TimerMode.FOCUS.name),
                    completed = o.optBoolean("completed", true)
                )
            }.filter { it.start > 0 }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun subjectsToJson(list: List<Subject>): JSONArray {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("color", it.color)
                put("emoji", it.emoji)
            })
        }
        return arr
    }

    private fun readSubjects(raw: String?): List<Subject> {
        if (raw.isNullOrBlank()) return defaultSubjects
        return try {
            val arr = JSONArray(raw)
            val out = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Subject(
                    name = o.optString("name", "Other"),
                    color = o.optLong("color", 0xFF9AA6B2L),
                    emoji = o.optString("emoji", "•")
                )
            }
            if (out.isEmpty()) defaultSubjects else out
        } catch (t: Throwable) {
            defaultSubjects
        }
    }

    private fun readStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
