package io.opentelemetry.kotlin.internal

/**
 * Name of the Ktor request attribute that marks SDK exporter HTTP so client instrumentation
 * can skip creating a span.
 *
 * Defined once so exporters and the Ktor client plugin can construct
 * `AttributeKey<Boolean>(NAME)` independently. Ktor keys compare equal by name and type.
 */
public object SuppressInstrumentationAttribute {
    public const val NAME: String = "suppress instrumentation"
}
