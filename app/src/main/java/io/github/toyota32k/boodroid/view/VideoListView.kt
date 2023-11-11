package io.github.toyota32k.boodroid.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.GenericBoolBinding
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.command.Command
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.getAttrColor
import io.github.toyota32k.boodroid.common.getAttrColorAsDrawable
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.dialog.RatingDialog
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.boodroid.viewmodel.MainViewModel
import io.github.toyota32k.utils.lifecycleOwner
import io.github.toyota32k.video.common.IAmvSource
import io.github.toyota32k.video.model.PlayerModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class VideoListView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private val normalColor:Drawable
    @ColorInt private val normalTextColor: Int
    private val selectedColor:Drawable
    @ColorInt private val selectedTextColor:Int
    private lateinit var model:PlayerModel

    init {
        context.theme!!.apply {
            normalColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSurface, Color.WHITE)
            normalTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            selectedColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSecondary, Color.BLUE)
            selectedTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSecondary, Color.WHITE)
        }
        layoutManager = LinearLayoutManager(context)
        setHasFixedSize(true)
    }

    fun bindViewModel(model: PlayerModel, binder: Binder) {
        this.model = model
        val owner = lifecycleOwner()!!
//        val scope = owner.lifecycleScope
        val viewModel = MainViewModel.instanceFor(owner as MainActivity)
        binder.register(
            RecyclerViewBinding.create(owner, this, model.videoSources, R.layout.list_item_video) { itemBinder, view, videoItem ->
                val textView = view.findViewById<TextView>(R.id.video_item_text)
                textView.text = videoItem.name
                itemBinder.register(
                    Command().connectAndBind(owner, textView) { onItemTapped(videoItem) },
                    GenericBoolBinding.create(owner, textView, model.currentSource.map { it?.id == videoItem.id }.asLiveData()) { v, hit->
                        val txv = v as TextView
                        if(hit) {
                            txv.background = selectedColor
                            txv.setTextColor(selectedTextColor)
                        } else {
                            txv.background = normalColor
                            txv.setTextColor(normalTextColor)
                        }
                    },
                )
            },
        )
        model.currentSource.onEach {
            // 再生ターゲットが変わったときに、それをリスト内に表示するようスクロール
            if(it!=null) {
                val pos = model.videoSources.indexOf(it)
                if (pos >= 0) {
                    scrollToPosition(pos)
                }
                LastPlayInfo.set(owner, it.id, 0L, true)
            }
        }.launchIn(viewModel.viewModelScope)

    }

    private fun onItemTapped(videoItem:IAmvSource) {
        if(model.currentSource.value == videoItem && videoItem is VideoItem) {
            if(AppViewModel.instance.capability.value.canPutReputation) {
                RatingDialog.show(videoItem)
            }
        } else {
            model.playAt(videoItem)
        }
    }
}