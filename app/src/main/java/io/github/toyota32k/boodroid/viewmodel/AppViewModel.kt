@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.toyota32k.boodroid.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.auth.Authentication
import io.github.toyota32k.boodroid.common.IUtPropertyHost
import io.github.toyota32k.boodroid.data.QueryBuilder
import io.github.toyota32k.boodroid.data.ServerCapability
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.data.VideoItemFilter
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.boodroid.dialog.SettingsDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtResetableValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

interface IURLResolver {
    val auth:String
    fun auth(token:String):String
    val list:String
    fun list(date:Long):String
    fun check(date:Long):String?
    fun item(id:String):String
    fun chapter(id:String):String?

    fun register(param:String):String?
    val current:String?
    val reputation:String?
    fun reputation(id:String):String?
}

class AppViewModel: ViewModel(), IUtPropertyHost {
    companion object {
        val logger = UtLog("AVP", BooApplication.logger)
        val instance: AppViewModel
            get() = ViewModelProvider(BooApplication.instance, ViewModelProvider.NewInstanceFactory())[AppViewModel::class.java].prepare()
        val url: IURLResolver
            get() = instance.urlResolver

    }
    // region Initialization / Termination

    val capability: StateFlow<ServerCapability> = MutableStateFlow(ServerCapability.empty)
    val needAuth:Boolean get() = capability.value.needAuth
    val authentication = Authentication()

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

    object EmptyVideoSource : IMediaFeed {
        override val hasNext: StateFlow<Boolean> = MutableStateFlow(false)
        override val hasPrevious: StateFlow<Boolean> = MutableStateFlow(false)
        override val currentSource: StateFlow<IMediaSource?> = MutableStateFlow(null)
        override fun next() {}
        override fun previous() {}
    }

    inner class MediaFeed() : IMediaFeed, IUtPropOwner {
        val listSource: StateFlow<VideoListSource> = MutableStateFlow<VideoListSource>(VideoListSource.empty)
        override val hasNext: StateFlow<Boolean> = listSource.flatMapLatest { it.hasNext }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
        override val hasPrevious: StateFlow<Boolean> = listSource.flatMapLatest { it.hasPrevious }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
        override val currentSource: StateFlow<IMediaSource?> = listSource.flatMapLatest { it.currentSource }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
//        val currentSourceIndex: StateFlow<Int> = listSource.flatMapLatest { it.currentSourceIndex }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, -1)
        override fun next() {
            listSource.mutable.value.next()
        }
        override fun previous() {
            listSource.mutable.value.previous()
        }
        val videoList = ObservableList<VideoItem>()

        fun setVideoListSource(v:VideoListSource?) {
            videoList.clear()
            if(v!=null && v.list.isNotEmpty()) {
                videoList.addAll(v.list)
            }
            mediaFeed.listSource.mutable.value = v ?: VideoListSource.empty
        }
    }

    private val mediaFeed = MediaFeed()
    val videoList:ObservableList<VideoItem>
        get() = mediaFeed.videoList
    var videoListSource: VideoListSource?
        get() = mediaFeed.listSource.value
        set(v) { mediaFeed.setVideoListSource(v) }
    val currentSource:StateFlow<IMediaSource?>
        get() = mediaFeed.currentSource

//    val videoListSourceFlow: StateFlow<VideoListSource?>
//        get() = mediaFeed.listSource


    // endregion

    // region Player/ControlPanel ViewModel

    inner class RefCounteredControlPanelModel {
        private val resetable = UtResetableValue<PlayerControllerModel>()
        private var refCount:Int = 0

        fun fetch():PlayerControllerModel {
            synchronized(this) {
                if (!resetable.hasValue) {
                    resetable.value =
                        PlayerControllerModel.Builder(BooApplication.instance.applicationContext, viewModelScope)
                            .supportChapter()
                            .supportPlaylist(mediaFeed, true, true)
                            .showNextPreviousButton()
                            .supportFullscreen()
                            .supportPiP()
                            .enableSeekMedium(5000,15000)
                            .build()
                    refCount = 0
                }
                refCount++
                return resetable.value
            }
        }
        fun release(v:PlayerControllerModel) {
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

        inline fun <T> withModel(fn:(PlayerControllerModel)->T):T {
            val v = fetch()
            return try {
                fn(v)
            } finally {
                release(v)
            }
        }
    }

    val controlPanelModelSource = RefCounteredControlPanelModel()

    // endregion

    // region Settings / Offline Mode

    /**
     * 設定ダイアログを開く
     */
    val settingCommand = LiteUnitCommand {
        UtImmortalSimpleTask.run("settings") {
            SettingViewModel.createBy(this) { it.prepare() }
            this.showDialog(taskName) { SettingsDialog() }.status.ok
        }
    }

    /**
     * サーバーの設定などが変更され、動画リストの更新が必要になったことを知らせるイベント
     * arg:
     * false: 無条件にリストの再取得を行う
     * true: サーバーエラーが起きていたら設定画面を開く。エラーが起きていなければリストの再取得を行う
     *      *
     */
    val refreshCommand = LiteCommand<Boolean>()

    /**
     * 環境設定
     */
    var settings: Settings = Settings.load(BooApplication.instance)
        set(v) {
            val o = field
            field = v
            val urlChanged = VideoItemFilter.urlWithQueryString(o,0,null) != VideoItemFilter.urlWithQueryString(v, 0, null)
            if (urlChanged || o.offlineMode != v.offlineMode) {
                offlineMode = if (urlChanged) false else v.offlineMode
            }
            refreshCommand.invoke(false)
            if (v.themeId != o.themeId) {
                UtImmortalSimpleTask.run {
                    withOwner {
                        val activity = it.asActivity() as? MainActivity ?: return@withOwner
                        activity.restartActivityToUpdateTheme()
                    }
                    true
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
            refreshCommand.invoke(false)
        }
    }

    inner class URLResolver : IURLResolver {
        private val baseUrl get() = settings.baseUrl
        private val authToken get() = authentication.authToken
        override val auth:String get() = "${baseUrl}auth"
        override fun auth(token:String):String {
            return "${baseUrl}auth/$token"
        }
        override val list:String get() = VideoItemFilter.urlWithQueryString(settings, 0, authToken)
        override fun list(date:Long):String {
            return VideoItemFilter.urlWithQueryString(settings, date, authToken)
        }
        override fun check(date:Long):String? {
            return if(capability.value.diff) "${baseUrl}/check?date=${date}" else null
        }
        override fun item(id:String):String {
            val qb = QueryBuilder()
            authToken?.also { token->
                qb.add("auth", token)
            }
            qb.add("id", id)
            val cmd = if( capability.value.version >= 2) "item" else "video"   // 新バージョンなら item / 旧バージョン互換: video
            return "${baseUrl}${cmd}?${qb.queryString}"
        }
        override fun chapter(id:String):String? {
            return if(capability.value.hasChapter) "${baseUrl}chapter?id=$id" else null
        }

        override fun register(param: String): String? {
            return if(capability.value.acceptRequest) "${baseUrl}register?url=$param" else null
        }

        override val current: String?
            get() = if(capability.value.hasView) "${baseUrl}current" else null
        override val reputation: String?
            get() = if(capability.value.reputation>0) "${baseUrl}reputation" else null

        override fun reputation(id: String):String? {
            return if(capability.value.reputation==2) "${baseUrl}reputation?id=$id" else null
        }
    }
    val urlResolver = URLResolver()

    // endregion
}