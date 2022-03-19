package io.github.toyota32k.boodroid

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.boodroid.dialog.SettingsDialog
import io.github.toyota32k.boodroid.view.VideoListView
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.video.view.AmvExoVideoPlayer
import io.github.toyota32k.video.view.ControlPanel
import io.github.toyota32k.video.view.VideoPlayerView

class MainActivity : UtMortalActivity() {
    private val binder = Binder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appViewModel = AppViewModel.instance

        findViewById<AmvExoVideoPlayer>(R.id.player).bindViewModel(appViewModel.controlPanelModel, binder)
        findViewById<ControlPanel>(R.id.controller).bindViewModel(appViewModel.controlPanelModel, binder)
        findViewById<VideoListView>(R.id.video_list).bindViewModel(appViewModel.controlPanelModel.playerModel, binder)

        binder.register(
            appViewModel.refreshCommand.connectViewEx(findViewById(R.id.refresh_button)),
            appViewModel.settingCommand.connectViewEx(findViewById(R.id.setting_button))
        )

        if (!AppViewModel.instance.settings.isValid) {
            appViewModel.settingCommand.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binder.reset()
    }
}