package eu.darken.sdmse.common.debug

import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.navigation.navVia
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.DashboardFragmentDirections
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

class DebugCardProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val debugSettings: DebugSettings,
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val dataAreaManager: DataAreaManager,
    private val automationController: AutomationController,
) {

    fun create(vm: ViewModel3) = combine(
        debugSettings.isDebugMode.flow.distinctUntilChanged(),
        debugSettings.isTraceMode.flow.distinctUntilChanged(),
        debugSettings.isDryRunMode.flow.distinctUntilChanged(),
    ) { isDebug, isTrace, isDryRun ->
        if (!isDebug) return@combine null
        DebugCardVH.Item(
            isDryRunEnabled = isDryRun,
            onDryRunEnabled = { debugSettings.isDryRunMode.valueBlocking = it },
            isTraceEnabled = isTrace,
            onTraceEnabled = { debugSettings.isTraceMode.valueBlocking = it },
            onReloadAreas = {
                vm.launch { dataAreaManager.reload() }
            },
            onReloadPkgs = {
                vm.launch {
                    pkgRepo.reload()
                }
            },
            onRunTest = {
                appScope.launch {
                    val pkgs = pkgRepo.currentPkgs()
                        .filter { it is NormalPkg }
                        .map { it.id }
                    automationController.submit(ClearCacheTask(pkgs))
                }
            },
            onViewLog = {
                DashboardFragmentDirections.actionDashboardFragmentToLogViewFragment().navVia(vm)
            }
        )
    }
}