package com.phuchienngo.marblemarvelous.filament

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class FilamentRendererCleanup(
  dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

  fun submit(cleanup: () -> Unit) {
    scope.launch cleanupRenderer@{
      try {
        cleanup()
      } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to destroy Filament renderer", throwable)
      }
      return@cleanupRenderer
    }
  }

  private companion object {
    const val TAG: String = "FilamentCleanup"
  }
}
