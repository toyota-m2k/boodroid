package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import io.github.toyota32k.video.model.ControlPanelModel

class MainViewModel : ViewModel() {
    lateinit var controlPanelModel:ControlPanelModel

    companion object {
        fun instanceFor(owner: ViewModelStoreOwner): MainViewModel {


            return MainViewModel().apply {

            }
        }
    }
}