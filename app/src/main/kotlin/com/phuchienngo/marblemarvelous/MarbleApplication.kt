package com.phuchienngo.marblemarvelous

import android.app.Application
import com.phuchienngo.marblemarvelous.di.DaggerMarbleComponent
import com.phuchienngo.marblemarvelous.di.MarbleComponent

class MarbleApplication : Application() {
  internal val component: MarbleComponent by lazy(LazyThreadSafetyMode.NONE) {
    DaggerMarbleComponent
      .factory()
      .create(applicationContext)
  }
}
