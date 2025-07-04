package io.github.toyota32k.boodroid.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.PackageUtil
import io.github.toyota32k.boodroid.data.HostAddressEntity
import io.github.toyota32k.boodroid.data.ServerCapability
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.data.SettingsOnServer
import io.github.toyota32k.boodroid.data.SourceType
import io.github.toyota32k.boodroid.dialog.HostAddressDialog
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

class HostSettingsViewModel : UtDialogViewModel() {
    val activeHost = MutableStateFlow<HostAddressEntity?>(null)
//    val editingHost = MutableLiveData<String>()
    val hostList = ObservableList<HostAddressEntity>()
    val hostCount = MutableStateFlow<Int>(0)
//    val sourceType = MutableLiveD                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     ata<SourceType>()

    val sourceType = MutableStateFlow(SourceType.DB)
//    val theme = MutableStateFlow(ThemeSetting.SYSTEM)
//    val useDynamicColor = MutableStateFlow(false)
//    val colorVariation = MutableStateFlow(ColorVariation.PINK)
//    val showTitleOnScreen = MutableStateFlow<Boolean>(false)
    val commandAddToList = LiteUnitCommand(this::addHost)
    val commandCategory = LiteUnitCommand()

    val rating = MutableStateFlow(0)
    val marks = MutableStateFlow<List<Int>>(emptyList())
    val category = MutableStateFlow("All")
    lateinit var settingsOnServer: MutableMap<String,SettingsOnServer>
    val capability = MutableStateFlow<ServerCapability?>(null)



    lateinit var version : String
    val context:Context get() = BooApplication.instance.applicationContext
    lateinit var originalSettings: Settings

    var prepared: Boolean = false
    fun prepare(): HostSettingsViewModel {
        if (!prepared) {
            prepared = true
            load()
            version = "${PackageUtil.appName(context)} v${PackageUtil.getVersion(context)}"
        }
        return this
    }

    var result:Boolean = false
    val commandComplete = LiteUnitCommand()

    init {
        hostList.addListenerForever {
            if(hostCount.value != it.list.size) {
                hostCount.value = it.list.size
            }
        }
    }

//    val category = categoryList.currentLabel


//    var srcTypeButton:MutableLiveData<Int>
//        get() = sourceType.map { it.id }
//        set(v) {
//            sourceType.value = SourceType.values().find {it.id==v}
//        }

//    fun hasHost(entity: HostAddressEntity): Boolean {
//        return null != hostList.find { it.address == entity.address }
//    }

//    fun addHost() {
//        viewModelScope.launch {
//            HostAddressDialog.getHostAddress()
//        }
//        addHost(address = editingHost.value ?: return)
//    }

    private fun addHost(entity: HostAddressEntity) {
        if(entity.address.isBlank()) return
        val org = hostList.find { it.address == entity.address }
        if(org!=null) {
            if(entity.name.isBlank() || entity.name == org.name) return
            hostList.remove(org)
        }
        hostList.add(entity)
        activeHost.value = entity
    }

    fun addHost() {
        viewModelScope.launch {
            val v = HostAddressDialog.getHostAddress(activeHost.value)
            if(v!=null && v.address.isNotBlank()) {
                addHost(v)
            }
        }
    }

    fun removeHost(entity: HostAddressEntity) {
        viewModelScope.launch {
            if(confirm(R.string.confirm_remove_host)) {
                val activeHostIndex = hostList.indexOf(activeHost.value)
                val index = hostList.indexOfFirst { it.address == entity.address }
                if(index>=0) {
                    hostList.removeAt(index)
                    if(index == activeHostIndex) {
                        activeHost.value = hostList.firstOrNull()
                    }
                }
            }
        }
    }

    fun editHost(entity: HostAddressEntity) {
        viewModelScope.launch {
            val v = HostAddressDialog.getHostAddress(entity)
            if(v!=null && entity!=v &&  v.address.isNotBlank()) {
                hostList.remove(entity)
                hostList.add(v)
                if(activeHost.value == null || activeHost.value==entity) {
                    activeHost.value = v
                }
            }
        }
    }

