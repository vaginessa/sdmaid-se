package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.Segments
import eu.darken.sdmse.common.pkgs.Pkg

interface ExpendablesFilter {

    suspend fun initialize()

    suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): ExpendablesFilter
    }
}