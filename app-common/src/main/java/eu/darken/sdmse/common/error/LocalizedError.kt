package eu.darken.sdmse.common.error

import android.content.Context
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString

interface HasLocalizedError {
    fun getLocalizedError(): LocalizedError
}

data class LocalizedError(
    val throwable: Throwable,
    val label: CaString,
    val description: CaString,
    val fixAction: ((Context) -> Unit)? = null,
    val infoAction: ((Context) -> Unit)? = null,
) {
    fun asText() = "$label:\n$description"
}

fun Throwable.localized(c: Context): LocalizedError = when {
    this is HasLocalizedError -> this.getLocalizedError()
    localizedMessage != null -> LocalizedError(
        throwable = this,
        label = caString { "${c.getString(R.string.general_error_label)}: ${this::class.simpleName!!}" },
        description = caString { localizedMessage ?: getStackTracePeek() }
    )
    else -> LocalizedError(
        throwable = this,
        label = caString { "${c.getString(R.string.general_error_label)}: ${this::class.simpleName!!}" },
        description = caString { getStackTracePeek() }
    )
}

private fun Throwable.getStackTracePeek() = this.stackTraceToString()
    .lines()
    .filterIndexed { index, _ -> index > 1 }
    .take(3)
    .joinToString("\n")