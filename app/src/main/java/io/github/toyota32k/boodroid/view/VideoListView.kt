package io.github.toyota32k.boodroid.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.genericBoolBinding
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.databinding.ListItemVideoBinding
import io.github.toyota32k.boodroid.dialog.RatingDialog
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.utils.android.getAttrColor
import io.github.toyota32k.utils.android.getAttrColorAsDrawable
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val model: AppViewModel get() = AppViewModel.instance

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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun bindViewModel(binder: Binder) {
        val owner = binder.lifecycleOwner!!
        binder
        .recyclerViewBindingEx(this) {
            options(
                list = model.videoList,
                inflater = ListItemVideoBinding::inflate,
                bindView = { itemControls, itemBinder, view, videoItem ->
                    itemControls.videoItemText.text = videoItem.name
                    itemBinder
                        .owner(owner)
                        .bindCommand(LiteUnitCommand { onItemTapped(videoItem) }, itemControls.videoItemText)
                        .genericBoolBinding(
                            itemControls.videoItemText,
                            model.currentSource.map { it?.id == videoItem.id }) { view, hit ->
                            val txv = view as TextView
                            if (hit) {
                                txv.background = selectedColor
                                txv.setTextColor(selectedTextColor)
                            } else {
                                txv.background = normalColor
                                txv.setTextColor(normalTextColor)
                            }
                        }
                }
            )
        }
        model.currentSource.onEach {
            // 再生ターゲットが変わったときに、それをリスト内に表示するようスクロール
            if(it!=null) {
                val pos = model.videoList.indexOf(it)
                if (pos >= 0) {
                    scrollToPosition(pos)
                }
                LastPlayInfo.set(owner as Context, it.id, null, null)
            }
        }.launchIn(owner.lifecycleScope)

    }

    private fun onItemTapped(videoItem: IMediaSourceWithChapter) {
        if(model.currentSource.value == videoItem) {
            if(AppViewModel.instance.capability.value.canPutReputation && videoItem is VideoItem) {
                RatingDialog.show(videoItem)
            }
        } else {
            model.videoListSource?.setCurrentSource(videoItem)
        }
    }
}