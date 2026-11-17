package io.github.yuroyami.libmpv

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** mpv's https through Mbed TLS against a local server: what the default trust accepts and what it refuses. */
class TlsVerificationTest {

    @Test
    fun aNewCoreVerifiesWithTheJvmTrustedCertificates() {
        // mpv answers option reads only once the core runs.
        val mpv = createTestCore().apply {
            setOption("vo", "null").getOrThrow(); setOption("ao", "null").getOrThrow(); setOption("idle", "yes").getOrThrow()
            initialize().getOrThrow()
        }
        try {
            assertEquals("yes", mpv.getString("tls-verify"))
            val caFile = File(checkNotNull(mpv.getString("tls-ca-file")))
            val pem = caFile.readText()
            assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----"), "the CA file is PEM")
            assertTrue(Regex("BEGIN CERTIFICATE").findAll(pem).count() > 50, "the CA file holds the JVM's trusted CAs")
        } finally {
            mpv.close()
        }
    }

    @Test
    fun aServerSignedByTheGivenCaPlays() = assertPlayback(TestTls.LOCALHOST_P12, caFile = testCaFile(), expected = EndFileReason.Eof)

    @Test
    fun aServerSignedByAnUnknownCaIsRefused() = assertPlayback(TestTls.LOCALHOST_P12, caFile = null, expected = EndFileReason.Error)

    @Test
    fun aCertificateForAnotherHostIsRefused() = assertPlayback(TestTls.OTHER_HOST_P12, caFile = testCaFile(), expected = EndFileReason.Error)

    @Test
    fun turningVerificationOffAcceptsAnyServer() =
        assertPlayback(TestTls.OTHER_HOST_P12, caFile = null, verify = false, expected = EndFileReason.Eof)

    private fun testCaFile(): String = writeTestFile("test-ca.pem", TestTls.CA_PEM.encodeToByteArray())

    /** Serves one second of silence over https with [p12], plays it from `localhost`, and checks how the file ended. */
    private fun assertPlayback(p12: ByteArray, caFile: String?, verify: Boolean = true, expected: EndFileReason) = runBlocking {
        val server = httpsServer(p12, SilentWav.bytes(1))
        val mpv = createTestCore().apply {
            setOption("vo", "null").getOrThrow(); setOption("ao", "null").getOrThrow(); setOption("idle", "yes").getOrThrow()
            caFile?.let { setOption("tls-ca-file", it).getOrThrow() }
            if (!verify) setOption("tls-verify", "no").getOrThrow()
            initialize().getOrThrow()
        }
        try {
            // events has no replay, so the collector is attached before the command.
            val attached = CompletableDeferred<Unit>()
            val ended = async {
                mpv.events.onSubscription { attached.complete(Unit) }.filterIsInstance<MpvEvent.EndFile>().first()
            }
            attached.await()
            mpv.command(MpvCommand.of("loadfile", "https://localhost:${server.address.port}/silence.wav")).getOrThrow()
            assertEquals(expected, withTimeout(30_000) { ended.await() }.reason)
        } finally {
            mpv.close()
            server.stop(0)
        }
    }

    private fun httpsServer(p12: ByteArray, body: ByteArray): HttpsServer {
        val keys = KeyStore.getInstance("PKCS12").apply { load(p12.inputStream(), TestTls.PASSWORD.toCharArray()) }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keys, TestTls.PASSWORD.toCharArray()) }.keyManagers
        val tls = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
        return HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(tls)
            createContext("/silence.wav") { exchange ->
                exchange.responseHeaders.add("Content-Type", "audio/wav")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }
}
