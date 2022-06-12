package io.github.toyota32k.video.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.children
import androidx.lifecycle.asLiveData
import com.google.android.material.slider.Slider
import io.github.toyota32k.bindit.*
import io.github.toyota32k.boodroid.common.getColorAsDrawable
import io.github.toyota32k.boodroid.common.getColorAwareOfTheme
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.lifecycleOwner
import io.github.toyota32k.video.R
import io.github.toyota32k.video.common.AmvSettings
import io.github.toyota32k.video.common.formatTime
import io.github.toyota32k.video.model.ControlPanelModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToLong

class ControlPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), Slider.OnChangeListener {
    companion object {
        val logger by lazy { UtLog("CP", AmvSettings.logger) }
    }

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.ControlPanel, defStyleAttr,0)
        val panelBackground = sa.getColorAsDrawable(R.styleable.ControlPanel_panelBackgroundColor, context.theme, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val panelText = sa.getColorAwareOfTheme(R.styleable.ControlPanel_panelTextColor, context.theme, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        val buttonEnabled = sa.getColorAwareOfTheme(R.styleable.ControlPanel_buttonTintColor, context.theme, com.google.android.material.R.attr.colorPrimaryVariant, Color.WHITE)
        val disabledDefault = Color.argb(0x80, Color.red(panelText), Color.green(panelText), Color.blue(panelText))
        val buttonDisabled = sa.getColor(R.styleable.ControlPanel_buttonDisabledTintColor, disabledDefault)
        val buttonTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(),
            ),
            intArrayOf(buttonEnabled, buttonDisabled)
        )
        sa.recycle()

        LayoutInflater.from(context).inflate(R.layout.v2_control_panel, this)
        findViewById<View>(R.id.control_panel_root).background = panelBackground
        findViewById<TextView>(R.id.counter_label).setTextColor(panelText)
        findViewById<TextView>(R.id.duration_label).setTextColor(panelText)

        val buttons = findViewById<ViewGroup>(R.id.control_buttons)
        buttons.children.forEach { (it as? ImageButton)?.imageTintList = buttonTint }
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
        val collapseButton = findViewById<ImageButton>(R.id.collapse_button)
//        val closeButton = findViewById<ImageButton>(R.id.close_button)
        val slider = findViewById<Slider>(R.id.slider)

        slider.addOnChangeListener(this)
//        slider.addOnSliderTouchListener(this)

        findViewById<ChapterView>(R.id.chapter_view).bindViewModel(model.playerModel, binder)

        binder.register(
            VisibilityBinding.create(owner, playButton, model.playerModel.isPlaying.asLiveData(), BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone),
            VisibilityBinding.create(owner, pauseButton, model.playerModel.isPlaying.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone),
            VisibilityBinding.create(owner, fullscreenButton, model.windowMode.map { it!=ControlPanelModel.WindowMode.FULLSCREEN }.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone),
            VisibilityBinding.create(owner, collapseButton, model.windowMode.map { it!=ControlPanelModel.WindowMode.NORMAL }.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone),

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
            model.commandNextChapter.connectViewEx(nextChapterButton),
            model.commandPrevChapter.connectViewEx(prevChapterButton),
            model.commandSeekBackward.connectViewEx(seekBackButton),
            model.commandSeekForward.connectViewEx(seekForwardButton),
            model.commandFullscreen.connectViewEx(fullscreenButton),
            model.commandPinP.connectViewEx(pinpButton),
            model.commandCollapse.connectViewEx(collapseButton),
        )
    }

//    private var isPlayingBeforeDragging = false
//    private var seekingCount = 0
//    @SuppressLint("RestrictedApi")
//    override fun onStartTrackingTouch(slider: Slider) {
//        logger.debug("seek start: $seekingCount")
//        seekingCount++
//        isPlayingBeforeDragging = model.playerModel.isPlaying.value
////        model.playerModel.pause()
////        model.playerModel.beginFastSeekMode()
//    }
//
//    @SuppressLint("RestrictedApi")
//    override fun onStopTrackingTouch(slider: Slider) {
//        seekingCount--
//        logger.debug("seek end : $seekingCount")
////        model.playerModel.endFastSeekMode()
//        if(isPlayingBeforeDragging) {
//            model.playerModel.play()
//        }
//    }

    @SuppressLint("RestrictedApi")
    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if(fromUser) {
            model.playerModel.seekManager.requestedPositionFromSlider.value = value.roundToLong()
        }
    }

}