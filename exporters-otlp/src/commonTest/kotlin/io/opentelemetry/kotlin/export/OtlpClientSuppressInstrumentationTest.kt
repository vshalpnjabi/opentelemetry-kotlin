package io.opentelemetry.kotlin.export

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.error.NoopSdkErrorHandler
import io.opentelemetry.kotlin.framework.SdkTracker
import io.opentelemetry.kotlin.instrumentation.ktor.client.openTelemetryKtorClientPlugin
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.data.FakeSpanData
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.FakeSpanProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class OtlpClientSuppressInstrumentationTest {

    private val sdks = SdkTracker()
    private lateinit var processor: FakeSpanProcessor

    private val spans: List<SpanData>
        get() = processor.endCalls.map { it.toSpanData() }

    @BeforeTest
    fun setUp() {
        processor = FakeSpanProcessor()
    }

    @AfterTest
    fun tearDown() = sdks.shutdownAll()

    @Test
    fun testExportDoesNotSuppressConcurrentApplicationRequest() = runTest {
        val exportStarted = CompletableDeferred<Unit>()
        val releaseExport = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("v1/traces")) {
                exportStarted.complete(Unit)
                releaseExport.await()
            }
            respond("", HttpStatusCode.OK)
        }
        val openTelemetry = createOpenTelemetry {
            tracerProvider { export { processor } }
        }.also(sdks::track)
        val httpClient = HttpClient(engine) {
            install(openTelemetryKtorClientPlugin(openTelemetry))
        }
        val otlpClient = OtlpClient(
            baseUrl = "http://localhost:4318",
            httpClient = httpClient,
            sdkErrorHandler = NoopSdkErrorHandler,
        )
        try {
            val exportJob = launch {
                otlpClient.exportTraces(listOf(FakeSpanData()))
            }
            exportStarted.await()

            httpClient.get("https://example.com/users")
            val applicationSpan = spans.single()
            assertEquals("GET", applicationSpan.name)
            assertEquals(SpanKind.CLIENT, applicationSpan.spanKind)

            releaseExport.complete(Unit)
            exportJob.join()
            assertEquals(listOf("GET"), spans.map { it.name })
        } finally {
            httpClient.close()
            engine.close()
        }
    }
}
