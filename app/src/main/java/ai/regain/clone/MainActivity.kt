package ai.regain.clone

import ai.regain.clone.block.Perms
import ai.regain.clone.block.WatchdogService
import ai.regain.clone.data.Store
import ai.regain.clone.focus.FocusEngine
import ai.regain.clone.focus.FocusService
import ai.regain.clone.ui.BlockScreen
import ai.regain.clone.ui.FocusScreen
import ai.regain.clone.ui.HomeScreen
import ai.regain.clone.ui.LocalPalette
import ai.regain.clone.ui.PermState
import ai.regain.clone.ui.RegainTheme
import ai.regain.clone.ui.RoomsScreen
import ai.regain.clone.ui.ScreenBackground
import ai.regain.clone.ui.SettingsScreen
import ai.regain.clone.ui.StatsScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    private val permTick = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        FocusEngine.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) permTick.intValue++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            RegainRoot(permissionVersion = permTick.intValue)
        }
    }

    override fun onResume() {
        super.onResume()
        ai.regain.clone.block.OverlayController.hide()
        WatchdogService.sync(this)
        FocusService.ensure(this)
    }
}

@Composable
fun RegainRoot(permissionVersion: Int) {
    val context = LocalContext.current
    val settings = Store.settings
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showRooms by rememberSaveable { mutableStateOf(false) }
    var localRefresh by remember { mutableIntStateOf(0) }

    val perms = remember(permissionVersion, localRefresh) {
        PermState(
            overlay = Perms.hasOverlay(context),
            usage = Perms.hasUsageAccess(context),
            accessibility = Perms.hasAccessibility(context),
            notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val refresh = {
        localRefresh++
        WatchdogService.sync(context)
    }

    RegainTheme(settings.theme) {
        Box(Modifier.fillMaxSize()) {
            ScreenBackground()
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    BottomNav(selected = tab) { index -> tab = index }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when {
                        showRooms -> RoomsScreen(
                            onBack = { showRooms = false },
                            onJoin = { minutes, subject ->
                                FocusEngine.startFocus(minutes, subject)
                                showRooms = false
                                tab = 1
                            }
                        )

                        tab == 0 -> HomeScreen(
                            perms = perms,
                            onStartFocus = { minutes, subject ->
                                FocusEngine.startFocus(minutes, subject)
                                tab = 1
                            },
                            onOpenFocus = { tab = 1 },
                            onOpenGuard = { tab = 2 },
                            onOpenRooms = { showRooms = true },
                            onOpenStats = { tab = 3 },
                            onOpenSettings = { tab = 4 }
                        )

                        tab == 1 -> FocusScreen(perms = perms, onOpenGuard = { tab = 2 })
                        tab == 2 -> BlockScreen(perms = perms, onPermissionsChanged = refresh)
                        tab == 3 -> StatsScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val entries = listOf(
        Triple(Icons.Outlined.Home, "Home", 0),
        Triple(Icons.Outlined.Timer, "Focus", 1),
        Triple(Icons.Outlined.Shield, "Guard", 2),
        Triple(Icons.Outlined.BarChart, "Stats", 3),
        Triple(Icons.Outlined.Tune, "Settings", 4)
    )
    Surface(color = p.bgBottom.copy(alpha = 0.96f), tonalElevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            entries.forEach { (icon: ImageVector, label: String, index: Int) ->
                val active = selected == index
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (active) p.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (active) p.accent else p.muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        fontSize = 9.sp,
                        color = if (active) p.accent else p.muted,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
