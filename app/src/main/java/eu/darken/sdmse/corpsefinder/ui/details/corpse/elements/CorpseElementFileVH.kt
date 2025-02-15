package eu.darken.sdmse.corpsefinder.ui.details.corpse.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.files.core.joinSegments
import eu.darken.sdmse.common.files.core.removePrefix
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseElementsAdapter
import eu.darken.sdmse.databinding.CorpsefinderCorpseElementFileBinding


class CorpseElementFileVH(parent: ViewGroup) :
    CorpseElementsAdapter.BaseVH<CorpseElementFileVH.Item, CorpsefinderCorpseElementFileBinding>(
        R.layout.corpsefinder_corpse_element_file,
        parent
    ) {

    override val viewBinding = lazy { CorpsefinderCorpseElementFileBinding.bind(itemView) }

    override val onBindData: CorpsefinderCorpseElementFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        when (item.lookup.fileType) {
            FileType.DIRECTORY -> R.drawable.ic_folder
            FileType.SYMBOLIC_LINK -> R.drawable.ic_file_link
            FileType.FILE -> R.drawable.ic_file
        }.run { icon.setImageResource(this) }

        val prefixFree = item.lookup.removePrefix(item.corpse.path)
        primary.text = prefixFree.joinSegments("/")

        secondary.text = when (item.lookup.fileType) {
            FileType.DIRECTORY -> getString(R.string.file_type_directory)
            FileType.SYMBOLIC_LINK -> getString(R.string.file_type_symbolic_link)
            FileType.FILE -> Formatter.formatFileSize(context, item.lookup.size)
        }

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val corpse: Corpse,
        val lookup: APathLookup<*>,
        val onItemClick: (Item) -> Unit,
    ) : CorpseElementsAdapter.Item {

        override val stableId: Long = lookup.hashCode().toLong()
    }

}