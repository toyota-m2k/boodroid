package io.github.toyota32k.boodroid.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.dialog.SettingsDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtResetableValue
import io.github.toyota32k.video.model.ControlPanelModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

class AppViewModel: ViewModel() {
    companion object {
        val logger = UtLog("AVP", BooApplication.logger)
        val instance: AppViewModel
            get() = ViewModelProvider(BooApplication.instance, ViewModelProvider.NewInstanceFactory())[AppViewModel::class.java].prepare()
    }

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

        fun withModel(fn:(ControlPanelModel)->Unit) {
            val v = fetch()
            try {
                fn(v)
            } finally {
                release(v)
            }
        }
    }

    val controlPanelModelSource = RefCounteredControlPanelModel()

//    private val controlPanelModelEntity = UtLazyResetableValue<ControlPanelModel>()

    //lateinit var controlPanelModel:ControlPanelModel
    // val playerModel:PlayerModel get() = controlPanelModel.playerModel
    val offlineModeFlow = MutableStateFlow<Boolean>(false)
    var offlineMode
        get() = offlineModeFlow.value
        private set(v) { offlineModeFlow.value = v }

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

    // 通信中フラグ
    val loading = MutableStateFlow(false)
    var lastUpdate : Long = 0L

    private var prepared:Boolean = false
    private fun prepare():AppViewModel {
        if(!prepared) {
//            settings = Settings.load(BooApplication.instance)
            val mode = settings.theme.mode
            if(AppCompatDelegate.getDefaultNightMode()!=mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
        return this
    }


    fun registerYoutubeUrl(rawUrl:String) {
        val urlParam = rawUrl.split("\r", "\n", " ", "\t").firstOrNull { it.isNotBlank() } ?: return
        val url = settings.urlToRegister(urlParam)
        CoroutineScope(Dispatchers.IO).launch {
            val req = Request.Builder()
                .url(url)
                .get()
                .build()
            try {
                NetClient.executeAsync(req)
            } catch (e:Throwable) {
                logger.stackTrace(e)
            }
        }
    }

    override fun onCleared() {
        logger.debug()
        super.onCleared()
    }

    val settingCommand = Command {
        UtImmortalSimpleTask.run("settings") {
            SettingViewModel.createBy(this) { it.prepare() }
            this.showDialog(taskName) { SettingsDialog() }.status.ok
        }
    }
    val refreshCommand = Command()

    val syncToServerCommand = Command()

    val syncFromServerCommand = Command ()

    val menuCommand = Command()
}