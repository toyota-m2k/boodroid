package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.data.BooTubeDiscovery
import io.github.toyota32k.boodroid.data.HostAddressEntity
import io.github.toyota32k.boodroid.databinding.DialogHostAddressBinding
import io.github.toyota32k.boodroid.databinding.ListItemDiscoveredServerBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class HostAddressDialog : UtDialogEx() {
    class HostAddressDialogViewModel : UtDialogViewModel() {
        val name = MutableStateFlow("")
        val address = MutableStateFlow("")

        // mDNS で発見したサーバ由来のメタ情報。手入力に切替えたら null/false にリセットされる。
        var serviceName: String? = null
        var fingerprint: String? = null
        var httpsOnly: Boolean = false

        // RecyclerView 用の発見リスト本体
        val discoveredServers = ObservableList<BooTubeDiscovery.DiscoveredServer>()

        // 「Searching…」の出し分け用 (ObservableList を Flow 化するのは骨が折れるので別管理)
        val hasDiscoveries = MutableStateFlow(false)

        // BooTubeDiscovery のラッパ。ダイアログ表示中だけ start/stop。
        private var discovery: BooTubeDiscovery? = null

        fun startDiscovery() {
            if (discovery != null) return
            val ctx = BooApplication.instance.applicationContext
            val d = BooTubeDiscovery(ctx)
            discovery = d
            d.services.onEach { list ->
                discoveredServers.replace(list)
                hasDiscoveries.value = list.isNotEmpty()
            }.launchIn(viewModelScope)
            d.start()
        }

        fun stopDiscovery() {
            discovery?.stop()
            discovery = null
        }

        /** 発見リスト中の 1 件をユーザが選択したとき呼ばれる。UI 入力にも反映する。 */
        fun selectDiscovered(server: BooTubeDiscovery.DiscoveredServer) {
            name.value = server.serviceName
            address.value = "${server.host}:${server.port}"
            serviceName = server.serviceName
            fingerprint = server.fingerprint
            httpsOnly = server.isHttps
        }

        /** 手入力で address を編集された場合は mDNS メタ情報を持ち越さない。 */
        fun resetDiscoveryMeta() {
            serviceName = null
            fingerprint = null
            httpsOnly = false
        }

        override fun onCleared() {
            super.onCleared()
            stopDiscovery()
        }
    }

    companion object {
        suspend fun getHostAddress(initialHost: HostAddressEntity?): HostAddressEntity? {
            return UtImmortalTask.awaitTaskResult(HostAddressDialog::class.java.name) {
                val vm = createViewModel<HostAddressDialogViewModel> {
                    if (initialHost != null) {
                        name.value = initialHost.name
                        address.value = initialHost.address
                        serviceName = initialHost.serviceName
                        fingerprint = initialHost.fingerprint
                        httpsOnly = initialHost.httpsOnly
                    }
                }
                if (showDialog(taskName) { HostAddressDialog() }.status.ok) {
                    HostAddressEntity(
                        name = vm.name.value,
                        address = vm.address.value,
                        serviceName = vm.serviceName,
                        fingerprint = vm.fingerprint,
                        httpsOnly = vm.httpsOnly,
                    )
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
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return DialogHostAddressBinding.inflate(inflater.layoutInflater).apply {
            controls = this
            val owner = requireActivity()
            binder.owner(owner)

            // ユーザが address を手で書き換えたら mDNS メタを破棄する
            // (発見元の serviceName と無関係な host を指定された可能性があるため)
            viewModel.address.onEach { newAddr ->
                val matched = viewModel.discoveredServers.any {
                    "${it.host}:${it.port}" == newAddr
                }
                if (!matched) viewModel.resetDiscoveryMeta()
            }.launchIn(viewModel.viewModelScope)

            binder
                .editTextBinding(hostName, viewModel.name)
                .editTextBinding(hostAddress, viewModel.address)
                .dialogRightButtonEnable(viewModel.address.map { it.isNotBlank() })
                .visibilityBinding(
                    emptyDiscoveryLabel,
                    viewModel.hasDiscoveries.map { !it },
                    hiddenMode = VisibilityBinding.HiddenMode.HideByGone
                )
                .recyclerViewBindingEx(discoveredList) {
                    options(
                        list = viewModel.discoveredServers,
                        inflater = ListItemDiscoveredServerBinding::inflate,
                        bindView = { itemControls, itemBinder, _, server ->
                            itemControls.discoveredNameText.text = server.serviceName
                            itemControls.discoveredAddressText.text = "${server.host}:${server.port}"
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

            viewModel.startDiscovery()
        }.root
    }
}
