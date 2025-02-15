package eu.darken.sdmse.common.forensics.csi.source.tools

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.source.AppSourceCheck
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.regex.Pattern
import javax.inject.Inject

@Reusable
class LuckyPatcherCheck @Inject constructor(
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo,
) : AppSourceCheck {

    override suspend fun process(areaInfo: AreaInfo): AppSourceCheck.Result {
        val name: String = areaInfo.file.name

        if (!name.endsWith(".odex") && !name.endsWith(".dex")) return AppSourceCheck.Result()

        val pkgName = LUCKYPATCHER_ODDONES.matcher(name)
            .takeIf { it.matches() }
            ?.group(1)
            ?.toPkgId()
            ?: return AppSourceCheck.Result()

        val owners = mutableSetOf<Owner>()
        if (pkgRepo.isInstalled(pkgName)) {
            owners.add(Owner(pkgName))
        }

        BAD_UNCLES
            .map { it.toPkgId() }
            .filter { pkgRepo.isInstalled(it) }
            .forEach {
                owners.add(Owner(it, setOf(Marker.Flag.CUSTODIAN)))
            }

        return AppSourceCheck.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: LuckyPatcherCheck): AppSourceCheck
    }

    companion object {
        private val LUCKYPATCHER_ODDONES = Pattern.compile("^([\\w\\W]+?)(?:-[0-9]{1,4}\\.o?dex)$")
        private val BAD_UNCLES = setOf(
            "com.forpda.lp",
            "com.chelpus.lackypatch",
            "com.dimonvideo.luckypatcher",
            "com.android.vending.billing.InAppBillingService.LUCK"
        )
    }
}