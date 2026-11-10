/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.kotlin.context

import io.opentelemetry.kotlin.ExperimentalApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalApi::class, TempContextStandInApi::class)
internal class SuppressInstrumentationTest {

    private val storage = SlotStorage()

    @AfterTest
    fun tearDown() {
        uninstallContextStorage(storage)
    }

    @Test
    fun `not suppressed by default`() {
        assertFalse(internalIsInstrumentationSuppressed(rootContext()))
    }

    @Test
    fun `suppresses until the scope is detached`() {
        assertTrue(installContextStorage(storage))
        val scope = internalSuppressInstrumentation()
        try {
            assertTrue(internalIsInstrumentationSuppressed(currentContext()))
        } finally {
            assertTrue(scope.detach())
        }
        assertFalse(internalIsInstrumentationSuppressed(currentContext()))
    }

    @Test
    fun `nested suppress restores the previous flag`() {
        val outer = internalSuppressInstrumentation()
        val inner = internalSuppressInstrumentation()
        assertTrue(internalIsInstrumentationSuppressed(rootContext()))
        assertTrue(inner.detach())
        assertTrue(internalIsInstrumentationSuppressed(rootContext()))
        assertTrue(outer.detach())
        assertFalse(internalIsInstrumentationSuppressed(rootContext()))
    }

    private class SlotStorage : CurrentContextStorage {
        private var slot: Context? = null

        override fun current(): Context = slot ?: rootContext()

        override fun attach(context: Context): Scope {
            val previous = slot
            slot = context
            return object : Scope {
                override fun detach(): Boolean {
                    slot = previous
                    return true
                }
            }
        }
    }
}
