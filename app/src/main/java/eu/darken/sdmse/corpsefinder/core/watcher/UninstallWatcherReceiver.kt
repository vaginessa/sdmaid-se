package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UninstallWatcherReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var corpseFinderSettings: CorpseFinderSettings

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
        }

        val uri = intent.data
        val pkg = uri?.schemeSpecificPart?.toPkgId()
        if (pkg == null) {
            log(TAG, ERROR) { "Package data was null" }
            return
        }

        log(TAG, INFO) { "$pkg was uninstalled" }

        // TODO did we uninstall this?
        if (AppControl.lastUninstalledPkg == pkg) {
            log(TAG, INFO) { "Skipping check, SD Maid was open, we did this" }
            return
        }

        val asyncPi = goAsync()

        appScope.launch {
            if (!corpseFinderSettings.isUninstallWatcherEnabled.value()) {
                log(TAG, WARN) { "Uninstall watcher is disabled in settings, skipping." }
                return@launch
            }

            val scanTask = UninstallWatcherTask(pkg)
            taskManager.submit(scanTask)

            log(TAG) { "Finished watcher trigger" }
            asyncPi.finish()
        }


    }

    companion object {
        internal val TAG = logTag("CorpseFinder", "UninstallWatcher", "Receiver")
    }
}
