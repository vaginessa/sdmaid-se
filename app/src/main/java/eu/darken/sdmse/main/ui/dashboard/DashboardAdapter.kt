package eu.darken.sdmse.main.ui.dashboard

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.appcleaner.ui.AppCleanerDashCardVH
import eu.darken.sdmse.appcontrol.ui.AppControlDashCardVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.scheduler.ui.SchedulerDashCardVH
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerDashCardVH
import javax.inject.Inject


class DashboardAdapter @Inject constructor() :
    ModularAdapter<DashboardAdapter.BaseVH<DashboardAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DashboardAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is TitleCardVH.Item }) { TitleCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DebugCardVH.Item }) { DebugCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SetupCardVH.Item }) { SetupCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is UpgradeCardVH.Item }) { UpgradeCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DataAreaCardVH.Item }) { DataAreaCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is CorpseFinderDashCardVH.Item }) { CorpseFinderDashCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SystemCleanerDashCardVH.Item }) { SystemCleanerDashCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppCleanerDashCardVH.Item }) { AppCleanerDashCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppControlDashCardVH.Item }) { AppControlDashCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SchedulerDashCardVH.Item }) { SchedulerDashCardVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}