package eu.darken.sdmse.main.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.doNavigate
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DashboardFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment3(R.layout.dashboard_fragment) {

    override val vm: DashboardFragmentVM by viewModels()
    override val ui: DashboardFragmentBinding by viewBinding()

    @Inject lateinit var dashAdapter: DashboardAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.list.setupDefaults(dashAdapter, dividers = false)

        vm.listItems.observe2(ui) {
            dashAdapter.update(it)
        }

        ui.bottomAppBar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_upgrade -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment())
                        true
                    }
                    R.id.menu_action_settings -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToSettingsContainerFragment())
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }
        vm.bottomBarState.observe2(ui) { state ->
            log { "BottombarState $state" }
            if (state.activeTasks > 0 || state.queuedTasks > 0) {
                bottomBarText.apply {
                    text = requireContext().getQuantityString2(
                        R.plurals.tasks_activity_active_notification_message,
                        state.activeTasks
                    )
                    append("\n")
                    append(
                        requireContext().getQuantityString2(
                            R.plurals.tasks_activity_queued_notification_message,
                            state.queuedTasks
                        )
                    )
                }
            } else if (state.totalItems > 0 || state.totalSize > 0L) {
                bottomBarText.apply {
                    text = requireContext().getString(
                        R.string.x_space_can_be_freed,
                        Formatter.formatShortFileSize(requireContext(), state.totalSize)
                    )
                    append("\n")
                    append(
                        requireContext().getQuantityString2(R.plurals.result_x_items, state.totalItems)
                    )
                }
            } else {
                bottomBarText.text = ""
            }

            bottomAppBar.menu?.findItem(R.id.menu_action_upgrade)?.let {
                it.isVisible = state.upgradeInfo?.isPro != true
            }

            mainAction.isEnabled = state.actionState != DashboardFragmentVM.BottomBarState.Action.WORKING

            mainAction.setOnClickListener {
                if (state.actionState == DashboardFragmentVM.BottomBarState.Action.DELETE) {
                    MaterialAlertDialogBuilder(requireContext()).apply {
                        setTitle(R.string.general_delete_confirmation_title)
                        setMessage(R.string.dashboard_delete_all_message)
                        setPositiveButton(R.string.general_delete_all_action) { _, _ -> vm.mainAction(state.actionState) }
                        setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    }.show()
                } else {
                    vm.mainAction(state.actionState)
                }
            }

            when (state.actionState) {
                DashboardFragmentVM.BottomBarState.Action.SCAN -> {
                    mainAction.setImageResource(R.drawable.ic_layer_search_24)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorOnPrimaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorPrimaryContainer))
                }
                DashboardFragmentVM.BottomBarState.Action.DELETE -> {
                    mainAction.setImageResource(R.drawable.ic_baseline_delete_sweep_24)
                    mainAction.imageTintList = ColorStateList.valueOf(
                        requireContext().getColorForAttr(R.attr.colorOnError)
                    )
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        requireContext().getColorForAttr(R.attr.colorError)
                    )
                }
                DashboardFragmentVM.BottomBarState.Action.WORKING -> {
                    mainAction.setImageDrawable(null)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorOnSecondaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorSecondaryContainer))
                }
                DashboardFragmentVM.BottomBarState.Action.WORKING_CANCELABLE -> {
                    mainAction.setImageResource(R.drawable.ic_cancel)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorOnTertiaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorTertiaryContainer))
                }
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is DashboardEvents.CorpseFinderDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(R.string.corpsefinder_delete_all_confirmation_message)
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.confirmCorpseDeletion() }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ -> vm.showCorpseFinderDetails() }
                }.show()
                is DashboardEvents.SystemCleanerDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(R.string.systemcleaner_delete_all_confirmation_message)
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.confirmFilterContentDeletion() }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ -> vm.showSystemCleanerDetails() }
                }.show()
                is DashboardEvents.AppCleanerDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(R.string.appcleaner_delete_all_confirmation_message)
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.confirmAppJunkDeletion() }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ -> vm.showAppCleanerDetails() }
                }.show()
                DashboardEvents.SetupDismissHint -> {
                    Snackbar
                        .make(
                            requireView(),
                            R.string.setup_dismiss_hint,
                            Snackbar.LENGTH_LONG
                        )
                        .setAction(R.string.general_undo_action) { _ -> vm.undoSetupHide() }
                        .show()
                }
                is DashboardEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
                DashboardEvents.TodoHint -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.general_todo_msg)
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
