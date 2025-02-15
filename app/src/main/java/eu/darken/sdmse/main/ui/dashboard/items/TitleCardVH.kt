package eu.darken.sdmse.main.ui.dashboard.items

import android.text.SpannableStringBuilder
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toColored
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.databinding.DashboardTitleItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class TitleCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<TitleCardVH.Item, DashboardTitleItemBinding>(R.layout.dashboard_title_item, parent) {

    override val viewBinding = lazy { DashboardTitleItemBinding.bind(itemView) }

    private val slogan by lazy { getRngSlogan() }

    private val wiggleAnim = AnimationUtils.loadAnimation(context, R.anim.anim_wiggle)

    override val onBindData: DashboardTitleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        icon.apply {
            var clickCount = 0
            setOnClickListener {
                clickCount++
                if (clickCount % 5 == 0) startAnimation(wiggleAnim)
            }
        }

        if (item.upgradeInfo?.isPro == true) {
            val builder = SpannableStringBuilder(getString(R.string.app_name))

            val postFix = getString(R.string.app_name_upgrade_postfix).toColored(context, R.color.colorUpgraded)
            builder.append(" ").append(postFix)

            title.text = builder
        } else {
            title.text = getString(R.string.app_name)
        }

        subtitle.text = getString(slogan)

        betaRibbon.apply {

            isVisible = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE
            text = when (BuildConfigWrap.BUILD_TYPE) {
                BuildConfigWrap.BuildType.DEV -> "              Dev              "
                BuildConfigWrap.BuildType.BETA -> "              Beta              "
                BuildConfigWrap.BuildType.RELEASE -> ""
            }
            setOnClickListener { item.onRibbonClicked() }
        }
    }

    data class Item(
        val upgradeInfo: UpgradeRepo.Info?,
        val onRibbonClicked: () -> Unit,
    ) : DashboardAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

    companion object {
        @StringRes
        fun getRngSlogan() = when ((0..5).random()) {
            0 -> R.string.slogan_message_0
            1 -> R.string.slogan_message_1
            2 -> R.string.slogan_message_2
            3 -> R.string.slogan_message_3
            4 -> R.string.slogan_message_4
            5 -> R.string.slogan_message_5
            else -> throw IllegalArgumentException()
        }
    }
}