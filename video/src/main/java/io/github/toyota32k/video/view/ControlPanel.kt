package io.github.toyota32k.video.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import io.github.toyota32k.bindit.*
import io.github.toyota32k.utils.lifecycleOwner
import io.github.toyota32k.video.R
import io.github.toyota32k.video.common.AmvSettings
import io.github.toyota32k.video.common.formatTime
import io.github.toyota32k.video.model.ControlPanelModel
import io.github.toyota32k.video.model.PlayerModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToLong

class ControlPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), Slider.OnSliderTouchListener, Slider.OnChangeListener {
    companion object {
        val logger get() = AmvSettings.logger
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_control_panel, this)
    }

    private lateinit var model: ControlPanelModel

    fun bindViewModel(model:ControlPanelModel, binder: Binder) {
        this.model = model
        val owner = lifecycleOwner()!!
//        val scope = owner.lifecycleScope

        val playButton = findViewById<ImageButton>(R.id.play_button)
        val pauseButton = findViewById<ImageButton>(R.id.pause_button)
        val prevVideoButton = findViewById<ImageButton>(R.id.prev_video_button)
        val nextVideoButton = findViewById<ImageButton>(R.id.next_video_button)
        val prevChapterButton = findViewById<ImageButton>(R.id.prev_chapter_button)
        val nextChapterButton = findViewById<ImageButton>(R.id.next_chapter_button)
        val seekBackButton = findViewById<ImageButton>(R.id.seek_back_button)
        val seekForwardButton = findViewById<ImageButton>(R.id.seek_forward_button)
        val pinpButton = findViewById<ImageButton>(R.id.pinp_button)
        val fullscreenButton = findViewById<ImageButton>(R.id.fullscreen_button)
//        val closeButton = findViewById<ImageButton>(R.id.close_button)
        val slider = findViewById<Slider>(R.id.slider)

        slider.addOnChangeListener(this)
        slider.addOnSliderTouchListener(this)

        findViewById<ChapterView>(R.id.chapter_view).bindViewModel(model.playerModel, binder)

        binder.register(
            VisibilityBinding.create(owner, playButton, model.playerModel.isPlaying.asLiveData(), BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone),
            VisibilityBinding.create(owner, pauseButton, model.playerModel.isPlaying.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone),

            MultiEnableBinding.create(owner, playButton, pauseButton, seekBackButton, seekForwardButton, fullscreenButton, pinpButton, slider, data=model.playerModel.isReady.asLiveData()),
            MultiEnableBinding.create(owner, prevChapterButton, nextChapterButton, data=model.playerModel.hasChapters.asLiveData()),
            EnableBinding.create(owner, prevVideoButton, model.playerModel.hasPrevious.asLiveData()),
            EnableBinding.create(owner, nextVideoButton, model.playerModel.hasNext.asLiveData()),

            TextBinding.create(owner, findViewById(R.id.counter_label), combine(model.playerModel.playerSeekPosition, model.playerModel.naturalDuration) { pos,dur->formatTime(pos, dur) }.asLiveData()),
            TextBinding.create(owner, findViewById(R.id.duration_label), model.playerModel.naturalDuration.map { formatTime(it,it) }.asLiveData()),

            SliderBinding.create(owner, slider, model.playerModel.playerSeekPosition.map { it.toFloat() }.asLiveData(), min=null, max=model.playerModel.naturalDuration.map { max(100f, it.toFloat())}.asLiveData() ),

            model.commandPlay.connectViewEx(playButton),
            model.commandPause.connectViewEx(pauseButton),
            model.commandNext.connectViewEx(nextVideoButton),
            model.commandPrev.connectViewEx(prevVideoButton),
            model.commandSeekBackward.connectViewEx(seekBackButton),
            model.commandSeekForward.connectViewEx(seekForwardButton),
        )
    }

    private var isPlayingBeforeDragging = false
    private var seekingCount = 0
    @SuppressLint("RestrictedApi")
    override fun onStartTrackingTouch(slider: Slider) {
        logger.debug("seek start: $seekingCount")
        seekingCount++
        isPlayingBeforeDragging = model.playerModel.isPlaying.value
        model.playerModel.pause()
//        model.playerModel.beginFastSeekMode()
    }

    @SuppressLint("RestrictedApi")
    override fun onStopTrackingTouch(slider: Slider) {
        seekingCount--
        logger.debug("seek end : $seekingCount")
//        model.playerModel.endFastSeekMode()
        if(isPlayingBeforeDragging) {
            model.playerModel.play()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if(fromUser) {
            model.playerModel.seekManager.requestedPositionFromSlider.value = value.roundToLong()
        }
    }

}