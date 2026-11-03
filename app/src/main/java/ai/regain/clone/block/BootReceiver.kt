package ai.regain.clone.block

import ai.regain.clone.data.Store
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Store.init(context)
        if (Store.settings.blockingEnabled) {
            WatchdogService.sync(context)
        }
    }
}
