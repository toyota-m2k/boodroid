package io.github.toyota32k.video.model

import android.content.Context
import io.github.toyota32k.binder.command.Command
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.video.common.AmvSettings
import io.github.toyota32k.video.common.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import java.io.Closeable

/**
 * コントローラーの共通実装
 * ControlPanelModel              基本実装（AmvFrameSelectorViewで使用）
 *   + FullControlPanelModel      フル機能プレイヤー（AmvPlayerUnitView）用
 *   + TrimmingControlPanelModel  トリミング画面(AMvTrimmingPlayerView)用
 */
open class ControlPanelModel(
    val playerModel: PlayerModel
) : Closeable {
    companion object {
        val logger by lazy { UtLog("CPM", AmvSettings.logger) }
        fun create(context: Context): ControlPanelModel {
            val playerViewModel = PlayerModel(context)
            return ControlPanelModel(playerViewModel)
        }
    }

    /**
     * コントローラーのCoroutineScope
     * playerModel.scope を継承するが、ライフサイクルが異なるので、新しいインスタンスにしておく。
     */
    private val scope:CoroutineScope = CoroutineScope(playerModel.scope.coroutineContext)

    /**
     * ApplicationContext参照用
     */
    val context: Context get() = playerModel.context

    /**
     * AmvExoVideoPlayerのbindViewModelで、playerをplayerView.playerに設定するか？
     * 通常は true。ただし、FullControlPanelのように、PinP/FullScreenモードに対応する場合は、
     * どのビューに関連付けるかを個別に分岐するため、falseにする。
     */
    open val autoAssociatePlayer:Boolean = true

    // region Commands

    val commandPlay = Command { playerModel.play() }
    val commandPause = Command { playerModel.pause() }
    val commandTogglePlay = Command { playerModel.togglePlay() }
    val commandNext = Command { playerModel.next() }
    val commandPrev = Command { playerModel.previous() }
    val commandNextChapter = Command { playerModel.nextChapter() }
    val commandPrevChapter = Command { playerModel.prevChapter() }
    val commandSeekForward = Command { playerModel.seekRelative(11000) }
    val commandSeekBackward = Command { playerModel.seekRelative(-5000) }
    val commandFullscreen = Command { setWindowMode(WindowMode.FULLSCREEN) }
    val commandPinP = Command { setWindowMode(WindowMode.PINP) }
    val commandCollapse = Command { setWindowMode(WindowMode.NORMAL) }
    val commandPlayerTapped = LiteUnitCommand()

    // endregion

    // region Fullscreen/PinP

    enum class WindowMode {
        NORMAL,
        FULLSCREEN,
        PINP
    }
    val windowMode : StateFlow<WindowMode> = MutableStateFlow(WindowMode.NORMAL)
    fun setWindowMode(mode:WindowMode) {
        logger.debug("mode=${windowMode.value} --> $mode")
        (windowMode as MutableStateFlow).value = mode
    }

    // endregion

    // region Slider

    /**
     * スライダーのトラッカー位置
     */
//    val sliderPosition = MutableStateFlow(0L)

    /**
     * プレーヤーの再生位置
     * 通常は、sliderPosition == presentingPosition だが、トリミングスライダーの場合は、左右トリミング用トラッカーも候補となる。
     * （最後に操作したトラッカーの位置が、presentingPosition となる。）
     */
//    open val presentingPosition:Flow<Long> = sliderPosition

//    fun seekAndSetSlider(pos:Long) {
//        val clipped = playerModel.clipPosition(pos)
////        sliderPosition.value = clipped
//        playerModel.seekTo(clipped)
//    }
    /**
     * スライダーのカウンター表示文字列
     */
//    val counterText:Flow<String> = combine(playerModel.playerSeekPosition, playerModel.naturalDuration) { pos, duration->
//        "${formatTime(pos,duration)} / ${formatTime(duration,duration)}"
//    }

    // endregion

//    init {
//        playerModel.playerSeekPosition.onEach(this::onPlayerSeekPositionChanged).launchIn(scope)
//    }

    /**
     * タイマーによって監視されるプレーヤーの再生位置（playerModel.playerSeekPosition）に応じて、スライダーのシーク位置を合わせる。
     */
//    open fun onPlayerSeekPositionChanged(pos:Long) {
//        sliderPosition.value = pos
//    }

    override fun close() {
        scope.cancel()
        playerModel.close()
    }

}
