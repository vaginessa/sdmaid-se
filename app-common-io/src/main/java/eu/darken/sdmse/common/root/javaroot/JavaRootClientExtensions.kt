package eu.darken.sdmse.common.root.javaroot

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log


@Suppress("UNCHECKED_CAST")
suspend fun <R, T> JavaRootClient.runModuleAction(
    moduleClass: Class<out R>,
    action: suspend (R) -> T
): T = runSessionAction { session ->
    if (Bugs.isTrace) log(JavaRootClient.TAG, VERBOSE) { "runModuleAction(moduleClass=$moduleClass, action=$action)" }
    val module = session.clientModules.single { moduleClass.isInstance(it) } as R
    return@runSessionAction action(module)
}