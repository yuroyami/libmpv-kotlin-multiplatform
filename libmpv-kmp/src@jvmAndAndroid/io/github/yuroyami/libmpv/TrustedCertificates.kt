package io.github.yuroyami.libmpv

import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64

/**
 * The certificate authorities that the JVM or the Android app trusts, as one PEM file for Mbed TLS.
 * Mbed TLS cannot read a Java key store or Android's trust store, so mpv gets this file as `tls-ca-file`.
 */
internal object TrustedCertificates {
    /** The default trust manager's CAs: the JVM's cacerts, or on Android the app's network security config. */
    fun pem(): String {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val certificates = factory.trustManagers.filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.asList() }
            .distinctBy { it.encoded.contentHashCode() }
        check(certificates.isNotEmpty()) { "the default trust manager names no certificate authority" }
        return certificates.joinToString("") { it.toPem() }
    }

    /**
     * Writes [pem] into [directory] under a name made from its SHA-256, unless that file exists already.
     * Only for a directory no other user can write to: the name is predictable.
     */
    fun writeNamedByContent(directory: File, pem: String): File {
        val bytes = pem.encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val file = File(directory, "ca-$digest.pem")
        if (file.isFile && file.length() == bytes.size.toLong()) return file
        directory.mkdirs()
        // Another process of the same app can write the same file at the same time, so a rename finishes it.
        val partial = File.createTempFile("ca-", ".partial", directory)
        partial.writeBytes(bytes)
        if (!partial.renameTo(file)) {
            partial.delete()
            check(file.isFile) { "cannot write $file" }
        }
        return file
    }

    /** Writes [pem] to a new temporary file with a random name, which only this user can read. Deleted at exit. */
    fun writeTemporary(pem: String): File =
        File.createTempFile("libmpv-kmp-ca-", ".pem").apply {
            deleteOnExit()
            writeBytes(pem.encodeToByteArray())
        }

    private fun X509Certificate.toPem(): String =
        "-----BEGIN CERTIFICATE-----\n" + Base64.encode(encoded).chunked(64).joinToString("\n") + "\n-----END CERTIFICATE-----\n"
}

/** Sets `tls-verify=yes` and the trusted CAs on every new core. [caFile] runs once per process. */
internal class DefaultTrust(private val caFile: () -> File) {
    private val file: File? by lazy {
        runCatching(caFile).onFailure { logError("no CA file for https, so every https stream will fail its certificate check", it) }.getOrNull()
    }

    fun applyTo(mpv: Mpv) {
        mpv.setOption("tls-verify", "yes")
        file?.let { mpv.setOption("tls-ca-file", it.absolutePath) }
    }
}
