package ai.regain.clone.ui

import ai.regain.clone.block.Detector
import ai.regain.clone.block.Enforcer
import ai.regain.clone.block.Perms
import ai.regain.clone.block.WatchdogService
import ai.regain.clone.data.Store
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AppEntry(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val icon: ImageBitmap?
)

@Composable
fun BlockScreen(perms: PermState, onPermissionsChanged: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val settings = Store.settings
    var showApps by rememberSaveable { mutableStateOf(false) }
    var newChannel by remember { mutableStateOf("") }
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    var debugText by remember { mutableStateOf(Detector.lastDebug) }

    if (showApps) {
        AppPickerScreen(onBack = { showApps = false })
        return
    }

    LaunchedEffect(debugOpen) {
        while (debugOpen) {
            debugText = Detector.lastDebug
            delay(1500)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(
            eyebrow = "FOCUS GUARD",
            title = "Make distraction loud",
            subtitle = "Block apps, cut Reels and Shorts, or keep YouTube to the handful of channels you actually study with."
        )

        Spacer(Modifier.height(16.dp))

        GlassCard(
            background = if (settings.blockingEnabled) p.accent.copy(alpha = 0.08f) else p.card,
            borderColor = if (settings.blockingEnabled) p.accent.copy(alpha = 0.35f) else p.cardBorder
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Shield)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Focus Guard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text)
                    Text(
                        if (settings.blockingEnabled) "Armed and watching the foreground app" else "Off — nothing is being blocked",
                        fontSize = 11.5.sp,
                        color = if (settings.blockingEnabled) p.accent else p.muted
                    )
                }
                Switch(
                    checked = settings.blockingEnabled,
                    onCheckedChange = {
                        Store.update { s -> s.copy(blockingEnabled = it) }
                        WatchdogService.sync(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = p.onAccent,
                        checkedTrackColor = p.accent,
                        uncheckedThumbColor = p.muted,
                        uncheckedTrackColor = p.card,
                        uncheckedBorderColor = p.cardBorder
                    )
                )
            }
            val bypass = Enforcer.bypassRemainingMinutes(context)
            if (bypass > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Temporary unlock active — $bypass min left.",
                    fontSize = 11.5.sp,
                    color = p.danger
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Permissions", "Android needs these to see the foreground app")
        Spacer(Modifier.height(8.dp))

        PermissionRow(
            title = "Display over other apps",
            subtitle = "Draws the focus wall on top of a blocked app",
            granted = perms.overlay,
            icon = Icons.Filled.Layers
        ) { Perms.openOverlaySettings(context) }
        PermissionRow(
            title = "Accessibility service",
            subtitle = "Detects Reels, Shorts, channels and app switches",
            granted = perms.accessibility,
            icon = Icons.Filled.Accessibility
        ) { Perms.openAccessibilitySettings(context) }
        PermissionRow(
            title = "Usage access",
            subtitle = "Backup foreground detection if accessibility is off",
            granted = perms.usage,
            icon = Icons.Filled.Visibility
        ) { Perms.openUsageSettings(context) }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Content filters", "Screen-level rules inside an app")
        Spacer(Modifier.height(8.dp))

        GlassCard {
            ToggleRow(
                title = "Block Instagram Reels",
                subtitle = "Home feed stays usable, the Reels tab does not",
                checked = settings.blockReels,
                icon = Icons.Filled.Shield,
                accent = p.danger
            ) { on ->
                Store.update { it.copy(blockReels = on) }
                WatchdogService.sync(context)
            }
            DividerLine()
            ToggleRow(
                title = "Block YouTube Shorts",
                subtitle = "Long-form videos still play normally",
                checked = settings.blockShorts,
                icon = Icons.Filled.Shield,
                accent = ColorAmber
            ) { on ->
                Store.update { it.copy(blockShorts = on) }
                WatchdogService.sync(context)
            }
            DividerLine()
            Text("YouTube mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(3.dp))
            Text(
                when (settings.youtubeMode) {
                    "allowlist" -> "Only these channels open: browse away, playback is filtered."
                    "block_all" -> "The whole app is blocked, including search and the home feed."
                    else -> "No filter. Everything on YouTube opens."
                },
                fontSize = 11.5.sp,
                color = p.muted,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "off" to "Off",
                    "allowlist" to "Allowlist only",
                    "block_all" to "Block all"
                ).forEach { (id, label) ->
                    Pill(text = label, selected = settings.youtubeMode == id) {
                        Store.update { it.copy(youtubeMode = id) }
                        WatchdogService.sync(context)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Shield, tint = p.accentAlt, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("YouTube allowlist", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                    Text("${settings.allowedChannels.size} channel${if (settings.allowedChannels.size == 1) "" else "s"} allowed", fontSize = 11.sp, color = p.muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newChannel,
                    onValueChange = { newChannel = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Channel name, e.g. Physics Wallah", fontSize = 12.sp, color = p.muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = p.accent,
                        unfocusedBorderColor = p.cardBorder,
                        focusedTextColor = p.text,
                        unfocusedTextColor = p.text,
                        cursorColor = p.accent
                    )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (newChannel.isBlank()) p.cardBorder else p.accent)
                        .clickable(enabled = newChannel.isNotBlank()) {
                            val name = newChannel.trim()
                            if (name.isNotEmpty()) {
                                Store.update { it.copy(allowedChannels = it.allowedChannels + name) }
                                newChannel = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add channel",
                        tint = if (newChannel.isBlank()) p.muted else p.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (settings.allowedChannels.isEmpty()) {
                Text(
                    "Add every channel you're allowed to study from. While the allowlist is active, any other channel's watch page gets walled off.",
                    fontSize = 11.5.sp,
                    color = p.muted,
                    lineHeight = 16.sp
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    settings.allowedChannels.forEach { channel ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(p.accentAlt.copy(alpha = 0.15f))
                                .clickable {
                                    Store.update { it.copy(allowedChannels = it.allowedChannels - channel) }
                                }
                                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(channel, fontSize = 11.5.sp, color = p.accentAlt, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Close, null, tint = p.accentAlt, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }

            if (Store.recentChannels.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Detected on screen recently", fontSize = 11.sp, color = p.muted)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Store.recentChannels
                        .filter { ch -> settings.allowedChannels.none { it.equals(ch, true) } }
                        .forEach { ch ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(p.card)
                                    .clickable {
                                        Store.update { it.copy(allowedChannels = it.allowedChannels + ch) }
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Add, null, tint = p.accent, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(ch, fontSize = 11.sp, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(
            title = "Blocked apps",
            subtitle = if (settings.blockedApps.isEmpty()) "Nothing blocked yet" else "${settings.blockedApps.size} apps walled off",
            action = "Manage",
            onAction = { showApps = true }
        )
        Spacer(Modifier.height(8.dp))

        if (settings.blockedApps.isEmpty()) {
            GlassCard(Modifier.clickable { showApps = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Apps, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pick the usual suspects", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                        Text("Instagram, X, Reddit, games — whatever steals the hour.", fontSize = 11.sp, color = p.muted)
                    }
                }
            }
        } else {
            GlassCard {
                settings.blockedApps.take(6).forEachIndexed { index, pkg ->
                    if (index > 0) DividerLine()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Store.update { s -> s.copy(blockedApps = s.blockedApps - pkg) }
                            }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Enforcer.appLabel(context, pkg), fontSize = 13.sp, color = p.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Delete, null, tint = p.muted, modifier = Modifier.size(15.dp))
                    }
                }
                if (settings.blockedApps.size > 6) {
                    DividerLine()
                    Text(
                        "and ${settings.blockedApps.size - 6} more",
                        fontSize = 11.5.sp,
                        color = p.muted,
                        modifier = Modifier.clickable { showApps = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        GlassCard {
            Row(
                Modifier.fillMaxWidth().clickable { debugOpen = !debugOpen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Filled.Accessibility, tint = p.muted, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Detection readout", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Text("See exactly what Regain sees on screen", fontSize = 10.5.sp, color = p.muted)
                }
                Icon(
                    if (debugOpen) Icons.Filled.Refresh else Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = p.muted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            debugText = Detector.lastDebug
                            debugOpen = !debugOpen
                        }
                )
            }
            if (debugOpen) {
                Spacer(Modifier.height(10.dp))
                Text(
                    debugText,
                    fontSize = 10.sp,
                    color = p.muted,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.bgTop.copy(alpha = 0.6f))
                        .padding(10.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tip: open the Reels tab or a Short, then come back here. If the readout says reels=false while you're scrolling Reels, your Instagram build exposes different labels — tell me and I'll tune the rule.",
                    fontSize = 10.sp,
                    color = p.muted,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    GlassCard(Modifier.clickable { onClick() }, padding = 13.dp, shapeRadius = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = if (granted) p.accent else p.danger, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Text(subtitle, fontSize = 10.5.sp, color = p.muted, lineHeight = 14.sp)
            }
            Text(
                if (granted) "ON" else "FIX",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (granted) p.accent else p.danger
            )
        }
    }
}

@Composable
private fun AppPickerScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var includeSystem by rememberSaveable { mutableStateOf(Store.settings.showSystemApps) }
    var query by rememberSaveable { mutableStateOf("") }
    val apps by rememberInstalledApps(includeSystem)
    val settings = Store.settings

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, true) || it.pkg.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
            }
            Column(Modifier.weight(1f)) {
                Text("Block apps", fontSize = 22.sp, fontWeight = FontWeight.Black, color = p.text)
                Text(
                    "${settings.blockedApps.size} selected · ${filtered.size} installed",
                    fontSize = 11.sp,
                    color = p.muted
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps", fontSize = 13.sp, color = p.muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = p.muted, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.accent,
                unfocusedBorderColor = p.cardBorder,
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
                cursorColor = p.accent
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show system apps", fontSize = 12.sp, color = p.muted, modifier = Modifier.weight(1f))
            Switch(
                checked = includeSystem,
                onCheckedChange = {
                    includeSystem = it
                    Store.update { s -> s.copy(showSystemApps = it) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = p.onAccent,
                    checkedTrackColor = p.accent,
                    uncheckedThumbColor = p.muted,
                    uncheckedTrackColor = p.card,
                    uncheckedBorderColor = p.cardBorder
                )
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.pkg }) { app ->
                val blocked = settings.blockedApps.contains(app.pkg)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Store.update { s ->
                                if (blocked) s.copy(blockedApps = s.blockedApps - app.pkg)
                                else s.copy(blockedApps = s.blockedApps + app.pkg)
                            }
                            WatchdogService.sync(context)
                        }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.cardBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(app.label.take(1).uppercase(), color = p.text, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 13.5.sp, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.pkg,
                            fontSize = 9.5.sp,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = blocked,
                        onCheckedChange = {
                            Store.update { s ->
                                if (it) s.copy(blockedApps = s.blockedApps + app.pkg)
                                else s.copy(blockedApps = s.blockedApps - app.pkg)
                            }
                            WatchdogService.sync(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = p.onAccent,
                            checkedTrackColor = p.accent,
                            uncheckedThumbColor = p.muted,
                            uncheckedTrackColor = p.card,
                            uncheckedBorderColor = p.cardBorder
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberInstalledApps(includeSystem: Boolean): androidx.compose.runtime.State<List<AppEntry>> {
    val context = LocalContext.current
    return produceState(
        initialValue = emptyList<AppEntry>(),
        includeSystem
    ) {
        value = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val launchable = pm.getInstalledApplications(0).filter { info ->
                info.packageName != context.packageName &&
                    pm.getLaunchIntentForPackage(info.packageName) != null
            }
            launchable
                .map { info ->
                    val system = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val icon = runCatching {
                        pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                    }.getOrNull()
                    AppEntry(
                        pkg = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        system = system,
                        icon = icon
                    )
                }
                .filter { includeSystem || !it.system }
                .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
}

private val ColorAmber = androidx.compose.ui.graphics.Color(0xFFFFD166)
