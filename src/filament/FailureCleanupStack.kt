package com.phuchienngo.marblemarvelous.filament

internal class FailureCleanupStack {
  private val actions: ArrayDeque<() -> Unit> = ArrayDeque()

  fun register(action: () -> Unit) {
    actions.addFirst(action)
  }

  fun dismiss() {
    actions.clear()
  }

  fun cleanUpFailure(failure: Throwable) {
    while (actions.isNotEmpty()) {
      val action: () -> Unit = actions.removeFirst()
      try {
        action()
      } catch (cleanupFailure: Throwable) {
        failure.addSuppressed(cleanupFailure)
      }
    }
  }
}
