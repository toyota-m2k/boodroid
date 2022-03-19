package io.github.toyota32k.boodroid.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.boodroid.data.Mark
import io.github.toyota32k.boodroid.data.Rating
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.data.SourceType
import io.github.toyota32k.ytremote.data.*

//class RatingRadioGroup : RadioButtonGroup<Rating>() {
//    override fun id2value(id: Int): Rating? {
//        return Rating.values().find {it.id==id}
//    }
//    override fun value2id(v: Rating): Int {
//        return v.id
//    }
//
//    var rating:Rating?
//        get() = selected
//        set(v) { selected = v }
//}
//
//class MarkToggleGroup : ToggleButtonGroup<Mark>() {
//    override fun id2value(id: Int): Mark? {
//        return Mark.values().find { it.id == id }
//    }
//
//    override fun value2id(v: Mark): Int {
//        return v.id
//    }
//
//    var marks:List<Mark>
//        get() = selected
//        set(v) { selected = v }
//}
//
//class SourceTypeRadioGroup : RawRadioButtonGroup<SourceType>() {
//    override fun id2value(id: Int): SourceType? {
//        return SourceType.values().find {it.id ==id}
//    }
//
//    override fun value2id(v: SourceType): Int {
//        return v.id
//    }
//
//    var sourceType:SourceType?
//        get() = selected
//        set(v) {selected = v}
//}

class SettingViewModel : ViewModel() {
    val activeHost = MutableLiveData<String?>()
    val editingHost = MutableLiveData<String>()
    val hostList = MutableLiveData<ObservableList<String>>(ObservableList())
//    val sourceType = MutableLiveData<SourceType>()

    val sourceType = MutableLiveData(SourceType.DB)
    val rating = MutableLiveData(Rating.NORMAL)
    val markList = MutableLiveData<List<Mark>>(emptyList())
    val commandAddToList = Command()
    val commandCategory = Command()
    val categoryList = CategoryList().apply { update() }

    var prepared:Boolean = false
    fun prepare(context: Context):SettingViewModel {
        if(!prepared) {
            prepared = true
            load(context)
        }
        return this
    }

//    val category = categoryList.currentLabel


//    var srcTypeButton:MutableLiveData<Int>
//        get() = sourceType.map { it.id }
//        set(v) {
//            sourceType.value = SourceType.values().find {it.id==v}
//        }

    private fun hasHost(address:String) : Boolean {
        return null != hostList.value?.find {it==address}
    }

    fun addHost() {
        addHost(address = editingHost.value ?: return)
    }

    private fun addHost(address:String) {
        if(address.isNotBlank() && !hasHost(address)) {
            hostList.value?.add(address)
            activeHost.value = address
        }
    }

    fun removeHost(address:String) {
        hostList.value?.remove(address)
        if(activeHost.value == address) {
            activeHost.value = hostList.value?.firstOrNull()
        }
    }

    val settings: Settings
        get() = Settings(
                activeHost = activeHost.value,
                hostList = hostList.value ?: listOf(),
                sourceType = sourceType.value ?: SourceType.DB,
                rating = rating.value ?: Rating.NORMAL,
                marks = markList.value?: emptyList(),
                category = categoryList.category)

    fun load(context:Context) {
        val s = Settings.load(context)
        activeHost.value = s.activeHost ?: ""
        hostList.value = ObservableList.from(s.hostList)
        sourceType.value = s.sourceType
        rating.value = s.rating
        markList.value = s.marks
        categoryList.category = s.category
    }

    fun save(context:Context):Boolean {
        val s = settings
        if(!s.isValid) {
            return false
        }
        s.save(context)
        return true
    }

    companion object {
        fun instanceFor(activity: ViewModelStoreOwner):SettingViewModel {
            return ViewModelProvider(activity, ViewModelProvider.NewInstanceFactory()).get(SettingViewModel::class.java)
        }
    }
}
