package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val exclusionStorage: ExclusionStorage,
) {

    private val _exclusions = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        exclusionStorage.load()
    }
    val exclusions: Flow<Collection<Exclusion>> = _exclusions.flow

    suspend fun add(exclusion: Exclusion): Boolean {
        log(TAG) { "add(): $exclusion" }
        _exclusions.updateAsync {
            val newData = this + exclusion
            exclusionStorage.save(newData)
            newData
        }
        return true
    }

    suspend fun remove(id: String): Boolean {
        log(TAG) { "remove(): $id" }
        val target = currentExclusions().single { it.id == id }
        return remove(target)
    }

    suspend fun remove(exclusion: Exclusion): Boolean {
        log(TAG) { "remove(): $exclusion" }
        _exclusions.updateAsync {
            val newData = this - exclusion
            exclusionStorage.save(newData)
            newData
        }
        return true
    }

    companion object {
        private val TAG = logTag("Exclusion", "Manager")
    }
}