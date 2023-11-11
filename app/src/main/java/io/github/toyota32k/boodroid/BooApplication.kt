package io.github.toyota32k.boodroid

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.toyota32k.boodroid.data.ServerCapability
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.UtStandardString
import io.github.toyota32k.utils.UtLazyResetableValue
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.video.common.AmvSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BooApplication : Application(), ViewModelStoreOwner {
    private val mViewModelStore = UtLazyResetableValue { ViewModelStore() }

    val offlineManager: OfflineManager by lazy { OfflineManager(this) }

    override val viewModelStore: ViewModelStore
        get() = mViewModelStore.value

    private fun releaseViewModelStore() {
        mViewModelStore.reset { it.clear() }
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
            showDialogImmediately = UtDialogConfig.ShowDialogMode.Commit
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
