package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.video.model.ControlPanelModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainViewModel : ViewModel() {
    companion object {
        fun instanceFor(owner: MainActivity): MainViewModel {
            return ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[MainViewModel::class.java].prepare(owner)
        }
    }


    var prepared = false

    private fun prepare(@Suppress("UNUSED_PARAMETER") owner:MainActivity):MainViewModel {
        if(!prepared) {
            prepared = true
        }
        return this
    }

    override fun onCleared() {
        super.onCleared()
    }
}