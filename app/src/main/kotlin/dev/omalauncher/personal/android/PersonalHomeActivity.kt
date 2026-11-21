package dev.omalauncher.personal.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.omalauncher.App
import dev.omalauncher.MainActivity
import dev.omalauncher.R
import dev.omalauncher.linux.LinuxState
import dev.omalauncher.personal.home.HomeScreen
import dev.omalauncher.personal.home.HomeStatus
import dev.omalauncher.personal.home.HomeViewModel
import dev.omalauncher.personal.home.SearchScreen
import dev.omalauncher.personal.home.StatusLabels
import dev.omalauncher.personal.home.VaultSnapshot
import dev.omalauncher.personal.home.VaultTask
import dev.omalauncher.personal.home.completeTaskLine
import dev.omalauncher.personal.home.parseTodoMarkdown
import dev.omalauncher.system.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class PersonalHomeActivity : ComponentActivity() {
    private val runtime get() = (application as? App)?.runtime
    private var status by mutableStateOf<HomeStatus?>(null)
    private var vault by mutableStateOf(VaultSnapshot())
    private var monitor: StatusMonitor? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppLanguage.applyTo(this)
    }

    private var searching by mutableStateOf(false)

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        // Android 10 needs resize to dispatch IME insets to an edge-to-edge window.
        if (Build.VERSION.SDK_INT < 30) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        hideSystemBars()
        val search = SearchBridge(this, runtime?.androidApps)
        var failedApp by mutableStateOf<String?>(null)
        setContent {
            val imeVisible = WindowInsets.isImeVisible
            LaunchedEffect(imeVisible) { if (!imeVisible) hideSystemBars() }
            if (searching) {
                LaunchedEffect(search) { search.refreshSettings() }
                SearchScreen(search::results, search::profileLabel, { result ->
                    if (!search.open(result)) failedApp = result.name
                }, ::closeSearch)
            } else {
                val launchFailure = failedApp?.let { stringResource(R.string.personal_search_open_failed, it) }
                HomeScreen(
                    status = status,
                    vault = vault,
                    today = LocalDate.now(),
                    launchFailure = launchFailure,
                    onCompleteTask = ::completeVaultTask,
                    openUpstream = ::openUpstream,
                ) {
                    failedApp = null
                    searching = true
                }
            }
        }
        // Preserve upstream's startup when Android first enters this Home instead of MainActivity.
        runtime?.start()
    }

    override fun onStart() {
        super.onStart()
        runtime?.screenStarted()
        val model = HomeViewModel(
            StatusLabels(
                wifi = getString(R.string.personal_wifi),
                sim = getString(R.string.personal_sim),
                airplane = getString(R.string.personal_airplane),
                alarm = getString(R.string.personal_alarm),
            ),
        )
        try {
            monitor = StatusMonitor(this, ::readArchState) { status = model.format(it) }
            monitor?.start()
        } catch (failure: RuntimeException) {
            Log.w("PersonalHome", "Could not start status monitor", failure)
        }
        refreshVault()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        runtime?.let { homeResumed(it) }
        refreshVault()
    }

    override fun onStop() {
        monitor?.stop()
        monitor = null
        super.onStop()
        runtime?.let {
            it.usage.settle()
            it.screenStopped()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun readArchState(): String {
        val rt = runtime ?: return "off"
        return when {
            rt.linuxRunning -> "ready"
            rt.linuxStopped -> "stopped"
            rt.bootstrap.state.value == LinuxState.Installed -> "starting"
            rt.bootstrap.state.value == LinuxState.NotInstalled -> "none"
            else -> "busy"
        }
    }

    private fun todoFile(): File = File(filesDir, "arch/root/vault/todo.md")

    private fun refreshVault() {
        lifecycleScope.launch {
            vault = withContext(Dispatchers.IO) {
                val file = todoFile()
                if (!file.isFile) return@withContext VaultSnapshot(exists = false)
                val text = runCatching { file.readText() }.getOrNull() ?: return@withContext VaultSnapshot(exists = true)
                VaultSnapshot(exists = true, openTasks = parseTodoMarkdown(text))
            }
        }
    }

    private fun completeVaultTask(task: VaultTask) {
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                val file = todoFile()
                if (!file.isFile) return@withContext null
                val current = runCatching { file.readText() }.getOrNull() ?: return@withContext null
                val next = completeTaskLine(current, task.lineIndex, task.rawLine) ?: return@withContext null
                runCatching {
                    val temp = File(file.parentFile, ".todo.tmp")
                    temp.writeText(next)
                    if (!temp.renameTo(file)) file.writeText(next)
                    VaultSnapshot(exists = true, openTasks = parseTodoMarkdown(next))
                }.getOrNull()
            }
            if (updated != null) vault = updated else refreshVault()
        }
    }

    private fun closeSearch() {
        searching = false
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openUpstream() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }
}
