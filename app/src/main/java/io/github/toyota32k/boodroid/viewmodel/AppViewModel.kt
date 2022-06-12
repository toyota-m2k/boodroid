package io.github.toyota32k.boodroid.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.common.IUtPropertyHost
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.ServerCapability
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.dialog.SettingsDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtResetableValue
import io.github.toyota32k.video.model.ControlPanelModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

class AppViewModel: ViewModel(), IUtPropertyHost {
    companion object {
        val logger = UtLog("AVP", BooApplication.logger)
        val instance: AppViewModel
            get() = ViewModelProvider(BooApplication.instance, ViewModelProvider.NewInstanceFactory())[AppViewModel::class.java].prepare()
    }
    // region Initialization / Termination

    val capability: StateFlow<ServerCapability> = MutableStateFlow(ServerCapability.empty)

    fun setCapability(cap:ServerCapability) {
        capability.mutable.value = cap
    }


    private var prepared:Boolean = false
    private fun prepare():AppViewModel {
        if(!prepared) {
//            settings = Settings.load(BooApplication.instance)
            prepared = true
            val mode = settings.theme.mode
            if(AppCompatDelegate.getDefaultNightMode()!=mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
        return this
    }

    override fun onCleared() {
        logger.debug()
        super.onCleared()
    }

    // endregion

    // region Player/ControlPanel ViewModel

    class RefCounteredControlPanelModel {
        private val resetable = UtResetableValue<ControlPanelModel>()
        private var refCount:Int = 0

        fun fetch():ControlPanelModel {
            synchronized(this) {
                if (!resetable.hasValue) {
                    resetable.value = ControlPanelModel.create(BooApplication.instance.applicationContext)
                    refCount = 0
                }
                refCount++
                return resetable.value
            }
        }
        fun release(v:ControlPanelModel) {
            synchronized(this) {
                if(v == resetable.value) {
                    refCount--
                    if (refCount <= 0) {
                        resetable.reset {
                            it.close()
                        }
                    }
                }
            }
        }
    }

    val controlPanelModelSource = RefCounteredControlPanelModel()

    // endregion

    // region Settings / Offline Mode

    /**
     * 設定ダイアログを開く
     */
    val settingCommand = Command {
        UtImmortalSimpleTask.run("settings") {
            SettingViewModel.createBy(this) { it.prepare() }
            this.showDialog(taskName) { SettingsDialog() }.status.ok
        }
    }

    /**
     * サーバーの設定などが変更され、動画リストの更新が必要になったことを知らせるイベント
     */
    val refreshCommand = Command()

    /**
     * 環境設定
     */
    var settings: Settings = Settings.load(BooApplication.instance)
        set(v) {
            if(v!=field) {
                val o = field
                field = v
                if(v.listUrl(0)!=o.listUrl(0) || v.offlineMode || v.offlineMode!=o.offlineMode) {
                    offlineMode = v.offlineMode
                    refreshCommand.invoke()
                }
                if(v.colorVariation!=o.colorVariation) {
                    UtImmortalSimpleTask.run {
                        withOwner {
                            val activity = it.asActivity() as? MainActivity ?: return@withOwner
                            activity.restartActivityToUpdateTheme()

                        }
                        true
                    }
                }
            }
        }

    /**
     * オフラインモード
     */
    val offlineModeFlow = MutableStateFlow(settings.offlineMode)
    var offlineMode
        get() = offlineModeFlow.value
        private set(v) { offlineModeFlow.value = v }
    val offlineFilter:Boolean
        get() = settings.offlineFilter

    /**
     * オフラインモードを変更する
     */
    fun updateOfflineMode(mode:Boolean, filter:Boolean, updateList:Boolean) {
        if(settings.offlineMode != mode || settings.offlineFilter != filter) {
            // モードが変更になった場合、Settings.save() --> AppViewModel#settings のセッターで refresh コマンドが呼ばれる
            Settings(settings, offlineMode = mode, offlineFilter = filter).save(BooApplication.instance.applicationContext)
        } else if(mode && updateList) {
            // オフラインモードのまま変わらない場合、リストが更新された時は、明示的にrefreshする
            refreshCommand.invoke()
        }
    }

    // endregion
}