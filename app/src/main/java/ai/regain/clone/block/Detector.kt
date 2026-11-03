package ai.regain.clone.block

import ai.regain.clone.data.Settings
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Heuristic screen reader. Android does not expose a public "which screen am I on"
 * API, so we look at the accessibility tree the same way mainstream blockers do.
 * The debug string is surfaced in the app so detection can be verified on a device.
 */
object Detector {

    data class Item(val text: String, val selected: Boolean)

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

    fun inspect(root: AccessibilityNodeInfo?, pkg: String, settings: Settings): Verdict {
        if (root == null) return Verdict()
        val items = collect(root)
        if (items.isEmpty()) {
            lastDebug = "$pkg · empty accessibility tree"
            return Verdict()
        }
        return when (pkg) {
            Enforcer.YOUTUBE -> inspectYoutube(pkg, items, settings)
            Enforcer.INSTAGRAM -> inspectInstagram(pkg, items, settings)
            else -> Verdict()
        }
    }

    private fun inspectYoutube(pkg: String, items: List<Item>, settings: Settings): Verdict {
        val hasSubscribe = items.any { it.text.equals("Subscribe", true) || it.text.equals("Subscribed", true) }
        val selectedShorts = items.any { it.selected && it.text.contains("shorts", true) }
        val shortsWord = items.any { it.text.equals("Shorts", true) }
        val remix = items.any {
            it.text.contains("remix", true) || it.text.contains("use this sound", true) ||
                it.text.contains("original audio", true) || it.text.contains("shorts", true) && it.text.length > 40
        }
        val hasLike = items.any { it.text.equals("Like", true) || it.text.startsWith("Like this", true) || it.text.contains("like this video", true) }
        val hasShare = items.any { it.text.contains("share", true) }
        val hasComments = items.any { it.text.contains("comment", true) }

        // Remix / "use this sound" only exist in the Shorts player, so they are the
        // strongest signal available when the bottom navigation is hidden.
        val shorts = selectedShorts ||
            (remix && hasLike && hasShare && !hasSubscribe) ||
            (shortsWord && remix) ||
            (shortsWord && hasLike && hasShare && hasComments && !hasSubscribe)

        val candidates = channelCandidates(items)
        val allowed = settings.allowedChannels
        // Match the channel header (the text right before Subscribe), not the whole
        // screen: recommendations can mention an allowed channel by coincidence.
        val primary = candidates.firstOrNull()
        val matchedChannel = allowed.firstOrNull { name ->
            val n = normalize(name)
            val c = primary?.let { normalize(it) } ?: ""
            n.isNotEmpty() && c.isNotEmpty() &&
                (c == n || c.contains(n) || (c.length >= 4 && n.contains(c)))
        }
        val watchPage = hasSubscribe || (hasLike && (hasShare || hasComments))

        var channelVerdict = Verdict()
        if (settings.youtubeMode == "allowlist") {
            if (!shorts && watchPage && matchedChannel == null) {
                val guess = primary
                recordChannel(guess)
                channelVerdict = Verdict(
                    block = true,
                    title = "Not on your allowlist",
                    message = if (guess != null)
                        "“$guess” is not one of the channels you allowed for study. Only your chosen channels open during focus."
                    else
                        "This channel isn't on your YouTube allowlist. Add channels you trust in Regain → Focus Guard → YouTube allowlist.",
                    chip = "YOUTUBE · ALLOWLIST",
                    label = guess,
                    key = "content:yt-channel:${guess ?: "unknown"}"
                )
            } else if (shorts && hasSubscribe && matchedChannel == null) {
                recordChannel(candidates.firstOrNull())
            }
        }

        lastDebug = buildString {
            append(pkg).append(" · shorts=").append(shorts)
            append(" subscribe=").append(hasSubscribe)
            append(" allowlist=").append(settings.youtubeMode)
            append(" matched=").append(matchedChannel ?: "-")
            append(" candidates=").append(candidates.take(3))
            append('\n').append(items.take(60).joinToString(" | ") { it.text }.take(900))
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
        return channelVerdict
    }

    private fun inspectInstagram(pkg: String, items: List<Item>, settings: Settings): Verdict {
        val selectedReels = items.any { it.selected && (it.text.contains("reel", true) || it.text.contains("clips", true)) }
        val reelsWord = items.any { it.text.equals("Reels", true) || it.text.equals("Clips", true) }
        val audio = items.any {
            it.text.contains("original audio", true) || it.text.equals("Audio", true) ||
                it.text.contains("audio ·", true) || it.text.contains("music", true)
        }
        val hasLike = items.any { it.text.contains("like", true) }
        val hasShare = items.any { it.text.contains("share", true) }
        val hasSave = items.any { it.text.contains("save", true) }
        val hasComment = items.any { it.text.contains("comment", true) }

        val reels = selectedReels ||
            (reelsWord && (audio || hasSave) && hasLike && (hasShare || hasComment))

        lastDebug = buildString {
            append(pkg).append(" · reels=").append(reels)
            append(" selectedReels=").append(selectedReels)
            append(" audio=").append(audio)
            append('\n').append(items.take(60).joinToString(" | ") { it.text }.take(900))
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

    private fun channelCandidates(items: List<Item>): List<String> {
        val idx = items.indexOfFirst {
            it.text.equals("Subscribe", true) || it.text.equals("Subscribed", true)
        }
        if (idx <= 0) return emptyList()
        val from = maxOf(0, idx - 8)
        return items.subList(from, idx)
            .map { it.text.trim() }
            .filter { text ->
                text.length in 2..48 &&
                    !text.matches(Regex("^[\\d.,:\\sKMBkmb+\\-]+$")) &&
                    !text.contains("subscriber", true) &&
                    !text.contains("view", true) &&
                    !text.equals("•", true) &&
                    !text.equals("Join", true)
            }
            .takeLast(3)
            .reversed()
    }

    private fun collect(root: AccessibilityNodeInfo): List<Item> {
        val out = ArrayList<Item>(220)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 900) {
            val node = queue.removeFirst()
            visited++
            val selected = node.isSelected
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(Item(it, selected)) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { out.add(Item(it, selected)) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return out
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun recordChannel(name: String?) {
        if (name.isNullOrBlank()) return
        ai.regain.clone.data.Store.noteChannels(listOf(name))
    }
}
