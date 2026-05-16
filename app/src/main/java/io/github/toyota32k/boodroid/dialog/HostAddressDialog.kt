package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.BooTubeDiscovery
import io.github.toyota32k.boodroid.data.HostAddressEntity
import io.github.toyota32k.boodroid.databinding.DialogHostAddressBinding
import io.github.toyota32k.boodroid.databinding.ListItemDiscoveredServerBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class HostAddressDialog : UtDialogEx() {
    class HostAddressDialogViewModel : UtDialogViewModel() {
        val name = MutableStateFlow("")
        val address = MutableStateFlow("")

        data class DiscoveredHostInfo(
            val serviceName: String,
            val fingerprint: String? = null,
            val isHttps: Boolean = false,
            val hostname: String? = null,
        ) {
            companion object {
                fun fromHostAddressEntity(src:HostAddressEntity): DiscoveredHostInfo? {
                    val serviceName = src.serviceName ?: return null
                    return DiscoveredHostInfo(serviceName,src.fingerprint,src.isHttps,src.hostname)
                }
            }
        }

        private var selectedHost: DiscoveredHostInfo? = null

        class DiscoveryModel {
//            // mDNS で発見したサーバ由来のメタ情報。手入力に切替えたら null/false にリセットされる。
//            var serviceName: String? = null
//            var fingerprint: String? = null
//            var isHttps: Boolean = false
//            var hostname: String? = null

            // RecyclerView 用の発見リスト本体
            val discoveredServers = ObservableList<BooTubeDiscovery.DiscoveredServer>()

            // 「Searching…」の出し分け用 (ObservableList を Flow 化するのは骨が折れるので別管理)
            val hasDiscoveries = MutableStateFlow(false)

            // BooTubeDiscovery のラッパ。ダイアログ表示中だけ start/stop。
            private var discovery: BooTubeDiscovery? = null

            fun start(scope: CoroutineScope) {
                if (discovery != null) return
                val ctx = BooApplication.instance.applicationContext
                val d = BooTubeDiscovery(ctx)
                discovery = d
                d.services.onEach { list ->
                    discoveredServers.replace(list)
                    hasDiscoveries.value = list.isNotEmpty()
                }.launchIn(scope)
                d.start()
            }

            fun stop() {
                discovery?.stop()
                discovery = null
            }
        }

        val discoveryModel = DiscoveryModel()

        fun toHostAddressEntity(): HostAddressEntity {
            return HostAddressEntity(
                name = name.value,
                address = address.value,
                serviceName = selectedHost?.serviceName,
                fingerprint = selectedHost?.fingerprint,
                isHttps = selectedHost?.isHttps == true,
                hostname = selectedHost?.hostname,
                )
        }
        fun applyHostAddressEntity(src: HostAddressEntity?) {
            if (src==null) return
            name.value = src.name
            address.value = src.address
            selectedHost = DiscoveredHostInfo.fromHostAddressEntity(src)
        }

        /** 発見リスト中の 1 件をユーザが選択したとき呼ばれる。UI 入力にも反映する。 */
        fun selectDiscovered(server: BooTubeDiscovery.DiscoveredServer) {
            name.value = server.serviceName
            address.value = "${server.host}:${server.port}"
            selectedHost = DiscoveredHostInfo(server.serviceName, server.fingerprint, server.isHttps, server.hostname)
        }

        /** 手入力で address を編集された場合は mDNS メタ情報を持ち越さない。 */
        fun resetDiscoveryMeta() {
            selectedHost = null
        }

        val discovering = MutableStateFlow<Boolean>(false)
        val commandDiscover = LiteUnitCommand {
            // （開始されていなければ）mDNSの検索を開始する
            if (!discovering.value) {
                // ユーザが address を手で書き換えたら mDNS メタを破棄する
                // (発見元の serviceName と無関係な host を指定された可能性があるため)
                address.onEach { newAddr ->
                    val matched = discoveryModel.discoveredServers.any {
                        "${it.host}:${it.port}" == newAddr
                    }
                    if (!matched) resetDiscoveryMeta()
                }.launchIn(viewModelScope)

                discovering.value = true
                discoveryModel.start(viewModelScope)
            }
        }

        override fun onCleared() {
            super.onCleared()
            discoveryModel.stop()
        }
    }

    companion object {
        suspend fun getHostAddress(initialHost: HostAddressEntity?): HostAddressEntity? {
            return UtImmortalTask.awaitTaskResult(HostAddressDialog::class.java.name) {
                val vm = createViewModel<HostAddressDialogViewModel> {
                    applyHostAddressEntity(initialHost)
                }
                if (showDialog(taskName) { HostAddressDialog() }.status.ok) {
                    vm.toHostAddressEntity()
                 } else null
            }
        }
    }

    private val viewModel by lazy { getViewModel<HostAddressDialogViewModel>() }
    private lateinit var controls: DialogHostAddressBinding

    override fun preCreateBodyView() {
        draggable = true
        guardColor = GuardColor.DIM
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        gravityOption = GravityOption.CENTER
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        title = getString(R.string.host_addr_label)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return DialogHostAddressBinding.inflate(inflater.layoutInflater).apply {
            controls = this
            val owner = requireActivity()
            binder
                .owner(owner)
                .editTextBinding(hostName, viewModel.name)
                .editTextBinding(hostAddress, viewModel.address)
                .dialogRightButtonEnable(viewModel.address.map { it.isNotBlank() })
                .bindCommand(viewModel.commandDiscover, discoverButton)
                .combinatorialVisibilityBinding(viewModel.discovering) {
                    inverseGone(discoverButton)
                    straightGone(discoverResultLabel)
                    straightGone(discoverResult)
                }
                .visibilityBinding(
                    emptyDiscoveryLabel,
                    viewModel.discoveryModel.hasDiscoveries.map { !it },
                    hiddenMode = VisibilityBinding.HiddenMode.HideByGone
                )
                .recyclerViewBindingEx(discoveredList) {
                    options(
                        list = viewModel.discoveryModel.discoveredServers,
                        inflater = ListItemDiscoveredServerBinding::inflate,
                        bindView = { itemControls, itemBinder, _, server ->
                            itemControls.discoveredNameText.text = server.serviceName
                            // hostname があれば「TOYOTA-PC.local (192.168.0.153:3501)」形式
                            itemControls.discoveredAddressText.text =
                                if (!server.hostname.isNullOrEmpty())
                                    "${server.hostname} (${server.host}:${server.port})"
                                else
                                    "${server.host}:${server.port}"
                            itemControls.httpsBadge.text = if (server.isHttps) "HTTPS" else "HTTP"
                            itemBinder.reset()
                            itemBinder.owner(owner)
                                .bindCommand(
                                    LiteCommand<BooTubeDiscovery.DiscoveredServer> {
                                        viewModel.selectDiscovered(it)
                                    },
                                    itemControls.discoveredItemContainer,
                                    server,
                                )
                        },
                    )
                }
        }.root
    }
}
