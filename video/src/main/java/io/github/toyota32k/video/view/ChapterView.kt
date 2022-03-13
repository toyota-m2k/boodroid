package io.github.toyota32k.video.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.video.model.IChapterList
import io.github.toyota32k.video.model.PlayerModel
import io.github.toyota32k.player.model.Range
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ChapterView @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null, defStyleAttr:Int=0) : View(context, attrs, defStyleAttr) {
    private var mWidth:Int = 0
    private var mHeight:Int = 0
    private val mTickWidth = 1f
//    private var duration:Long = 0L
//    private var chapterList:ChapterList? = null
//    private var disabledRanges:List<Range>? = null

    private lateinit var model: PlayerModel
    private val duration:Long get() = model.naturalDuration.value
    private val chapterList: IChapterList? get() = model.currentSource.value?.chapterList
    private val disabledRanges:List<Range> get() =model.currentSource.value?.disabledRanges ?: emptyList()

//    private lateinit var hasChapters: Flow<Boolean>

    fun bindViewModel(model: PlayerModel, binder: Binder) {
        this.model = model
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope

        combine(model.naturalDuration, model.currentSource) {duration, source->
            duration>0 && source?.hasChapter == true
        }.onEach {
            invalidate()
        }.launchIn(scope)
    }

    private fun time2x(time: Long): Float {
        return if (duration == 0L) 0f else mWidth.toFloat() * time.toFloat() / duration.toFloat()
    }

    val rect = RectF()
    val paint = Paint()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if(canvas==null) return
        if(mWidth==0||mHeight==0) return
        if(!this::model.isInitialized) return

        val width = mWidth.toFloat()
        val height = mHeight.toFloat()
        // background
        rect.set(0f,0f, width, height)
        paint.setColor(Color.rgb(0,0,0x8b))
        canvas.drawRect(rect,paint)

        // chapters
        if(duration<=0L) return
        val list = chapterList?.chapters ?: return
        paint.setColor(Color.WHITE)
        for(c in list) {
            val x = time2x(c.position)
            rect.set(x,0f,x+mTickWidth,height)
            canvas.drawRect(rect,paint)
        }

        // disabled range
        val dr = disabledRanges
        if(dr.isNullOrEmpty()) return

        rect.set(0f,height/2, width, height)
        paint.setColor(Color.rgb(0x80,0xFF, 0))
        canvas.drawRect(rect,paint)

        paint.setColor(Color.GRAY)
        for(r in dr) {
            val end = if (r.end == 0L) duration else r.end
            val x1 = time2x(r.start)
            val x2 = time2x(end)
            rect.set(x1,height/2f,x2,height)
            canvas.drawRect(rect,paint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(w>0 && h>=0 && (mWidth!=w || mHeight!=h)) {
            mWidth = w
            mHeight = h
            invalidate()
        }
    }
}