package com.phuchienngo.marblemarvelous.filament

import com.google.android.filament.Filament
import com.google.android.filament.filamat.MaterialBuilder
import java.util.concurrent.atomic.AtomicBoolean

internal object FilamentRuntime {
  private val initialized: AtomicBoolean = AtomicBoolean(false)

  fun initialize() {
    if (!initialized.compareAndSet(false, true)) {
      return
    }
    Filament.init()
    MaterialBuilder.init()
  }
}
