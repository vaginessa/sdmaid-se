package eu.darken.sdmse.appcleaner.core.forensics.sieves.dynamic

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isCaseInsensitive
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.isNotNullOrEmpty
import eu.darken.sdmse.common.pkgs.Pkg


class DynamicSieve @AssistedInject constructor(
    @Assisted private val configs: Set<MatchConfig>,
) {

    init {
        if (configs.isEmpty()) throw IllegalArgumentException("Empty match configs")
    }

    data class MatchConfig(
        val pkgNames: Set<Pkg.Id>? = null,
        val areaTypes: Set<DataArea.Type>? = null,
        val contains: Set<String>? = null,
        val ancestors: Set<String>? = null,
        val patterns: Set<String>? = null,
        val exclusions: Set<String>? = null,
    ) {
        init {
            if (contains.isNullOrEmpty() && ancestors.isNullOrEmpty() && patterns.isNullOrEmpty()) {
                throw IllegalStateException("Underdefined match config")
            }
        }

        val patternCacheCaseInsensitive by lazy {
            patterns?.map { Regex(it, RegexOption.IGNORE_CASE) }
        }
        val patternCacheCaseSensitive by lazy {
            patterns?.map { Regex(it) }
        }
    }

    fun matches(
        pkgId: Pkg.Id,
        areaType: DataArea.Type,
        target: Segments,
    ): Boolean = configs.any { it.match(pkgId, areaType, target) }

    private fun DynamicSieve.MatchConfig.match(
        pkgId: Pkg.Id,
        areaType: DataArea.Type,
        target: Segments,
    ): Boolean {
        if (!pkgNames.isNullOrEmpty() && !pkgNames.contains(pkgId)) {
            return false
        }

        if (!areaTypes.isNullOrEmpty() && !areaTypes.contains(areaType)) {
            return false
        }


        val ignoreCase = areaType.isCaseInsensitive

        exclusions?.takeIf { it.isNotEmpty() }?.let { excls ->
            val excluded = excls.any {
                target.containsSegments(it.toSegs(), ignoreCase = ignoreCase, allowPartial = true)
            }
            if (excluded) return@match false
        }

        val ancestorsCondition = ancestors
            ?.takeIf { it.isNotEmpty() }
            ?.let { starters ->
                starters.any { target.startsWith(it.toSegs(), ignoreCase = ignoreCase) }
            }
            ?: true

        val containsCondition = contains
            ?.takeIf { it.isNotEmpty() }
            ?.let { contains ->
                contains.any {
                    target.containsSegments(
                        it.toSegs(),
                        ignoreCase = ignoreCase,
                        allowPartial = true
                    )
                }
            }
            ?: true

        val regexCondition = when {
            ignoreCase && patternCacheCaseInsensitive.isNotNullOrEmpty() -> {
                patternCacheCaseInsensitive!!.any { it.matches(target.joinSegments()) }
            }
            !ignoreCase && patternCacheCaseSensitive.isNotNullOrEmpty() -> {
                patternCacheCaseInsensitive!!.any { it.matches(target.joinSegments()) }
            }
            else -> true
        }

        return ancestorsCondition && containsCondition && regexCondition
    }

    @AssistedFactory
    interface Factory {
        fun create(configs: Set<DynamicSieve.MatchConfig>): DynamicSieve
    }
}