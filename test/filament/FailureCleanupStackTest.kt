package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FailureCleanupStackTest {
  @Test
  fun cleanUpFailureRunsActionsInReverseRegistrationOrder() {
    val cleanedResources: MutableList<String> = mutableListOf()
    val stack = FailureCleanupStack()

    stack.register {
      cleanedResources.add("engine")
    }
    stack.register {
      cleanedResources.add("swap-chain")
    }
    stack.register {
      cleanedResources.add("renderer")
    }

    stack.cleanUpFailure(RuntimeException("init failed"))

    assertEquals(listOf("renderer", "swap-chain", "engine"), cleanedResources)
  }

  @Test
  fun cleanUpFailureAddsCleanupErrorsAsSuppressedFailures() {
    val initFailure = RuntimeException("init failed")
    val cleanupFailure = RuntimeException("cleanup failed")
    val stack = FailureCleanupStack()

    stack.register {
      throw cleanupFailure
    }

    stack.cleanUpFailure(initFailure)

    assertSame(cleanupFailure, initFailure.suppressed.single())
  }

  @Test
  fun dismissSkipsRegisteredCleanupActions() {
    var cleanupCount = 0
    val stack = FailureCleanupStack()

    stack.register {
      cleanupCount += 1
    }
    stack.dismiss()
    stack.cleanUpFailure(RuntimeException("init failed"))

    assertEquals(0, cleanupCount)
  }
}
