package ai.regain.clone.block

import ai.regain.clone.data.Settings
import ai.regain.clone.data.Store
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.TreeMap

/**
 * Heuristic screen reader.
 *
 * This is a from-scratch port of the detection strategy used by the original Regain
 * app (verified by decompiling its accessibility service with JADX):
 *
 *  1. Read every visible node with its text, content description, view id,
 *     class name and on-screen bounds.
 *  2. Bucket nodes into horizontal rows by their vertical midpoint and merge
 *     rows that are within 10 px of each other.
 *  3. A YouTube watch page is confirmed by `watch_panel_scrim` (or the player /
 *     subscribe view ids). Shorts are confirmed by the reel view ids and the
 *     like / comment / share / remix cluster.
 *  4. The channel is read from the channel row:
 *       - the Subscribe button's description ("Subscribe to <name>."),
 *       - or the row geometry: channel avatar -> exactly one text node -> the
 *         subscriber/view count that sits to its right,
 *       - plus the @handle when the page exposes one.
 *  5. Names and @handles are compared against the allowlist after normalising
 *     away case, spaces, punctuation and a leading "@".
 *
 * Detection is still heuristic, but it only ever blocks a watch page when it has
 * a concrete channel identity. That prevents the false positives the old
 * text-only scanner produced (recommended videos mentioning an allowed channel).
 */
object Detector {

    private const val TAG = "RegainDetector"

    /** A single accessibility node snapshot. */
    data class Node(
        val text: String?,
        val desc: String?,
        val bounds: Rect,
        val viewId: String?,
        val className: String?,
        val visible: Boolean,
        val selected: Boolean
    ) {
        val label: String get() = text ?: desc ?: ""
    }

    data class Channel(val name: String?, val handle: String?)

    data class Verdict(
        val block: Boolean = false,
        val title: String = "",
        val message: String = "",
        val chip: String = "",
        val label: String? = null,
        val key: String = ""
    )

    @Volatile
    var lastDebug: String = "No screen read yet. Open YouTube or Instagram and come back."

    @Volatile
    private var lastRecorded: String = ""

    private val youtube = Enforcer.YOUTUBE
    private val instagram = Enforcer.INSTAGRAM

    // View ids that confirm a YouTube watch page.
    private val watchViewIds = setOf(
        "com.google.android.youtube:id/watch_panel_scrim",
        "com.google.android.youtube:id/watch_player",
        "com.google.android.youtube:id/player_video_heading",
        "com.google.android.youtube:id/player_control_play_pause_replay_button",
        "com.google.android.youtube:id/subscribe_button_label"
    )

