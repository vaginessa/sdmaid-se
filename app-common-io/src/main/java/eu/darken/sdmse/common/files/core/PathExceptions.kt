package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.io.R
import java.io.File
import java.io.IOException

open class PathException(
    val path: APath,
    message: String = "Error during access.",
    cause: Throwable? = null
) : IOException("$message <-> ${path.path}", cause)

open class ReadException(
    path: APath,
    message: String = "Can't read from path.",
    cause: Throwable? = null
) : PathException(path, message, cause), HasLocalizedError {

    constructor(file: File) : this(RawPath.build(file))

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "ReadException".toCaString(),
        description = caString { it.getString(R.string.general_error_cant_access_msg, path) }
    )
}

class WriteException(
    path: APath,
    message: String = "Can't write to path.",
    cause: Throwable? = null
) : PathException(path, message, cause), HasLocalizedError {

    constructor(file: File) : this(RawPath.build(file))

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "WriteException".toCaString(),
        description = caString { it.getString(R.string.general_error_cant_access_msg, path) }
    )
}