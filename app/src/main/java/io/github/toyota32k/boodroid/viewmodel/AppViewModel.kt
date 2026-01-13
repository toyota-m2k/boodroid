@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.toyota32k.boodroid.viewmodel

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.WallpaperActivity
import io.github.toyota32k.boodroid.auth.Authentication
import io.github.toyota32k.boodroid.common.IUtPropertyHost
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.QueryBuilder
import io.github.toyota32k.boodroid.data.ServerCapability
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.data.VideoItemFilter
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.boodroid.dialog.HostSettingsDialog
import io.github.toyota32k.boodroid.dialog.PreferencesDialog
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtResetableValue
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.RefBitmapHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

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
        controlPanelModelSource.withModel {
            // 認証が必要な秘密のファイルは保存を禁止する
            it.permitSnapshot(!cap.needAuth)
        }
    }

    val supportSyncItemSelection: Boolean
        get() = capability.value.hasView && !offlineMode
    val supportRating: Boolean
        get() = capability.value.hasRating
    val supportCategory: Boolean
        get() = capability.value.hasCategory
    val supportMark: Boolean
        get() = capability.value.hasMark

    private var prepared:Boolean = false
    private fun prepare():AppViewModel {
        if(!prepared) {
//            settings = Settings.load(BooApplication.instance)
            prepared = true
//            val mode = settings.theme.mode
//            if(AppCompatDelegate.getDefaultNightMode()!=mode) {
//                AppCompatDelegate.setDefaultNightMode(mode)
//            }
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
        override fun next() {
            listSource.mutable.value.next()
        }
        override fun previous() {
            listSource.mutable.value.previous()
        }
        val videoList = ObservableList<IMediaSourceWithChapter>()

        fun setVideoListSource(v:VideoListSource?) {
            videoList.clear()
            if(v!=null && v.list.isNotEmpty()) {
                videoList.addAll(v.list)
            }
            mediaFeed.listSource.mutable.value = v ?: VideoListSource.empty
        }
    }

    private val mediaFeed = MediaFeed()
    val videoList:ObservableList<IMediaSourceWithChapter>
        get() = mediaFeed.videoList
    var videoListSource: VideoListSource?
        get() = mediaFeed.listSource.value
        set(v) { mediaFeed.setVideoListSource(v) }
    val currentSource:StateFlow<IMediaSource?>
        get() = mediaFeed.currentSource

    // endregion

    private suspend fun saveImageAsFile(activity:MainActivity, bitmap:Bitmap, fileName:String) {
        val uri = activity.activityBrokers.createFilePicker.selectFile(fileName, "image/jpeg")
        if(uri!=null) {
            try {
                activity.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    it.flush()
                }
            } catch(e:Exception) {
                logger.error(e)
            }
        }
    }

    var wallpaperSourceBitmap:RefBitmap? by RefBitmapHolder()

    private fun saveBitmap(position:Long, bitmap: RefBitmap) {
        val item = currentSource.value ?: return
        val name = item.name
        val fileName = if (item.isPhoto) "${name}.jpg" else "${name}_${position}.jpg"
        controlPanelModelSource.withModel {
            it.playerModel.pause()
        }
        UtImmortalTask.launchTask("snapshot") {
            wallpaperSourceBitmap = bitmap
            withOwner {
                val activity = it.asActivity() as? MainActivity ?: return@withOwner
                activity.startActivity(
                    Intent(activity, WallpaperActivity::class.java).putExtra(Intent.EXTRA_TEXT,fileName)
                )
            }
        }
    }

    /**
     * BooRemote で再生中の動画をBooTubeの動画リスト上で選択する
     */
    fun syncToServer() {
        controlPanelModelSource.withModel { controlPanelModel ->
            val current = controlPanelModel.playerModel.currentSource.value ?: return
            val url = AppViewModel.url.current ?: return
            val json = JSONObject()
                .put("id", current.id)
                .toString()
            val req = Request.Builder()
                .url(url)
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NetClient.executeAsync(req).close()
                } catch (e: Throwable) {
                    MainViewModel.Companion.logger.stackTrace(e)
                }
            }
        }
    }

    /**
     * BooTube上で選択（フォーカス）されている動画をBooRemote上で再生する。
     */
    fun syncFromServer() {
        fun tryPlayAt(id: String) {
            val listSource = videoListSource ?: return
            val index = listSource.list.indexOfFirst { it.id == id }
            if(index>=0) {
                listSource.setCurrentSource(index, 0)
            }
        }

        val url = AppViewModel.url.current ?: return
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = NetClient.executeAndGetJsonAsync(req)
                withContext(Dispatchers.Main) {
                    tryPlayAt(json.getString("id"))
                }
            } catch (e: Throwable) {
                MainViewModel.Companion.logger.stackTrace(e)
            }
        }
    }


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
                            .supportSnapshot(::saveBitmap)
                            .enableSeekMedium(5000,15000)
                            .enableVolumeController(true)
                            .enablePhotoViewer(settings.slideInterval.seconds)
                            .enableRotateRight()
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
    val hostSettingsCommand = LiteUnitCommand {
        UtImmortalTask.launchTask("settings") {
            createViewModel<HostSettingsViewModel> { prepare() }
            showDialog(taskName) { HostSettingsDialog() }
        }
    }

    val preferencesCommand = LiteUnitCommand {
        PreferencesDialog.show()
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
            showTitleOnScreen.mutable.value = v.showTitleOnScreen
            controlPanelModelSource.withModel { vm->
                vm.playerModel.photoSlideShowDuration = v.slideInterval.seconds
            }
            refreshCommand.invoke(false)
        }

    /**
     * タイトルを表示するモード
     */
    val showTitleOnScreen: StateFlow<Boolean> = MutableStateFlow(settings.showTitleOnScreen)


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
            // val cmd = if( capability.value.version >= 2) "item" else "video"   // 新バージョンなら item / 旧バージョン互換: video
            val cmd = "item"
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