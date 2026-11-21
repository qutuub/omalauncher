package dev.omalauncher.personal.android

import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.omalauncher.Runtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ComponentActivity.homeResumed(runtime: Runtime) {
    runtime.wm.focus(null)
    // The Home role controls whether Android exposes pinned shortcuts.
    runtime.androidApps.checkShortcutAccess()

    // Query Recents off the main thread. Linux windows can start through aliases.
    lifecycleScope.launch {
        val windows =
            withContext(Dispatchers.IO) {
                getSystemService(ActivityManager::class.java).appTasks.mapNotNull { task ->
                    val data = runCatching { task.taskInfo?.baseIntent?.data }.getOrNull() ?: return@mapNotNull null
                    // By its data rather than its class: windows start through aliases too.
                    if (data.scheme == "omarchy" && data.host == "window") task to data.lastPathSegment else null
                }
            }
        val stale = windows.filter { (_, handle) -> runtime.wm.windowByHandle(handle) == null }.map { it.first }
        // A window that just closed removes its own task meanwhile; finishing it again throws.
        if (stale.isNotEmpty()) withContext(Dispatchers.IO) { stale.forEach { runCatching { it.finishAndRemoveTask() } } }
    }
}
