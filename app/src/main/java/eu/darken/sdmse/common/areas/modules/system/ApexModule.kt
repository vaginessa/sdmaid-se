package eu.darken.sdmse.common.areas.modules.system

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.canRead
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.user.UserManager2
import java.io.File
import java.io.IOException
import javax.inject.Inject

@Reusable
class ApexModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val originalPath = File("/apex")
        val resolvedPath = try {
            originalPath.canonicalFile
        } catch (e: IOException) {
            log(TAG, ERROR) { "Failed to resolve canonical apex path" }
            originalPath
        }
        val finalPath = LocalPath.build(resolvedPath)
        return if (finalPath.canRead(gatewaySwitch)) {
            setOf(
                DataArea(
                    type = DataArea.Type.APEX,
                    path = LocalPath.build(resolvedPath),
                    userHandle = userManager2.systemUser,
                    flags = emptySet(),
                )
            )
        } else {
            emptySet()
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ApexModule): DataAreaModule
    }

    companion object {
        val TAG = logTag("DataArea", "Module", "Apex")
    }
}