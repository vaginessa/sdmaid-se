package eu.darken.sdmse.common.areas.modules.dalvik

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.canRead
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class DalvikProfileModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val possibleLocation = mutableSetOf<LocalPath>()

        firstPass
            .filter { it.type == DataArea.Type.DATA }
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { LocalPath.build(it.path as LocalPath, "dalvik-cache", "profiles") }
            .run { possibleLocation.addAll(this) }

        firstPass
            .filter { it.type == DataArea.Type.DOWNLOAD_CACHE }
            .map { LocalPath.build(it.path as LocalPath, "dalvik-cache", "profiles") }
            .run { possibleLocation.addAll(this) }

        return possibleLocation
            .filter { gateway.exists(it, mode = LocalGateway.Mode.ROOT) }
            .map {
                DataArea(
                    type = DataArea.Type.DALVIK_PROFILE,
                    path = it,
                    userHandle = userManager2.systemUser,
                )
            }
            .filter {
                val canRead = it.path.canRead(gatewaySwitch)
                if (!canRead) log(TAG) { "Can't read $it" }
                canRead
            }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikProfileModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "DalvikProfile")
    }
}