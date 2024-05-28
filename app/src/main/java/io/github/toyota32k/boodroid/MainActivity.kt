package io.github.toyota32k.boodroid

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.common.compatGetParcelableExtra
import io.github.toyota32k.boodroid.common.compatRegisterReceiver
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.boodroid.view.VideoListView
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.boodroid.viewmodel.MainViewModel
import io.github.toyota32k.dialog.UtMessageBox
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.video.model.ControlPanelModel
import io.github.toyota32k.video.view.AmvExoVideoPlayer
import io.github.toyota32k.video.view.ControlPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("Main", BooApplication.logger)

    private val binder = Binder()
    private val viewModel :MainViewModel by lazy { MainViewModel.instanceFor(this) }
    private val appViewModel:AppViewModel by lazy { AppViewModel.instance }
    private val controlPanelModel:ControlPanelModel get() = viewModel.controlPanelModel
    private val playerModel get() = controlPanelModel.playerModel

    private lateinit var controlPanel: ControlPanel
    private lateinit var videoListView: VideoListView
    private lateinit var splitter: View
    private lateinit var playerView : AmvExoVideoPlayer
    private lateinit var listPanel:View

    private var landscape:Boolean? = null
    private val Configuration.isLandscape : Boolean
        get() = this.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * 普通のActivityなら、onCreate()の中で setContentViewしてバインドするが、PinPをサポートするために、
     * AndroidManifestで、
     * > android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
     * を指定する必要があり、デバイスを回転しても onCreate()が呼ばれなくなったので、
     * 回転に伴うリソースの切り替えを、onConfigurationChanged から自前で実装する必要が生じた。
     * そのため、setContentViewがらみの処理を切り出しておく。
     */
    private fun initViews() {
        logger.debug("mode=${controlPanelModel.windowMode.value}")
        setContentView(R.layout.activity_main)

        binder.reset()

        playerView = findViewById(R.id.player)
        controlPanel = findViewById(R.id.controller)
        videoListView = findViewById(R.id.video_list)
        splitter = findViewById(R.id.splitter)

        listPanel = findViewById(R.id.video_list_panel)

        playerView.bindViewModel(controlPanelModel, binder)
        controlPanel.bindViewModel(controlPanelModel, binder)
        videoListView.bindViewModel(controlPanelModel.playerModel, binder)

        val selectButton = findViewById<ImageButton>(R.id.select_button)
//        val upButton = findViewById<ImageButton>(R.id.up_button)
//        val downButton = findViewById<ImageButton>(R.id.down_button)
        val syncButton = findViewById<ImageButton>(R.id.sync_button)
        val offlineButton = findViewById<ImageButton>(R.id.offline_button)
        val onlineButton = findViewById<ImageButton>(R.id.online_button)
        val refreshButton = findViewById<ImageButton>(R.id.refresh_button)
        val settingButton = findViewById<ImageButton>(R.id.setting_button)
        val titleText = findViewById<TextView>(R.id.title_text)

        binder
            .owner(this)
            .bindCommand(viewModel.selectOfflineVideoCommand, selectButton)
            .bindCommand(viewModel.setupOfflineModeCommand, onlineButton,offlineButton)
            .bindCommand(viewModel.syncWithServerCommand, syncButton)
            .bindCommand(appViewModel.refreshCommand, refreshButton, true)
            .bindCommand(appViewModel.settingCommand, settingButton)
            .bindCommand(controlPanelModel.commandPlayerTapped, this::onPlayerTapped)
            // リロードボタンは オンラインモードのときだけ表示する
            .visibilityBinding(refreshButton, appViewModel.offlineModeFlow, BoolConvert.Inverse)
            // オフラインボタンは、オンラインモードで、オフラインモード対応サーバーの場合だけ表示する。
            .visibilityBinding(offlineButton, combine(appViewModel.offlineModeFlow,viewModel.offlineModeAvailable) {off,av-> !off && av })
            // 選択アイテム同期ボタンは、オンラインモードで、サーバーがアイテム選択に対応している場合だけ表示する。
            .visibilityBinding(syncButton, combine(appViewModel.offlineModeFlow,viewModel.syncCommandAvailable) {off,av-> !off && av })
            // オンラインボタン、再生アイテム選択ボタンは、オフラインモードの場合のみ表示する。
            .multiVisibilityBinding(arrayOf(onlineButton,selectButton), appViewModel.offlineModeFlow)
//
//        binder.register(
//            viewModel.selectOfflineVideoCommand.attachView(selectButton),
////            viewModel.syncToServerCommand.attachView(upButton),
////            viewModel.syncFromServerCommand.attachView(downButton),
//            viewModel.setupOfflineModeCommand.attachView(onlineButton),
//            viewModel.setupOfflineModeCommand.attachView(offlineButton),
//            viewModel.syncWithServerCommand.attachView(syncButton),
////            viewModel.menuCommand.connectViewE(findViewById(R.id.boo_title_button)),
//            appViewModel.refreshCommand.attachView(refreshButton),
//            appViewModel.settingCommand.attachView(settingButton),
//            controlPanelModel.commandPlayerTapped.bind(this, this::onPlayerTapped),
//            MultiVisibilityBinding.create(this, onlineButton, syncButton, refreshButton, data = appViewModel.offlineModeFlow.asLiveData(), boolConvert = BoolConvert.Inverse),
//            MultiVisibilityBinding.create(this, selectButton, offlineButton, data = appViewModel.offlineModeFlow.asLiveData(), boolConvert = BoolConvert.Straight),
//            TextBinding.create(this, titleText, viewModel.playerModel.currentSource.map { it?.name ?: ""}),
//            VisibilityBinding.create(this, titleText, combine(controlPanelModel.windowMode, viewModel.playerModel.currentSource) {mode,src-> AppViewModel.instance.settings.showTitleOnScreen && mode== ControlPanelModel.WindowMode.FULLSCREEN && src!=null }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//        )

        when(controlPanelModel.windowMode.value) {
            ControlPanelModel.WindowMode.FULLSCREEN -> layoutForFullscreen()
            ControlPanelModel.WindowMode.NORMAL -> layoutForNormal()
            else -> { logger.error("unexpected state on windowMode.") }
        }

        viewModel.playerModel.requestAuthentication.bind(this) { current->
            UtImmortalSimpleTask.run {
                appViewModel.authentication.authentication(true)
                viewModel.playerModel.playAt(current.source, current.position)
                true
            }
        }

        if(!viewModel.serverAvailable) {
            appViewModel.settingCommand.invoke()
        }
    }

    private var updatingTheme:Boolean = false
    fun restartActivityToUpdateTheme() {
        updatingTheme = true
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }

    private val mediaSession by lazy { MediaSession(this, "Boo") }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppViewModel.instance.settings.colorVariation.themeId)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // 最近(2024/3/28現在)のAndroid Studioのテンプレートが書き出すコード（１）。。。タブレットでステータスバーなどによってクライアント領域が不正になる現象が回避できるっぽい。、

        landscape = resources.configuration.isLandscape
        initViews()

        // 最近(2024/3/28現在)のAndroid Studioのテンプレートが書き出すコード（２）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                logger.debug("collection START")
