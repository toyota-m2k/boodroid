package io.github.toyota32k.boodroid

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
    private val appViewModel:AppViewModel by lazy { AppViewModel.instance }
    private lateinit var playerNotificationManager: PlayerNotificationManager

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
        private val terminator = MutableStateFlow<Boolean>(false)
        fun terminate() {
            terminator.value = true
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.debug()

        val context = this
        val adapter = object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                // return pending intent
                logger.debug()
                val intent = Intent(context, MainActivity::class.java);
                return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            //pass description here
            override fun getCurrentContentText(player: Player): String? {
                return null
            }

            //pass title (mostly playing audio name)
            override fun getCurrentContentTitle(player: Player): String {
                val title = appViewModel.playerModel.currentSource.value?.name
                logger.debug("$title")
                return if(title.isNullOrBlank()) "untitled" else title
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
                logger.debug()
                stopSelf()
            }

        }

        playerNotificationManager = PlayerNotificationManager
            .Builder(this, notificationId, channelId)
            .setChannelNameResourceId(R.string.channel_name)
            .setChannelDescriptionResourceId(R.string.channel_description)
            .setMediaDescriptionAdapter(adapter)
            .setNotificationListener(listener)
            .build()

        //attach player to playerNotificationManager
        appViewModel.playerModel.associateNotificationManager(playerNotificationManager)

        terminator.onEach {
            if(it) stopSelf()
        }.launchIn(UtImmortalTaskManager.immortalTaskScope)
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
    override fun onDestroy() {
        logger.debug()
        playerNotificationManager.setPlayer(null)
        AppViewModel.instance.controlPanelModel.close()
        super.onDestroy()
    }

    //removing service when user swipe out our app
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}