package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardDebugItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class DebugCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DebugCardVH.Item, DashboardDebugItemBinding>(R.layout.dashboard_debug_item, parent) {

    override val viewBinding = lazy { DashboardDebugItemBinding.bind(itemView) }

    override val onBindData: DashboardDebugItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        traceEnabled.apply {
            setOnCheckedChangeListener(null)
            isChecked = item.isTraceEnabled
            setOnCheckedChangeListener { _, isChecked -> item.onTraceEnabled(isChecked) }
        }
        dryrunEnabled.apply {
            setOnCheckedChangeListener(null)
            isChecked = item.isDryRunEnabled
            setOnCheckedChangeListener { _, isChecked -> item.onDryRunEnabled(isChecked) }
        }
        pkgsReloadAction.setOnClickListener { item.onReloadPkgs() }
        areasReloadAction.setOnClickListener { item.onReloadAreas() }
        testAction.setOnClickListener { item.onRunTest() }
        testAction.isVisible = BuildConfigWrap.DEBUG
        logviewAction.isVisible = BuildConfigWrap.DEBUG
        logviewAction.setOnClickListener { item.onViewLog() }
    }

    data class Item(
        val isDryRunEnabled: Boolean,
        val onDryRunEnabled: (Boolean) -> Unit,
        val isTraceEnabled: Boolean,
        val onTraceEnabled: (Boolean) -> Unit,
        val onReloadAreas: () -> Unit,
        val onReloadPkgs: () -> Unit,
        val onRunTest: () -> Unit,
        val onViewLog: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}