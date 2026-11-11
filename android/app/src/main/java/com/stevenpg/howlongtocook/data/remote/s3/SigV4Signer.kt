package com.stevenpg.howlongtocook.data.remote.s3

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal AWS Signature Version 4 signer for S3-compatible APIs (AWS S3, Cloudflare R2,
 * MinIO, …). Implemented in-house to avoid pulling the multi-megabyte AWS SDK into the app.
 *
 * Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
 */
class SigV4Signer(
    private val accessKeyId: String,
    private val secretAccessKey: String,
    private val region: String,
    private val service: String = "s3",
) {
    data class Request(
        val method: String,
        /** Host header value, including a non-default port (e.g. "10.0.2.2:9000"). */
        val host: String,
        /** Raw (unencoded) absolute path, e.g. "/bucket/prefix/library.json". */
        val path: String,
        /** Raw (unencoded) query parameters. */
        val query: Map<String, String> = emptyMap(),
        /** Extra headers to sign (lowercased internally). */
        val headers: Map<String, String> = emptyMap(),
        /** Hex SHA-256 of the body ([EMPTY_SHA256] for no body). */
        val payloadSha256: String = EMPTY_SHA256,
    )

    /** Returns the headers to add to the request: Authorization, x-amz-date, x-amz-content-sha256. */
    fun sign(request: Request, now: Instant = Instant.now()): Map<String, String> {
        val amzDate = AMZ_DATE.format(now)
        val date = amzDate.substring(0, 8)

        val headers = sortedMapOf<String, String>()
        request.headers.forEach { (k, v) -> headers[k.lowercase()] = v.trim().replace(Regex("\\s+"), " ") }
        headers["host"] = request.host
        headers["x-amz-date"] = amzDate
        headers["x-amz-content-sha256"] = request.payloadSha256

        val signedHeaders = headers.keys.joinToString(";")
        val canonicalRequest = listOf(
            request.method.uppercase(),
            canonicalUri(request.path),
            canonicalQuery(request.query),
            headers.entries.joinToString("") { "${it.key}:${it.value}\n" },
            signedHeaders,
            request.payloadSha256,
        ).joinToString("\n")

        val scope = "$date/$region/$service/aws4_request"
        val stringToSign = listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest.toByteArray())).joinToString("\n")

        val kDate = hmac("AWS4$secretAccessKey".toByteArray(), date)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, service)
        val kSigning = hmac(kService, "aws4_request")
        val signature = hmac(kSigning, stringToSign).toHex()

        return mapOf(
            "Authorization" to "$ALGORITHM Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature",
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to request.payloadSha256,
        )
    }

    companion object {
        const val ALGORITHM = "AWS4-HMAC-SHA256"
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        private val AMZ_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        /** RFC 3986 encoding as required by SigV4 (unreserved characters are kept). */
        fun uriEncode(value: String, encodeSlash: Boolean = true): String = buildString {
            for (b in value.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt().toChar()
                when {
                    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
                    c == '/' && !encodeSlash -> append(c)
                    else -> append('%').append("%02X".format(b.toInt() and 0xFF))
                }
            }
        }

        fun canonicalUri(path: String): String = if (path.isEmpty()) "/" else uriEncode(path, encodeSlash = false)

        fun canonicalQuery(query: Map<String, String>): String =
            query.entries
                .map { uriEncode(it.key) to uriEncode(it.value) }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString("&") { "${it.first}=${it.second}" }

        private fun hmac(key: ByteArray, data: String): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(data.toByteArray(Charsets.UTF_8))
            }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