//                AppViewModel.instance.controlPanelModel.windowMode.collect { mode->
//                    when (mode) {
//                        ControlPanelModel.WindowMode.NORMAL-> layoutForNormal()
//                        ControlPanelModel.WindowMode.FULLSCREEN->layoutForFullscreen()
//                        ControlPanelModel.WindowMode.PINP->layoutForPinP()
//                    }
//                }
//            }
//            logger.debug("collection END")
//        }

        controlPanelModel.windowMode
            .onEach { mode ->
                when (mode) {
                    ControlPanelModel.WindowMode.NORMAL-> layoutForNormal()
                    ControlPanelModel.WindowMode.FULLSCREEN->layoutForFullscreen()
                    ControlPanelModel.WindowMode.PINP->layoutForPinP()
                }
            }
            .onStart { logger.debug("mode collection BEGIN") }
            .onCompletion { logger.debug("mode collection END") }
            .launchIn(viewModel.viewModelScope)
//            .launchIn(lifecycleScope)

        // 最初は lifecycleScopeを使っていたが、PinPから復帰したとき、
        // onPictureInPictureModeChanged --> onConfigurationChanged の間に、lifecycleScope（windowsModeの監視のonEach）が終了し、
        // その後、onConfigurationChangedや、onResumeで、onEachを再開を試みたが、どういうわけか、開始されない（なぜだろう）。
        // repeatOnLifecycle を使う方法も試したが、そもそも、PinPからの復帰では、onStartが来ないので役に立たなかった。
        // ちなみに、PinPに入って出るときのライフサイクルイベントは次のようになっていた。
        //
        //        :: PinPボタンタップ
        //        onPause
        //        onPictureInPictureModeChanged: pinp=true
        //        onEnterPinP
        //        onConfigurationChanged
        //
        //        :: 復帰ボタンタップ
        //        onPictureInPictureModeChanged: pinp=false
        //        onExitPinP
        //        onCreate$3.invokeSuspend: mode collection END	::このタイミングでlifecycleが死ぬ
        //        onConfigurationChanged
        //        onResume
        //
        // このイベントから、Activityは PinP に入るとき、Pause され、PinP から復帰するときに Resume されていることがわかる。
        // つまり、PinP中、Activity は Pauseされている、ということらしい。
        handleUriInIntent(intent)

        startPlayerService()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // ヘッドセットからのボタンイベント監視を開始
            mediaSession.isActive = true
            mediaSession.setCallback(object: MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val action = mediaButtonIntent.action
                    if (Intent.ACTION_MEDIA_BUTTON == action) {
                        val event = mediaButtonIntent.compatGetParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        if (event?.action == KeyEvent.ACTION_DOWN) {
                            when (event.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY -> viewModel.controlPanelModel.commandTogglePlay.invoke()
                                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> viewModel.controlPanelModel.commandPause.invoke()
                                KeyEvent.KEYCODE_MEDIA_NEXT -> viewModel.controlPanelModel.commandNext.invoke()
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> viewModel.controlPanelModel.commandPrev.invoke()
                                // 他のキーコードに対する処理を追加
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleUriInIntent(intent)
    }

    private fun startPlayerService() {
        try {
            logger.info("startForegroundService calling.")
            val intent = Intent(this, PlayerNotificationService::class.java)
            startForegroundService(intent)
        } catch(e:Throwable) {
            logger.stackTrace(e, "cannot start service.")
        }
    }

    private fun stopPlayerService() {
        logger.debug()
        stopService(Intent(application, PlayerNotificationService::class.java))
    }


    @Suppress("SpellCheckingInspection")
    private fun isAcceptableUrl(url:String?):Boolean {
        if(url==null) return false
        if(!url.startsWith("https://")) return false
        val host = Uri.parse(url).host ?: return false
        return host.contains("youtube.com")||host.contains("youtu.be")
    }

    private fun handleUriInIntent(intent:Intent?) : Boolean {
        if(intent?.action == Intent.ACTION_SEND) {
            if(intent.type == "text/plain") {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    logger.debug(it)
                    if(isAcceptableUrl(it)) {
                        viewModel.registerYouTubeUrl(it)
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 回転によるレイアウト変更を自力でやる。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        logger.debug("$newConfig")
        super.onConfigurationChanged(newConfig)
        if(!isPinP) {
            newConfig.isLandscape.also { land ->
                if (land != landscape) {
                    landscape = land
                    initViews()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        logger.debug()
    }

    override fun onResume() {
        super.onResume()
        logger.debug()
        keepScreenOn(true)
    }

    override fun onRestart() {
        super.onRestart()
        logger.debug()
    }

    override fun onPause() {
        super.onPause()
        logger.debug()
        keepScreenOn(false)
    }

    override fun onStop() {
        super.onStop()
        logger.debug()
    }

    private fun onPlayerTapped() {
        when(controlPanelModel.windowMode.value) {
            ControlPanelModel.WindowMode.FULLSCREEN-> {
                controlPanel.visibility = if(controlPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            ControlPanelModel.WindowMode.NORMAL -> {
                controlPanelModel.commandTogglePlay.invoke()
            }
            else -> {}
        }
    }

    private fun keepScreenOn(sw:Boolean) {
        if(sw) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun layoutForPinP() {
//        appViewModel.keepAlive(true)
        logger.debug()
        enterPinP()
   }

    private val exceptPlayerViews:Array<View> get() = arrayOf(listPanel, controlPanel, splitter)

    private fun layoutForFullscreen() {
//        appViewModel.keepAlive(true)
        logger.debug()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        for(v in exceptPlayerViews) {
            v.visibility = View.GONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
//                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    private fun layoutForNormal() {
//        appViewModel.keepAlive(false)
        logger.debug()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        for(v in exceptPlayerViews) {
            v.visibility = View.VISIBLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }


    override fun onDestroy() {
        logger.debug()
        if(isFinishing) {
            logger.debug("finishing")
            val current = playerModel.currentSource.value
            if (current != null) {
                val pos = playerModel.playerSeekPosition.value
                LastPlayInfo.set(BooApplication.instance.applicationContext, current.id, pos, true)
            }
            if(!updatingTheme) {
                logger.debug("activity finishing")
                stopPlayerService()
            }
        }

        // ヘッドセットからのボタンイベント監視を終了
        mediaSession.isActive = false
        mediaSession.setCallback(null)

        super.onDestroy()
        binder.reset()
    }

    private fun queryToFinish() {
        UtImmortalSimpleTask.run {
            val dlg = showDialog("finishing") { UtMessageBox.createForYesNo("BooDroid", "Finish BooDroid") }
            if(dlg.status.yes) {
                stopPlayerService()
                finishAndRemoveTask()
                true
            } else false
        }
    }

    override fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode==KeyEvent.KEYCODE_BACK) {
            when(controlPanelModel.windowMode.value) {
                ControlPanelModel.WindowMode.NORMAL-> queryToFinish()
                ControlPanelModel.WindowMode.FULLSCREEN->controlPanelModel.setWindowMode(ControlPanelModel.WindowMode.NORMAL)
                else-> return false
            }
            return true
        }
        return false
    }

    // region PinP
    private var isPinP = false
    /**
     * PinPに遷移する
     * （PinPから通常モードへの遷移はシステムに任せる。というより、そのようなAPIは存在しない。）
     */
    private fun enterPinP() {
        if(isPinP) return

        val w = controlPanelModel.playerModel.videoWidth ?: 100
        val h = controlPanelModel.playerModel.videoHeight ?: 100
        val ro = Rational(w, h)
        val rational = when {
            ro.isNaN || ro.isInfinite || ro.isZero -> Rational(1, 1)
            ro.toFloat() > 2.39 -> Rational(239, 100)
            ro.toFloat() < 1 / 2.39 -> Rational(100, 239)
            else -> ro
        }
        val param = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .setActions(listOf(playAction, pauseAction, seekTopAction))
            .build()
        enterPictureInPictureMode(param)
    }

    /**
     * PinP中のアクション
     */
    private enum class Action(val code:Int) {
        PLAY(1),
        PAUSE(2),
        SEEK_TOP(3),
    }

    private lateinit var pinpBroadcastReceiver: BroadcastReceiver        // PinP中のコマンド（ブロードキャスト）を受け取るレシーバー

//    private val headsetBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            val action = intent.action
//            if (Intent.ACTION_MEDIA_BUTTON == action) {
//                val event = intent.compatGetParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
//                if (event?.action == KeyEvent.ACTION_DOWN) {
//                    when (event.keyCode) {
//                        KeyEvent.KEYCODE_MEDIA_PLAY -> viewModel.controlPanelModel.commandPlay.invoke()
//                        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> viewModel.controlPanelModel.commandPause.invoke()
//                        KeyEvent.KEYCODE_MEDIA_NEXT -> viewModel.controlPanelModel.commandNext.invoke()
//                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> viewModel.controlPanelModel.commandPrev.invoke()
//                        // 他のキーコードに対する処理を追加
//                    }
//                }
//            }
//        }
//    }



    /**
     * PinP内のPlayボタン
     */
    private val playAction: RemoteAction by lazy {
        val context = this
        val icon = Icon.createWithResource(context, io.github.toyota32k.video.R.drawable.ic_play)
        val title = context.getText(R.string.play)
        val pendingIntent = PendingIntent.getBroadcast(context, Action.PLAY.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PLAY.code), PendingIntent.FLAG_IMMUTABLE)
        RemoteAction(icon, title, title, pendingIntent)
    }

    /**
     * PinP内のPauseボタン
     */
    private val pauseAction: RemoteAction by lazy {
        val context = this
        val icon = Icon.createWithResource(context, io.github.toyota32k.video.R.drawable.ic_pause)
        val title = context.getText(R.string.pause)
        val pendingIntent = PendingIntent.getBroadcast(context, Action.PAUSE.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PAUSE.code), PendingIntent.FLAG_IMMUTABLE)
        RemoteAction(icon, title, title, pendingIntent)
    }

    /**
     * 先頭へシーク
     */
    private val seekTopAction:RemoteAction by lazy {
        val context = this
        val icon = Icon.createWithResource(context, io.github.toyota32k.video.R.drawable.ic_prev)
        val title = context.getText(R.string.seekTop)
        val pendingIntent = PendingIntent.getBroadcast(context, Action.SEEK_TOP.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.SEEK_TOP.code),PendingIntent.FLAG_IMMUTABLE)
        RemoteAction(icon, title, title, pendingIntent)
    }

    private var pinpScope:CoroutineScope? = null
    /**
     * PinPモードが開始される
     */
    private fun onEnterPinP() {
        logger.debug()
        isPinP = true

        for(v in exceptPlayerViews) {
            v.visibility = View.GONE
        }

        pinpScope = CoroutineScope(lifecycleScope.coroutineContext)
        controlPanelModel.playerModel.isPlaying.onEach { playing->
            playAction.isEnabled = !playing
            pauseAction.isEnabled = playing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                playAction.setShouldShowIcon(!playing)
                pauseAction.setShouldShowIcon(playing)
            }
        }.launchIn(pinpScope!!)

        pinpBroadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null||intent.action!=INTENT_NAME) {
                    return
                }

                when(intent.getIntExtra(ACTION_TYPE_KEY, -1)) {
                    Action.PAUSE.code -> controlPanelModel.playerModel.pause()
                    Action.PLAY.code -> controlPanelModel.playerModel.play()
                    Action.SEEK_TOP.code -> controlPanelModel.playerModel.seekTo(0L)
                    else -> {}
                }
            }
        }
        compatRegisterReceiver(pinpBroadcastReceiver, IntentFilter(INTENT_NAME), exported = false)
    }

    /**
     * PinPモードが終了する
     */
    private fun onExitPinP() {
        logger.debug()
        isPinP = false
        landscape = null
        pinpScope?.cancel()
        unregisterReceiver(pinpBroadcastReceiver)
        controlPanelModel.setWindowMode(ControlPanelModel.WindowMode.NORMAL)
    }

    /**
     * PinPモードが変更されるときの通知
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        logger.debug("pinp=$isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if(!isInPictureInPictureMode) {
            onExitPinP()
        } else {
            onEnterPinP()
        }
    }

    // endregion

    companion object {
        private const val INTENT_NAME = "PlayVideo"
        private const val ACTION_TYPE_KEY = "ActionType"
    }
}