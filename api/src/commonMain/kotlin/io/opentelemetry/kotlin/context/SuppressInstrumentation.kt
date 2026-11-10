/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.kotlin.context

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.ThreadLocal

@OptIn(ExperimentalApi::class)
private val suppressInstrumentationKey: ContextKey<Boolean> =
    createContextKey("suppress instrumentation")

private val suppressInstrumentationFlag = ThreadLocal<Boolean>()

/**
 * Returns true when nested instrumentation should skip creating a new span.
 *
 * Temporary stand-in so exporters and instrumentation in this project can share a suppress
 * signal before a public API exists.
 */
@ExperimentalApi
@TempContextStandInApi
public fun internalIsInstrumentationSuppressed(context: Context): Boolean =
    suppressInstrumentationFlag.get() == true || context.get(suppressInstrumentationKey) == true

/**
 * Suppresses nested instrumentation until the returned [Scope] is detached.
 *
 * Temporary stand-in so exporters can mark their own transport calls.
 */
@ExperimentalApi
@TempContextStandInApi
public fun internalSuppressInstrumentation(): Scope {
    val previous = suppressInstrumentationFlag.get()
    suppressInstrumentationFlag.set(true)
    val contextScope = currentContext().set(suppressInstrumentationKey, true).attach()
    return object : Scope {
        override fun detach(): Boolean {
            val detached = contextScope.detach()
            suppressInstrumentationFlag.set(previous)
            return detached
        }
    }
}
