package io.github.toyota32k.boodroid

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.UtStandardString
import io.github.toyota32k.utils.UtLazyResetableValue
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.video.common.AmvSettings

class BooApplication : Application(), ViewModelStoreOwner {
    private var viewModelStore = UtLazyResetableValue { ViewModelStore() }

    val offlineManager: OfflineManager by lazy { OfflineManager(this) }

    override fun getViewModelStore(): ViewModelStore {
        return viewModelStore.value
    }

    private fun releaseViewModelStore() {
        viewModelStore.reset { it.clear() }
    }

    init {
        instance_ = this
    }

    override fun onCreate() {
        super.onCreate()
        AmvSettings.logger = logger
        UtStandardString.setContext(applicationContext,null)
        UtDialogConfig.apply {
            solidBackgroundOnPhone = false
            showDialogImmediately = false
            showInDialogModeAsDefault = true
        }

        val appViewModel = AppViewModel.instance
        if (!AppViewModel.instance.settings.isValid) {
            appViewModel.settingCommand.invoke()
        }
    }

    override fun onTerminate() {
        logger.debug()
        releaseViewModelStore()
        super.onTerminate()
    }

    companion object {
        private lateinit var instance_:BooApplication
        val instance get() = instance_
        val logger = UtLog("Boo.", omissionNamespace = "io.github.toyota32k.")
    }
}
