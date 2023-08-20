package io.github.toyota32k.boodroid

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.video.model.ControlPanelModel
import io.github.toyota32k.video.model.PlayerModel

/**
 * 端末スリープ中でも、バックグラウンドで再生を続けられるようにするためのサービスクラス
 * IntentService が deprecated になり、代用品の JobIntentServiceに替えようと思ったら、なんと、こいつもdeprecated。
 * 今はWorkManagerの時代らしいが、ExoPlayerには、PlayerNotificationManagerという専用の仕掛けがあって、
 * これを使うと、簡単にバックグラウンド再生に対応できるらしい。
 * https://intensecoder.com/android-exoplayer-background-play-using-kotlin/
 * を参考にしたが、要するに、（通知エリアに状態を表示する）フォアグラウンドサービスを作って、ここにExoPlayerをアタッチしておけば、
 * アプリがバックグラウンドに回っても、このサービスがフォアグラウンドに居座るので、再生が続けられる、という動作。
 */
class PlayerNotificationService : Service() {
    private lateinit var playerNotificationManager: PlayerNotificationManager

    private var controlPanelModel:ControlPanelModel? = null
    private val playerModel:PlayerModel? get() = controlPanelModel?.playerModel

    companion object {
        private val logger = UtLog("SRV", BooApplication.logger)
        private const val notificationId:Int = 0xB00
        private const val channelId:String = "BooService"

        /**
         * Activity がユーザー操作によって閉じられたときに、サービスも終了するための仕掛け
         * タスク一覧でスワイプしてタスクを停止されると onTaskRemoved が呼ばれて終了できるのだが、
         * Activityを戻るボタンで閉じたときには、サービスが残ってしまう。
         * Activity#onDestroy(isFinishing==true)で明示的にサービスを終了する。
         */
//        private val terminator = MutableStateFlow<Boolean>(false)
//        fun terminate() {
//            logger.debug("request termination.")
//            terminator.value = true
//        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()
        logger.debug()

        if(controlPanelModel==null) {
            controlPanelModel = AppViewModel.instance.controlPanelModelSource.fetch()
        }
//        terminator.value = false
        val context = this
        val adapter = object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                // return pending intent
                logger.debug()
                val intent = Intent(context, MainActivity::class.java)
                return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            //pass description here
            override fun getCurrentContentText(player: Player): String? {
                return null
            }

            //pass title (mostly playing audio name)
            override fun getCurrentContentTitle(player: Player): String {
                val title = playerModel?.currentSource?.value?.name ?: "untitled"
                logger.debug("$title")
                return title
            }

            // pass image as bitmap
            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): Bitmap? {
                return null
            }
        }
        val listener = object : PlayerNotificationManager.NotificationListener {

            override fun onNotificationPosted(
                notificationId: Int,
                notification: Notification,
                onGoing: Boolean) {

                logger.debug()
                startForeground(notificationId, notification)

            }

            override fun onNotificationCancelled(
                notificationId: Int,
                dismissedByUser: Boolean
            ) {
                logger.debug("notification cancelled --> terminate service by stopSelf()")
                stopSelf()
            }

        }

        if(Build.VERSION.SDK_INT >= 26) {
            // 起動後、再生が開始されないと、playerからの通知が行われず、
            // Context.startForegroundService() did not then call Service.startForeground() みたいな例外が出る。
            // これを回避するため、サービス起動後、1回、startForeground()を呼んでおく。
            // ref. https://stackoverflow.com/questions/44425584/context-startforegroundservice-did-not-then-call-service-startforeground
            startForeground(notificationId,
                NotificationCompat.Builder(this, channelId)
                .setContentTitle("")
                .setContentText("").build())
        }

        playerNotificationManager = PlayerNotificationManager
            .Builder(this, notificationId, channelId)
            .setChannelNameResourceId(R.string.channel_name)
            .setChannelDescriptionResourceId(R.string.channel_description)
            .setMediaDescriptionAdapter(adapter)
            .setNotificationListener(listener)
            .build()

        //attach player to playerNotificationManager
        playerModel?.associateNotificationManager(playerNotificationManager)

//        terminator.onEach {
//            if(it) {
//                logger.debug("terminating ... stopSelf()")
//                stopSelf()
//            }
//        }.launchIn(UtImmortalTaskManager.immortalTaskScope)
    }

    override fun onBind(intent: Intent?): IBinder? {
        logger.debug()
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.debug()
        return START_STICKY
    }

    // detach player
    @androidx.media3.common.util.UnstableApi
    override fun onDestroy() {
        logger.debug()
        if(controlPanelModel!=null) {
            AppViewModel.instance.controlPanelModelSource.release(controlPanelModel!!)
            controlPanelModel = null
        }
        playerNotificationManager.setPlayer(null)
//        AppViewModel.instance.controlPanelModel.close()
        super.onDestroy()
    }

    //removing service when user swipe out our app
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}