    // View ids that only exist inside the Shorts player.
    private val shortsViewIds = setOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_watch_player",
        "com.google.android.youtube:id/reel_watch_fragment_root",
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/reel_comment_button",
        "com.google.android.youtube:id/reel_like_button",
        "com.google.android.youtube:id/reel_dislike_button",
        "com.google.android.youtube:id/reel_share_button",
        "com.google.android.youtube:id/reel_remix_button"
    )

    private val videoPageMentions = listOf(
        "subscribe", "subscribed", "like this video", "dislike this video",
        "save to playlist", "share", "remix", "download"
    )

    fun inspect(root: AccessibilityNodeInfo?, pkg: String, settings: Settings): Verdict {
        if (root == null) return Verdict()
        val nodes = collect(root)
        if (nodes.isEmpty()) {
            lastDebug = "$pkg · empty accessibility tree"
            return Verdict()
        }
        return when (pkg) {
            youtube -> inspectYoutube(pkg, nodes, settings)
            instagram -> inspectInstagram(pkg, nodes, settings)
            else -> Verdict()
        }
    }

    // ---------------------------------------------------------------------
    // YouTube
    // ---------------------------------------------------------------------

    private fun inspectYoutube(pkg: String, nodes: List<Node>, settings: Settings): Verdict {
        val groups = groupRows(nodes)
        val videoPage = isVideoPage(nodes)
        val shorts = isShorts(nodes, videoPage)
        val landscape = (nodes.maxOfOrNull { it.bounds.right } ?: 0) >
            (nodes.maxOfOrNull { it.bounds.bottom } ?: 0) * 1.15
        val channel = if (videoPage || shorts) {
            findChannel(groups, videoPage)
                ?: if (landscape) Channel(findLandscapeName(groups), null) else null
        } else null

        val allowed = channel != null && matchesAllowlist(settings.allowedChannels, channel)

        if (channel != null && (videoPage || shorts)) {
            val recorded = listOfNotNull(
                channel.handle,
                channel.name?.takeIf { it.isNotBlank() && !it.startsWith("@") }
            )
            val recordedKey = recorded.joinToString("|").lowercase(Locale.ROOT)
            if (recorded.isNotEmpty() && recordedKey != lastRecorded) {
                lastRecorded = recordedKey
                Store.noteChannels(recorded)
            }
        }

        lastDebug = buildString {
            append(pkg)
            append(" · video=").append(videoPage)
            append(" shorts=").append(shorts)
            append(" groups=").append(groups.size)
            append('\n')
            append("channel=").append(channel?.name ?: "-")
            append(" handle=").append(channel?.handle ?: "-")
            append(" allowlist=").append(settings.youtubeMode)
            append(" matched=").append(allowed)
            append('\n')
            append(nodes.take(90).joinToString(" | ") { n ->
                buildString {
                    append(n.label.take(46))
                    if (n.viewId != null && n.viewId.contains("youtube:id/")) {
                        append('[').append(n.viewId.substringAfter("youtube:id/")).append(']')
                    }
                }
            }.take(1100))
        }
        android.util.Log.d(
            TAG,
            "$pkg video=$videoPage shorts=$shorts channel=${channel?.name} " +
                "handle=${channel?.handle} matched=$allowed groups=${groups.size}"
        )

        if (settings.youtubeMode == "allowlist") {
            // A channel that is explicitly on the list is always allowed. This includes
            // its Shorts, because the list is the user's definition of "study content".
            if (allowed) return Verdict()

            // Shorts are never allowed when no allowed channel can be identified:
            // they are the one surface where the endless feed is the whole point.
            if (shorts) {
                val label = channel?.name ?: channel?.handle
                return Verdict(
                    block = true,
                    title = "Not on your allowlist",
                    message = if (label != null)
                        "“$label” isn't one of the channels you allowed for study. Only your chosen channels open while the allowlist is on."
                    else
                        "This Short isn't from one of your allowed channels. Only your chosen study channels open while the allowlist is on.",
                    chip = "YOUTUBE · ALLOWLIST",
                    label = label ?: "Shorts blocked",
                    key = "content:yt-channel:${safeKey(label ?: "unknown")}"
                )
            }

            // On a normal watch page we only block when the channel is known.
            // This is the original app's behaviour and avoids walling an allowed
            // video because accessibility did not expose the channel row yet.
            if (videoPage && channel != null) {
                val label = channel.name ?: channel.handle
                return Verdict(
                    block = true,
                    title = "Not on your allowlist",
                    message = if (label != null)
                        "“$label” is not one of the channels you allowed for study. Only your chosen channels open while the allowlist is on."
                    else
                        "This channel isn't on your YouTube allowlist. Add the channels you trust in Regain → Focus Guard.",
                    chip = "YOUTUBE · ALLOWLIST",
                    label = label,
                    key = "content:yt-channel:${safeKey(label ?: "unknown")}"
                )
            }

            return Verdict()
        }

        if (settings.blockShorts && shorts) {
            return Verdict(
                block = true,
                title = "Shorts are off",
                message = "You asked Regain to keep endless Shorts out of your study time. The rest of YouTube is still here when you need it.",
                chip = "YOUTUBE · SHORTS",
                label = "Shorts player detected",
                key = "content:yt-shorts"
            )
        }
        return Verdict()
    }

    /**
     * Find the channel row.
     *
     * The original app scans every row for:
     *  - "Action menu" (a bottom sheet is open),
     *  - the Subscribe button description,
     *  - the avatar/name/subscriber-count geometry,
     *  - a bare @handle.
     */
    private fun findChannel(groups: List<List<Node>>, videoPage: Boolean): Channel? {
        var name: String? = null
        var nameIndex = -1
        var handle: String? = null
        var handleOnly: Channel? = null

        for ((index, group) in groups.withIndex()) {
            if (group.any { it.label.equals("Action menu", true) }) continue

            val groupHandle = findHandle(group)
            if (handle == null && groupHandle != null &&
                (name == null || index <= nameIndex + 4)
            ) {
                handle = groupHandle
            }

            if (name == null) {
                val fromSubscribe = findSubscribeName(group)
                val fromRow = findChannelFromRow(group)
                val fromLegacy = findLegacyName(group)
                val candidate = cleanChannelName(fromSubscribe)
                    ?: cleanChannelName(fromLegacy)
                    ?: cleanChannelName(fromRow)
                if (candidate != null &&
                    (videoPage || groupHandle != null || fromSubscribe != null || fromRow != null)
                ) {
                    name = candidate
                    nameIndex = index
                }
            }
            if (name != null && handle != null) break
            if (name == null && groupHandle != null && handleOnly == null) {
                handleOnly = Channel(null, groupHandle)
            }
        }

        // Watch pages usually show the @handle in the description row just below
        // the channel row. Only trust a handle in the next few rows, never one
        // from the recommendation feed.
        if (name != null && handle == null) {
            val end = minOf(groups.lastIndex, nameIndex + 4)
            for (i in nameIndex + 1..end) {
                val group = groups[i]
                val hasViews = group.any { it.label.contains("view", true) }
                val hasMore = group.any { node ->
                    val value = node.label.lowercase(Locale.ROOT)
                    value.contains("...more") || value.contains("… more") || value.contains("…more")
                }
                if (hasViews && hasMore) {
                    handle = findHandle(group)
                    if (handle != null) break
                }
            }
        }

        if (name != null) return Channel(name, handle)
        if (handle != null) return Channel(null, handle)
        return handleOnly
    }

    /**
     * YouTube puts the channel name in the Subscribe button's accessibility label:
     * "Subscribe to Physics Wallah." / "Unsubscribe from Physics Wallah."
     * This is the most reliable signal on both the watch page and Shorts.
     */
    private fun findSubscribeName(group: List<Node>): String? {
        for (node in group) {
            for (raw in listOfNotNull(node.text, node.desc)) {
                val value = raw.trim()
                if (value.length > 90) continue
                val match = Regex(
                    "(?i)^(?:subscribe(?:d)?\\s+to|unsubscribe\\s+from)\\s+(.+?)[.]?$"
                ).find(value) ?: continue
                val name = match.groupValues[1].trim().removeSuffix(".")
                if (name.length in 2..60 && !name.startsWith("@")) return name
            }
        }
        return null
    }

    /**
     * Row geometry, ported from the original:
     *   avatar (ImageView) -> the single text node -> subscriber/view count
     * The count node is the numeric node on the right of the row. If there is
     * exactly one text node between the avatar and the count, that node is the
     * channel name.
     */
    private fun findChannelFromRow(group: List<Node>): String? {
        val visible = group.filter { it.visible && !it.bounds.isEmpty }
        if (visible.size < 3) return null

        val counts = visible.filter { node ->
            val value = node.label.trim().lowercase(Locale.ROOT)
            if (value.isEmpty()) return@filter false
            val words = value.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size > 4) return@filter false
            if (!Regex("^[-+]?\\d+(\\.\\d+)?").containsMatchIn(value)) return@filter false
            // Durations / timestamps and "2 days ago" are not subscriber counts.
            listOf("hour", "minute", "second", "ago", "day", "week", "month", "year")
                .none { value.contains(it) }
        }
        val count = counts.firstOrNull { it.label.contains("subscriber", true) }
            ?: counts.firstOrNull { it.label.contains("view", true) }
            ?: counts.firstOrNull()
            ?: return null

        val avatars = visible.filter {
            it.className == "android.widget.ImageView" &&
                it.bounds.width() > 0 &&
                it.bounds.right <= count.bounds.left
        }
        val avatar = avatars.maxByOrNull { it.bounds.right } ?: return null

        val between = visible.filter { node ->
            node.text != null &&
                node.bounds.left >= avatar.bounds.right &&
                node.bounds.right <= count.bounds.left &&
                !node.label.equals("Subscribe", true) &&
                !node.label.equals("Subscribed", true) &&
                !node.label.equals("Join", true) &&
                !node.label.contains("subscriber", true) &&
                !node.label.contains("view", true) &&
                !node.label.matches(Regex("^[\\d.,:\\sKMBkmb+\\-]+$"))
        }
        if (between.size != 1) return null
        return between.first().text
    }

    /**
     * The bare handle YouTube shows on Shorts and in expanded descriptions:
     * text and content description are identical, start with "@", and contain
     * no spaces. The original also required a views + "...more" row; we accept
     * a button/avatar row so Shorts are covered.
     */
    private fun findHandle(group: List<Node>): String? {
        val handlePattern = Regex("^@\\S{1,50}$")
        val hasButton = group.any { it.className == "android.widget.Button" }
        val hasAvatar = group.any { it.className == "android.widget.ImageView" }
        val hasSubscribe = group.any { it.label.contains("subscribe", true) }
        val hasViews = group.any { it.label.contains("view", true) }
        val hasMore = group.any {
            val value = it.label.lowercase(Locale.ROOT)
            value.contains("...more") || value.contains("… more") || value.contains("…more")
        }
        val confirmed = hasSubscribe || hasViews || hasMore || (hasButton && hasAvatar)
        if (!confirmed) return null
        for (node in group) {
            val text = node.text?.trim()?.removeSuffix(".") ?: continue
            val desc = node.desc?.trim()?.removeSuffix(".") ?: continue
            if (text == desc && handlePattern.matches(text)) return text
        }
        // Some builds expose the handle only as text (no description).
        return group.firstOrNull {
            val text = it.text?.trim()?.removeSuffix(".") ?: return@firstOrNull false
            handlePattern.matches(text)
        }?.text?.trim()?.removeSuffix(".")
    }

    /** Last-resort name: the text immediately before the Subscribe button. */
    private fun findLegacyName(group: List<Node>): String? {
        val index = group.indexOfFirst {
            it.label.equals("Subscribe", true) || it.label.equals("Subscribed", true)
        }
        if (index <= 0) return null
        val start = maxOf(0, index - 5)
        return group.subList(start, index).asReversed().firstNotNullOfOrNull { node ->
            cleanChannelName(node.text)
        }
    }

    /**
     * Landscape watch pages put the channel name in the same row as
     * `player_video_heading`; the original takes the last text node of that row.
     */
    private fun findLandscapeName(groups: List<List<Node>>): String? {
        for (group in groups) {
            if (group.none { it.viewId == "com.google.android.youtube:id/player_video_heading" }) continue
            val name = group.asReversed().firstOrNull { !it.text.isNullOrBlank() }?.text?.trim() ?: continue
            if (name.length in 2..50 && !isTimestamp(name) && cleanChannelName(name) != null) return name
        }
        return null
    }

    private fun isVideoPage(nodes: List<Node>): Boolean {
        if (nodes.any { it.viewId in watchViewIds }) return true
        val mentions = nodes.count { node ->
            val label = node.label.lowercase(Locale.ROOT)
            videoPageMentions.any { label == it || label.startsWith("$it ") || label.contains(it) }
        }
        val hasSubscribe = nodes.any {
            it.label.equals("Subscribe", true) || it.label.equals("Subscribed", true)
        }
        // A title + action row without any subscribe/reel signal is the home feed.
        return hasSubscribe && mentions >= 3
    }

    private fun isShorts(nodes: List<Node>, videoPage: Boolean): Boolean {
        val reelView = nodes.any { it.viewId in shortsViewIds }
        if (reelView) return true

        val selectedShorts = nodes.any {
            it.selected && (it.label.contains("shorts", true) || it.label.contains("reel", true))
        }
        if (selectedShorts) return true

        // Do not mistake the "Shorts" shelf on a watch page for the Shorts player.
        if (videoPage) return false

        val shortsWord = nodes.any { it.label.equals("Shorts", true) }
        val remix = nodes.any {
            it.label.contains("remix", true) ||
                it.label.contains("use this sound", true) ||
                it.label.contains("original audio", true)
        }
        val like = nodes.any { it.label.contains("like", true) }
        val share = nodes.any { it.label.contains("share", true) }
        val comment = nodes.any { it.label.contains("comment", true) }
        return remix && like && share &&
            (shortsWord || comment || nodes.any { it.label.contains("dislike", true) })
    }

    // ---------------------------------------------------------------------
    // Instagram
    // ---------------------------------------------------------------------

    private fun inspectInstagram(pkg: String, nodes: List<Node>, settings: Settings): Verdict {
        val reelView = nodes.any {
            val id = it.viewId ?: return@any false
            id.contains("clips_viewer") || id.contains("reel_viewer") || id.contains("clips_video")
        }
        val selectedReels = nodes.any {
            it.selected && (it.label.contains("reel", true) || it.label.contains("clips", true))
        }
        val reelsWord = nodes.any { it.label.equals("Reels", true) || it.label.equals("Clips", true) }
        val audio = nodes.any {
            it.label.contains("original audio", true) || it.label.equals("Audio", true) ||
                it.label.contains("audio ·", true) || it.label.contains("music", true)
        }
        val like = nodes.any { it.label.contains("like", true) }
        val share = nodes.any { it.label.contains("share", true) }
        val save = nodes.any { it.label.contains("save", true) }
        val comment = nodes.any { it.label.contains("comment", true) }

        val reels = reelView || selectedReels ||
            (reelsWord && (audio || save) && like && (share || comment))

        lastDebug = buildString {
            append(pkg).append(" · reels=").append(reels)
            append(" selectedReels=").append(selectedReels)
            append(" audio=").append(audio)
            append('\n').append(nodes.take(80).joinToString(" | ") { it.label.take(46) }.take(900))
        }

        if (settings.blockReels && reels) {
            return Verdict(
                block = true,
                title = "Reels are off",
                message = "Scrolling is paused. Messages and the people you actually follow are still a tap away — but the infinite feed can wait.",
                chip = "INSTAGRAM · REELS",
                label = "Reels detected",
                key = "content:ig-reels"
            )
        }
        return Verdict()
    }

    // ---------------------------------------------------------------------
    // Allowlist matching
    // ---------------------------------------------------------------------

    private fun matchesAllowlist(allowed: Set<String>, channel: Channel): Boolean {
        if (allowed.isEmpty()) return false
        val detected = buildList {
            channel.name?.let { add(it) }
            channel.handle?.let { add(it) }
        }.flatMap { variants(it) }.map { normalize(it) }.filter { it.isNotEmpty() }
        if (detected.isEmpty()) return false

        return allowed.any { entry ->
            val key = normalize(entry)
            if (key.isEmpty()) return@any false
            detected.any { value ->
                value == key || (minOf(value.length, key.length) >= 4 &&
                    (value.contains(key) || key.contains(value)))
            }
        }
    }

    /**
     * "Physics Wallah - Topic" and "Physics Wallah Official" are the same channel
     * for allowlist purposes.
     */
    private fun variants(name: String): List<String> {
        val raw = name.trim()
        return listOf(
            raw,
            raw.removeSuffix(" - Topic"),
            raw.removeSuffix(" - topic"),
            raw.removeSuffix(" Official"),
            raw.removeSuffix(" official"),
            raw.removeSuffix(" Music"),
            raw.removeSuffix(" music")
        ).distinct()
    }

    private fun normalize(value: String): String {
        var s = value.trim()
        if (s.contains("youtube.com/") || s.contains("youtu.be/")) {
            s = s.substringAfterLast('/').substringBefore('?').substringBefore('&')
        }
        if (s.startsWith("@")) s = s.substring(1)
        return s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    private fun safeKey(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(40).ifEmpty { "unknown" }

    private fun cleanChannelName(raw: String?): String? {
        val value = raw?.trim()?.removeSuffix(".") ?: return null
        if (value.length !in 2..60) return null
        if (value.equals("Subscribe", true) || value.equals("Subscribed", true)) return null
        if (value.equals("Join", true) || value.equals("Action menu", true)) return null
        if (value.contains("subscriber", true)) return null
        if (value.matches(Regex("^[\\d.,:\\sKMBkmb+\\-]+$"))) return null
        if (value == "�?�" || value == "⋯" || value == "…") return null
        return value
    }

    private fun isTimestamp(value: String): Boolean {
        val v = value.trim()
        return Regex("^/?\\s*\\d{1,2}:\\d{2}(:\\d{2})?$").matches(v)
    }

    // ---------------------------------------------------------------------
    // Node collection and row grouping
    // ---------------------------------------------------------------------

    private fun collect(root: AccessibilityNodeInfo): List<Node> {
        val out = ArrayList<Node>(320)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val rect = Rect()
        while (queue.isNotEmpty() && visited < 2000) {
            val node = queue.removeFirst()
            visited++
            node.getBoundsInScreen(rect)
            val bounds = Rect(rect)
            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val viewId = node.viewIdResourceName
            val className = node.className?.toString()
            // Keep text nodes, view-id anchors (watch_panel_scrim) and images
            // (the channel avatar used by the row geometry).
            if (text != null || desc != null || viewId != null || className == "android.widget.ImageView") {
                if (!bounds.isEmpty) {
                    out.add(
                        Node(
                            text = text,
                            desc = desc,
                            bounds = bounds,
                            viewId = viewId,
                            className = className,
                            visible = node.isVisibleToUser,
                            selected = node.isSelected
                        )
                    )
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return out
    }

    /**
     * Group nodes into rows by vertical midpoint. Consecutive midpoints within
     * 10 px are merged, keyed by the lowest (bottom-most) midpoint, exactly like
     * the original `getMergedHashMap`.
     */
    private fun groupRows(nodes: List<Node>): List<List<Node>> {
        if (nodes.isEmpty()) return emptyList()
        val byMid = TreeMap<Int, MutableList<Node>>()
        for (node in nodes) {
            val mid = node.bounds.centerY()
            byMid.getOrPut(mid) { mutableListOf() }.add(node)
        }
        if (byMid.size < 2) return byMid.values.toList()

        val keys = byMid.keys.toList()
        val merged = ArrayList<List<Node>>(keys.size)
        var bucket = mutableListOf(keys[0])

        fun flush() {
            val list = ArrayList<Node>()
            for (key in bucket) byMid[key]?.let { list.addAll(it) }
            merged.add(list)
        }

        for (i in 1 until keys.size) {
            if (keys[i] - keys[i - 1] <= 10) {
                bucket.add(keys[i])
            } else {
                flush()
                bucket = mutableListOf(keys[i])
            }
        }
        flush()
        return merged
    }
}
