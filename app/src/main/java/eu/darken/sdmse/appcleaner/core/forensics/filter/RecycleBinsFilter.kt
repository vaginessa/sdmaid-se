package eu.darken.sdmse.appcleaner.core.forensics.generic

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.sieves.json.JsonBasedSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.Segments
import eu.darken.sdmse.common.files.core.lowercase
import eu.darken.sdmse.common.pkgs.Pkg
import java.util.*
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class RecycleBinsFilter @Inject constructor(
    private val jsonBasedSieveFactory: JsonBasedSieve.Factory
) : ExpendablesFilter {

    private lateinit var sieve: JsonBasedSieve

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
        sieve = jsonBasedSieveFactory.create("expendables/db_trash_files.json")
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        val hierarchy = segments.lowercase()

        // package/trashfile
        if (hierarchy.size == 2 && TRASH_FILES.contains(hierarchy[1])) {
            return true
        }

        // package/files/trashfile
        if (hierarchy.size == 3 && "files" == hierarchy[1] && TRASH_FILES.contains(hierarchy[2])) {
            return true
        }

        //    0      1     2
        // package/.trash/file
        if (hierarchy.size >= 3 && TRASH_FOLDERS.contains(hierarchy[1])) {
            return true
        }

        //    0      1     2     3
        // package/files/.trash/file
        if (hierarchy.size >= 4
            && "files" == hierarchy[1]
            && (TRASH_FOLDERS.contains(hierarchy[2]) || "cache" == hierarchy[2])
        ) {
            return true
        }

        return segments.isNotEmpty() && sieve.matches(pkgId, areaType, segments)
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<RecycleBinsFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterRecycleBinsEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val TRASH_FOLDERS: Collection<String> = listOf(
            ".trash",
            "trash",
            ".trashfiles",
            "trashfiles",
            ".trashbin",
            "trashbin",
            ".recycle",
            "recycle",
            ".recyclebin",
            "recyclebin",
            ".garbage"
        )
        private val TRASH_FILES: Collection<String> = emptyList()
        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "RecycleBins")
    }
}