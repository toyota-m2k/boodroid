package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.logger.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * アクティブホスト (active host) の IP/port を mDNS-SD で常時監視し、
 * 変動があれば自動で `Settings` の `activeHost.address` を更新するトラッカ。
 *
 * 動作概要:
 *   - [BooTubeDiscovery] のシングルトン的なインスタンスをアプリ起動中ずっと走らせる
 *   - `discovery.services` (StateFlow) を購読
 *   - 現在の `AppViewModel.instance.settings.activeHost.serviceName` と一致するエントリの
 *     IP/port が変わった瞬間、settings を更新して永続化する
 *
 * 利点:
 *   - DHCP リース更新等で BooTube PC の IP が変わっても、ユーザが何もしなくても追従できる
 *   - API 34+ 端末では `NsdManager.registerServiceInfoCallback` 経由で push 通知的に更新される
 *
 * 注意:
 *   - `AppViewModel.instance.settings = newSettings` の setter は `refreshCommand` を発火するため、
 *     IP 変動時は動画リスト再取得が走る (UX 上は再表示で済むので許容範囲)
 *   - 並行して [HostAddressDialog] や [BooTubeDiscovery.resolveOnce] が独自に discovery を起動する
 *     ことがあるが、`NsdManager` は複数 listener を許容するので衝突しない
 */
object ActiveHostTracker {
    private val logger = UtLog("HostTracker", BooApplication.logger)

    private var discovery: BooTubeDiscovery? = null
    private var collectorJob: Job? = null

    fun start(scope: CoroutineScope) {
        synchronized(this) {
            if (discovery != null) return
            val ctx = BooApplication.instance.applicationContext
            val d = BooTubeDiscovery(ctx)
            discovery = d
            d.start()
            collectorJob = scope.launch {
                d.services.collect { servers ->
                    handleServerUpdates(servers)
                }
            }
            logger.debug("ActiveHostTracker started")
        }
    }

    fun stop() {
        synchronized(this) {
            collectorJob?.cancel()
            collectorJob = null
            discovery?.stop()
            discovery = null
            logger.debug("ActiveHostTracker stopped")
        }
    }

    private fun handleServerUpdates(servers: List<BooTubeDiscovery.DiscoveredServer>) {
        val cur = try { AppViewModel.instance.settings } catch (_: Throwable) { return }
        val active = cur.activeHost ?: return
        val svc = active.serviceName ?: return
        val match = servers.firstOrNull { it.serviceName == svc } ?: return

        val newAddr = "${match.host}:${match.port}"
        val newFp = match.fingerprint ?: active.fingerprint
        val newHostname = match.hostname ?: active.hostname
        val sameAddr = newAddr == active.address
        val sameFp = newFp == active.fingerprint
        val sameHostname = newHostname == active.hostname
        if (sameAddr && sameFp && sameHostname) return // no-op

        val updated = active.copy(
            address = newAddr,
            fingerprint = newFp,
            hostname = newHostname,
            httpsOnly = match.isHttps || active.httpsOnly,
        )
        val newList = cur.hostList.map { if (it.address == active.address) updated else it }
        val idx = newList.indexOfFirst { it.address == updated.address }
        val newSettings = Settings(cur, hostList = newList, activeHostIndex = idx)
        try {
            newSettings.save(BooApplication.instance.applicationContext)
            logger.debug("Active host auto-refreshed: ${active.address} -> $newAddr (svc=$svc)")
        } catch (e: Throwable) {
            logger.stackTrace(e, "Failed to persist auto-refreshed active host")
        }
    }
}
