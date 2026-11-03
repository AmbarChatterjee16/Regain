package ai.regain.clone

import ai.regain.clone.data.Store
import ai.regain.clone.focus.FocusEngine
import android.app.Application

class RegainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        FocusEngine.init(this)
    }
}
