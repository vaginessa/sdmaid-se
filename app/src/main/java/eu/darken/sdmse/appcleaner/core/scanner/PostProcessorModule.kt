package eu.darken.sdmse.appcleaner.core.scanner

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.containsSegments
import eu.darken.sdmse.common.files.core.segs
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.currentExclusions
import eu.darken.sdmse.exclusion.core.excludeNested
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.reflect.KClass

@Reusable
class PostProcessorModule @Inject constructor(
    private val rootManager: RootManager,
    private val exclusionManager: ExclusionManager,
    @ApplicationContext private val context: Context,
    private val settings: AppCleanerSettings,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun postProcess(apps: Collection<AppJunk>): Collection<AppJunk> {
        log(TAG) { "postProcess(${apps.size})" }
        val minCacheSize = settings.minCacheSizeBytes.value()
        val processed = apps
            .map { checkAliasedItems(it) }
            .map { checkExclusions(it) }
            .map { checkForHiddenModules(it) }
            .filter { it.size >= minCacheSize }
            .filter { !it.isEmpty() }
        log(TAG) { "After post processing: ${apps.size} reduced to ${processed.size}" }
        return processed
    }

    // TODO can we replace this by just working with sets in previous steps?
    private fun checkAliasedItems(before: AppJunk): AppJunk {
        if (before.expendables.isNullOrEmpty()) return before

        log(TAG) { "Before duplicate/aliased check: ${before.expendables.size}" }

        val after = before.copy(
            expendables = before.expendables
                .mapValues { (key, value) ->
                    value.distinctBy { it.path }
                }
                .filter { it.value.isNotEmpty() }
        )

        if (before.expendables != after.expendables!!) {
            val beforeAll = before.expendables.map { it.value }.flatten()
            val afterAll = after.expendables.map { it.value }.flatten()
            log(TAG, WARN) { "Overlapping items: ${beforeAll.subtract(afterAll.toSet())}" }
        }

        log(TAG) { "After duplicate/aliased check: ${after.expendables.size}" }
        return after
    }

    private suspend fun checkExclusions(before: AppJunk): AppJunk {
        // Empty apps don't generate edge cases (and are omitted).
        if (before.expendables.isNullOrEmpty()) return before

        val isRooted = rootManager.isRooted()
        val useShizuku = settings.useShizuku.value()

        val edgeCaseMap = mutableMapOf<KClass<out ExpendablesFilter>, Collection<APathLookup<*>>>()

        if (!isRooted && useShizuku) {
            val edgeCaseSegs = segs(before.pkg.id.name, "cache")
            before.expendables.forEach { (type, paths) ->
                val edgeCases = paths.filter { it.segments.containsSegments(edgeCaseSegs) }
                edgeCaseMap[type] = edgeCases
            }
        }

        val exclusions = exclusionManager.currentExclusions()
            .filter { it.tags.contains(Exclusion.Tag.APPCLEANER) || it.tags.contains(Exclusion.Tag.GENERAL) }
            .filterIsInstance<Exclusion.Path>()

        var after = before.copy(
            expendables = before.expendables.mapValues { (type, paths) ->
                paths.filterNot { path ->
                    exclusions.any { it.match(path) }
                }
            }
        )

        after = after.copy(
            expendables = after.expendables?.mapValues { (type, paths) ->
                var temp = paths
                exclusions.forEach { temp = it.excludeNested(temp) }
                temp
            }
        )

        after = after.copy(
            expendables = after.expendables?.mapValues { (type, paths) ->
                val edges = edgeCaseMap[type] ?: emptySet()
                if (edges.isNotEmpty()) log(TAG, VERBOSE) { "Readding edge cases: $edges" }
                paths.plus(edges)
            }
        )

        if (before != after) {
            log(TAG) { "Before checking exclusions: $before" }
            log(TAG) { "After checking exclusions: $after" }
        }

        return after
    }

    // https://github.com/d4rken/sdmaid-public/issues/2659
    private fun checkForHiddenModules(before: AppJunk): AppJunk {
        if (!hasApiLevel(29)) return before

        return if (HIDDEN_Q_PKGS.contains(before.pkg.id)) {
            before.copy(
                inaccessibleCache = null
            )
        } else {
            before
        }
    }

    companion object {
        val HIDDEN_Q_PKGS = listOf(
            "com.google.android.networkstack.permissionconfig",
            "com.google.android.ext.services",
            "com.google.android.angle",
            "com.google.android.documentsui",
            "com.google.android.modulemetadata",
            "com.google.android.networkstack",
            "com.google.android.permissioncontroller",
            "com.google.android.captiveportallogin"
        ).map { it.toPkgId() }
        val TAG = logTag("AppCleaner", "Scanner", "PostProcessor")
    }

}