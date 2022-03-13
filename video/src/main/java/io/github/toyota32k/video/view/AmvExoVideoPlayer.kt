package io.github.toyota32k.video.view

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.BoolConvert
import io.github.toyota32k.bindit.TextBinding
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.video.common.AmvSettings
import io.github.toyota32k.video.model.ControlPanelModel
import io.github.toyota32k.video.model.PlayerModel
import io.github.toyota32k.utils.lifecycleOwner
import io.github.toyota32k.utils.px2dp
import io.github.toyota32k.utils.setLayoutSize
import io.github.toyota32k.video.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AmvExoVideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = AmvSettings.logger

        fun createViewModel(context:Context) : PlayerModel {
            return PlayerModel(context)
        }
    }

    private val playerView: StyledPlayerView
    private val rootView:ViewGroup

    // 使う人（ActivityやFragment）がセットすること
    private lateinit var model: PlayerModel
    var useExoController:Boolean
        get() = playerView.useController
        set(v) { playerView.useController = v }
    val fitParent:Boolean


    init {
        LayoutInflater.from(context).inflate(R.layout.v2_video_exo_player, this)
        val sa = context.theme.obtainStyledAttributes(attrs,
            R.styleable.AmvExoVideoPlayer,defStyleAttr,0)
        var showControlBar = false
        try {
            // タッチで再生/一時停止をトグルさせる動作の有効・無効
            //
            // デフォルト有効
            //      ユニットプレーヤー以外は無効化
            if (sa.getBoolean(R.styleable.AmvExoVideoPlayer_playOnTouch, true)) {
                this.setOnClickListener {
                    if (it is AmvExoVideoPlayer) {
                        it.model.togglePlay()
                    }
                }
            }
            // ExoPlayerのControllerを表示するかしないか・・・表示する場合も、カスタマイズされたControllerが使用される
            //
            // デフォルト無効
            //      フルスクリーン再生の場合のみ有効
            showControlBar = sa.getBoolean(R.styleable.AmvExoVideoPlayer_showControlBar, false)

            // AmvExoVideoPlayerのサイズに合わせて、プレーヤーサイズを自動調整するかどうか
            // 汎用的には、AmvExoVideoPlayer.setLayoutHint()を呼び出すことで動画プレーヤー画面のサイズを変更するが、
            // 実装によっては、この指定の方が便利なケースもありそう。
            //
            // デフォルト無効
            //      フルスクリーン再生の場合のみ有効
            fitParent = sa.getBoolean(R.styleable.AmvExoVideoPlayer_fitParent, false)
        } finally {
            sa.recycle()
        }
        playerView = findViewById<StyledPlayerView>(R.id.exp_playerView)
        if(showControlBar) {
            playerView.useController = true
        }
        rootView = findViewById(R.id.exp_player_root)
    }

    fun associatePlayer(flag:Boolean) {
        playerView.player = if(flag) model.player else null
    }

    fun bindViewModel(controlPanelModel: ControlPanelModel, binder:Binder) {
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope

        val model = controlPanelModel.playerModel
        this.model = model
        if(controlPanelModel.autoAssociatePlayer) {
            playerView.player = model.player
        }

//        controlPanelModel.hasPlayerOwnership.onEach { ownership->
//            playerView.player = if (ownership) model.player else null
//        }.launchIn(scope)

        val errorMessageView : TextView = findViewById(R.id.exp_errorMessage)
        val progressRing : View = findViewById(R.id.exp_progressRing)
        binder.register(
            VisibilityBinding.create(owner, progressRing, model.isLoading.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible),
            VisibilityBinding.create(owner, errorMessageView, model.isError.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible),
            TextBinding.create(owner,errorMessageView, model.errorMessage.filterNotNull().asLiveData()),
        )

        val matchParent = Size(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        combine(model.playerSize, model.stretchVideoToView) { playerSize, stretch ->
            logger.debug("AmvExoVideoPlayer:Size=(${playerSize.width}w, ${playerSize.height}h (stretch=$stretch))")
            if(stretch) {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                matchParent
            } else {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerSize
            }
        }.onEach(this::updateLayout).launchIn(scope)
    }

    private fun updateLayout(videoSize:Size) {
        playerView.setLayoutSize(videoSize.width, videoSize.height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(w>0 && h>0) {
            logger.debug("width=$w (${context.px2dp(w)}dp), height=$h (${context.px2dp(h)}dp)")
            model.onRootViewSizeChanged(Size(w, h))
        }
    }

//    fun togglePlay() {
//        viewModel.togglePlay()
//    }
//
//    fun play() {
//        viewModel.play()
//    }
//
//    fun pause() {
//        viewModel.pause()
//    }
//
//    fun seekTo(pos:Long) {
//        viewModel.seekTo(pos)
//    }



}