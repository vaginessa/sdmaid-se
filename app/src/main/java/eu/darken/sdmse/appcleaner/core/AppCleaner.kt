package eu.darken.sdmse.appcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.automation.core.errors.AutomationUnavailableException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
    private val appScannerProvider: Provider<AppScanner>,
    private val automationController: AutomationController,
    private val exclusionManager: ExclusionManager,
) : SDMTool, Progress.Client {

    private val usedResources = setOf(fileForensics, gatewaySwitch, pkgOps)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    override val type: SDMTool.Type = SDMTool.Type.APPCLEANER

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as AppCleanerTask
        log(TAG) { "submit(): Starting...$task" }
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is AppCleanerScanTask -> performScan(task)
                    is AppCleanerDeleteTask -> performDelete(task)
                    is AppCleanerSchedulerTask -> {
                        performScan(AppCleanerScanTask())
                        performDelete(AppCleanerDeleteTask())
                    }
                }
            }
            log(TAG, INFO) { "submit() finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: AppCleanerScanTask): AppCleanerScanTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }

        internalData.value = null

        val scanner = appScannerProvider.get()

        scanner.initialize()

        val results = scanner.withProgress(this) {
            scan()
        }

        internalData.value = Data(
            junks = results,
        )

        return AppCleanerScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.size },
        )
    }

    private suspend fun performDelete(task: AppCleanerDeleteTask): AppCleanerDeleteTask.Result {
        log(TAG, VERBOSE) { "performDelete(): $task" }

        val deletionMap = mutableMapOf<Pkg.Id, Set<APathLookup<*>>>()
        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val targetPkgs = task.targetPkgs ?: snapshot.junks.map { it.pkg.id }

        targetPkgs.forEach { targetPkg ->
            if (task.onlyInaccessible) return@forEach

            val appJunk = snapshot.junks.single { it.pkg.id == targetPkg }

            val targetFilters = task.targetFilters
                ?: appJunk.expendables?.keys
                ?: emptySet()

            val targetFiles: Collection<APathLookup<*>> = task.targetContents
                ?.map { tc ->
                    val allFiles = appJunk.expendables?.values?.flatten() ?: emptySet()
                    allFiles.single { tc.matches(it) }
                }
                ?: appJunk.expendables?.filterKeys { targetFilters.contains(it) }?.values?.flatten()
                ?: emptySet()

            val deleted = mutableSetOf<APathLookup<*>>()

            targetFiles.forEach { targetFile ->
                updateProgressPrimary(caString {
                    it.getString(R.string.general_progress_deleting, targetFile.userReadableName.get(it))
                })
                log(TAG) { "Deleting $targetFile..." }
                targetFile.deleteAll(gatewaySwitch) {
                    updateProgressSecondary(it.userReadablePath)
                    true
                }
                log(TAG) { "Deleted $targetFile!" }
                deleted.add(targetFile)
            }

            deletionMap[appJunk.identifier] = deleted
        }

        updateProgressPrimary(R.string.appcleaner_automation_loading)
        updateProgressSecondary(CaString.EMPTY)

        val automationTargets = targetPkgs
            .filter { targetPkg -> snapshot.junks.single { it.pkg.id == targetPkg }.inaccessibleCache != null }
        val automationTask = ClearCacheTask(automationTargets)
        val automationResult = try {
            automationController.submit(automationTask) as ClearCacheTask.Result
        } catch (e: AutomationUnavailableException) {
            throw InaccessibleDeletionException(e)
        }

        internalData.value = snapshot.copy(
            junks = snapshot.junks
                .map { appJunk ->
                    // Remove all files we deleted or children of deleted files
                    appJunk.copy(
                        expendables = appJunk.expendables
                            ?.mapValues { (type, typeFiles) ->
                                typeFiles.filter { file ->
                                    val mapContent = deletionMap[appJunk.identifier]
                                    mapContent?.none { it.matches(file) || it.isAncestorOf(file) } ?: true
                                }
                            }
                            ?.filterValues { it.isNotEmpty() },
                        inaccessibleCache = when {
                            automationResult.successful.contains(appJunk.identifier) -> null
                            else -> appJunk.inaccessibleCache
                        },
                    )
                }
                .filter { !it.isEmpty() }
        )

        return AppCleanerDeleteTask.Success(
            deletedCount = deletionMap.values.sumOf { it.size },
            recoveredSpace = deletionMap.values.sumOf { contents -> contents.sumOf { it.size } }
        )
    }

    suspend fun exclude(pkgId: Pkg.Id, path: APath? = null) = toolLock.withLock {
        log(TAG) { "exclude(): $pkgId, $path" }
        if (path != null) {
            val exclusion = PathExclusion(
                path = path.downCast(),
                tags = setOf(Exclusion.Tag.APPCLEANER),
            )
            exclusionManager.add(exclusion)

            val snapshot = internalData.value!!
            internalData.value = snapshot.copy(
                junks = snapshot.junks.map { junk ->
                    if (junk.identifier == pkgId) {
                        junk.copy(
                            expendables = junk.expendables?.entries
                                ?.map { entry -> entry.key to entry.value.filter { !it.matches(path) } }
                                ?.filter { it.second.isNotEmpty() }
                                ?.toMap()
                        )
                    } else {
                        junk
                    }
                }
            )
        } else {
            val exclusion = PackageExclusion(
                pkgId = pkgId,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            )
            exclusionManager.add(exclusion)

            val snapshot = internalData.value!!
            internalData.value = snapshot.copy(
                junks = snapshot.junks - snapshot.junks.single { it.identifier == pkgId }
            )
        }
    }

    data class Data(
        val junks: Collection<AppJunk>
    ) {
        val totalSize: Long get() = junks.sumOf { it.size }
        val totalCount: Int get() = junks.sumOf { it.itemCount }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppCleaner): SDMTool
    }

    companion object {
        private val TAG = logTag("AppCleaner")
    }
}