    fun checkOnClosing(done:Boolean) {
        immortalCoroutineScope.launch {
//            val editingHost = editingHost.value
//            val hostList = hostList
//            if (!editingHost.isNullOrBlank() && !hasHost(editingHost)) {
//                if (!confirm(R.string.confirm_close_on_editing_host)) {
//                    return@launch
//                }
//            }
            if (done && hostList.isEmpty()) {
                if (!confirm(R.string.confirm_close_with_no_host)) {
                    return@launch
                }
            }
            if(done) {
                save()  // save の中で AppViewModelに設定される
//                AppViewModel.instance.settings = settings
            }

            result = done
            commandComplete.invoke()
        }
    }

    val settings: Settings
        get() = Settings(originalSettings,
            activeHostIndex = hostList.indexOfFirst { it.address == activeHost.value?.address },
            hostList = hostList,
            sourceType = sourceType.value,
//            theme = theme.value,
//            useDynamicColor = useDynamicColor.value,
//            colorVariation = colorVariation.value,
//            showTitleOnScreen = showTitleOnScreen.value,
            settingsOnServer = settingsOnServer.toMap()
        )

    fun load() {
        originalSettings = Settings.load(context)
        val s = originalSettings
        activeHost.value = if(0<=s.activeHostIndex && s.activeHostIndex<s.hostList.size) s.hostList[s.activeHostIndex] else null
        hostList.replace(s.hostList)
        sourceType.value = s.sourceType
//        theme.value = s.theme
//        useDynamicColor.value = s.useDynamicColor
//        colorVariation.value = s.colorVariation
//        showTitleOnScreen.value = s.showTitleOnScreen

        settingsOnServer = s.settingsOnServer.toMutableMap()
//        val sos = s.settingsOnActiveHost
//        rating.value = sos.minRating
//        marks.value = sos.marks
//        category.value = sos.category

        combine(activeHost,capability, rating,marks,category) {h,a,r,m,c->
            if(h!=null&&a!=null) {
                Pair(h, SettingsOnServer(r,m,c))
            } else null
        }.onEach {
            if (it!=null) {
                settingsOnServer[it.first.address] = it.second
            }
        }.launchIn(viewModelScope)

        activeHost.onEach {
            updateOnHostSelection(it?.address)
        }.launchIn(viewModelScope)

        updateOnHostSelection(s.hostAddress)
    }

    private fun updateOnHostSelection(address:String?) {
        rating.value = 0
        marks.value = emptyList()
        category.value = "All"
        capability.value = null
        if(address==null) {
            return
        }
        loadServerCapability(address)
    }
    private fun loadServerCapability(address:String) {
        viewModelScope.launch {
            val cap = ServerCapability.get(address) ?: return@launch
            val sos = settingsOnServer[address] ?: SettingsOnServer.clean
            if(address != activeHost.value?.address) return@launch

            if (cap.ratingList.isValidRating(sos.minRating)) {
                rating.value = sos.minRating
            }
            if (cap.markList.isValidMarks(sos.marks)) {
                marks.value = sos.marks
            }
            if (cap.categoryList.isValidCategory(sos.category)) {
                category.value = sos.category
            }
            capability.value = cap
        }
    }
    fun onActiveHostSelected(host:HostAddressEntity) {
        if(activeHost.value != host) {
            // host changed
            activeHost.value = host
        } else if(capability.value == null) {
            // capability not loaded
            loadServerCapability(host.address)
        }
    }


    private fun save(): Boolean {
        val s = settings
        s.save(context)
        return true
    }

    companion object {
//        fun instanceFor(activity: ViewModelStoreOwner):SettingViewModel {
//            return ViewModelProvider(activity, ViewModelProvider.NewInstanceFactory()).get(SettingViewModel::class.java)
//        }
//        /**
//         * タスク開始時の初期化用
//         */
//        fun createBy(task: IUtImmortalTask, initialize: ((SettingViewModel) -> Unit)? = null): SettingViewModel
//            = UtImmortalViewModelHelper.createBy(SettingViewModel::class.java, task, initialize)
//
//        /**
//         * ダイアログから取得する用
//         */
//        fun instanceFor(dialog: IUtDialog): SettingViewModel
//            = UtImmortalViewModelHelper.instanceFor(SettingViewModel::class.java, dialog)

        fun getString(@StringRes id:Int):String =
            BooApplication.instance.getString(id)

        suspend fun confirm(@StringRes id:Int):Boolean {
            return UtImmortalTask.awaitTaskResult("confirm") {
                showOkCancelMessageBox(getString(R.string.app_name), getString(id))
            }
        }

    }
}
