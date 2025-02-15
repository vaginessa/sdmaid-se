package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.view.ContextThemeWrapper
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.crawler.AutomationHost
import eu.darken.sdmse.automation.core.crawler.CrawlerException
import eu.darken.sdmse.automation.core.crawler.getRoot
import eu.darken.sdmse.automation.ui.AutomationControlView
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class AutomationService : AccessibilityService(), AutomationHost, Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    @Inject lateinit var dispatcher: DispatcherProvider
    private lateinit var serviceScope: CoroutineScope
    override val scope: CoroutineScope get() = serviceScope

    @Inject lateinit var automationProcessorFactory: AutomationProcessor.Factory
    private lateinit var automationProcessor: AutomationProcessor

    @Inject lateinit var generalSettings: GeneralSettings

    private var currentOptions = AutomationHost.Options()
    private lateinit var windowManager: WindowManager
    private val mainThread = Handler(Looper.getMainLooper())

    private val automationEvents = MutableSharedFlow<AccessibilityEvent>()
    override val events: Flow<AccessibilityEvent> = automationEvents

    private var controlView: AutomationControlView? = null

    override fun onCreate() {
        log(TAG) { "onCreate(application=$application)" }
        super.onCreate()

        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "Missing consent for accessibility service" }
            disableSelf()
            return
        }

//        var injected = false
//
//        if (!injected) {
//            // https://issuetracker.google.com/issues/37137009
//            (application as? HasManualServiceInjector)?.let {
//                log(TAG) { "Injecting via service.application." }
//                it.serviceInjector().inject(this@AutomationService)
//                injected = true
//            }
//        }
//
//        if (!injected) {
//            // We can try this but there is a slim chance this will work if the above failed.
//            log(TAG, WARN) { "Injecting via fallback singleton access." }
//            App.require().serviceInjector().inject(this)
//        }

        serviceScope = CoroutineScope(dispatcher.IO + SupervisorJob())

        automationProcessor = automationProcessorFactory.create(this)

        progress
            .mapLatest { progressData ->
                mainThread.post {
                    val acv = controlView
                    when {
                        progressData == null && acv != null -> {
                            log(TAG) { "Removing controlview: $acv" }
                            try {
                                windowManager.removeView(acv)
                            } catch (e: Exception) {
                                log(TAG, WARN) {
                                    "Failed to remove controlview, possibly failed to add it in the first place: $acv"
                                }
                            }
                            controlView = null
                        }
                        progressData != null && acv == null -> {
                            log(TAG) { "Adding controlview" }
                            val view = AutomationControlView(ContextThemeWrapper(this, R.style.AppTheme))
                            log(TAG) { "Adding new controlview: $view" }
                            view.setCancelListener {
                                view.showOverlay(false)
                                currentTaskJob?.cancel()
                            }

                            try {
                                windowManager.addView(view, controlLp)
                                controlView = view
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Failed to add control view to window: ${e.asLog()}" }
                            }
                        }
                        acv != null -> {
                            log(TAG, VERBOSE) { "Updating control view" }
                            log(TAG, VERBOSE) { "Updating progress $progress" }
                            acv.setProgress(progressData)
                        }
                        else -> {
                            log(TAG) { "ControlView is $acv and progress is $progressData" }
                        }
                    }
                }
            }
            .launchIn(serviceScope)
    }

    override fun onInterrupt() {
        log(TAG) { "onInterrupt()" }
    }

    override fun onServiceConnected() {
        log(TAG) { "onServiceConnected()" }
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onUnbind(intent: Intent?): Boolean {
        log(TAG) { "onUnbind(intent=$intent)" }
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        log(TAG) { "onDestroy()" }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val copy = try {
            AccessibilityEvent.obtain(event)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to obtain accessibility event copy $event" }
            return
        }

        if (Bugs.isDebug) log(TAG, VERBOSE) { "New automation event: $copy" }

        try {
            event.source
                ?.getRoot(maxNesting = Int.MAX_VALUE)
                ?.let { fallbackRoot = it }
                .also { log(TAG, VERBOSE) { "Fallback root was $fallbackRoot, now is $it" } }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to get fallbackRoot from $event" }
        }

        serviceScope.launch { automationEvents.emit(copy) }
    }

    private var fallbackRoot: AccessibilityNodeInfo? = null

    override suspend fun windowRoot(): AccessibilityNodeInfo = suspendCancellableCoroutine {
        var maybeRootNode: AccessibilityNodeInfo? = rootInActiveWindow
        if (maybeRootNode == null) {
            log(TAG, WARN) { "Using fallback rootNode: $fallbackRoot" }
            maybeRootNode = fallbackRoot
        }
        log(TAG, VERBOSE) { "Providing window root: $maybeRootNode" }
        it.resume(maybeRootNode ?: throw CrawlerException("Root node is currently null"))
    }

    private val controlLp: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        gravity = Gravity.BOTTOM
    }

    override suspend fun changeOptions(action: (AutomationHost.Options) -> AutomationHost.Options) {
        val newOptions = action(currentOptions)
        currentOptions = newOptions

        mainThread.post {
            controlView?.let { acv ->
                controlLp.gravity = newOptions.panelGravity
                windowManager.updateViewLayout(acv, controlLp)

                if (newOptions.showOverlay) {
                    controlLp.height = WindowManager.LayoutParams.MATCH_PARENT
                } else {
                    controlLp.height = WindowManager.LayoutParams.WRAP_CONTENT
                }

                windowManager.updateViewLayout(acv, controlLp)
                acv.showOverlay(newOptions.showOverlay)
                acv.setTitle(newOptions.controlPanelTitle, newOptions.controlPanelSubtitle)
            }
        }
    }

    private var currentTaskJob: Job? = null
    private val taskLock = Mutex()

    suspend fun submit(task: AutomationTask): AutomationTask.Result = taskLock.withLock {
        log(TAG) { "submit(): $task" }
        updateProgress { Progress.DEFAULT_STATE }
        val deferred = serviceScope.async {
            try {
                automationProcessor.process(task)
            } finally {
                updateProgress { null }
            }
        }
        currentTaskJob = deferred
        log(TAG) { "submit(): ...waiting for result" }
        deferred.await()
    }

    override val service: AccessibilityService get() = this

    companion object {
        val TAG: String = logTag("Automation", "Service")
        var instance: AutomationService? = null
    }
}