package com.hardrivetech.t1dtracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test stub for biometric/auth flows.
 * This file is a scaffold and is not executed by default in CI.
 */
@RunWith(AndroidJUnit4::class)
class BiometricAuthInstrumentedTest {

    @Test
    fun appContext_packageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.hardrivetech.t1dtracker", appContext.packageName)
    }

    // Add real biometric flow tests here when enabling connected tests.
}
