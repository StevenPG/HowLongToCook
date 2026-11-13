package com.stevenpg.howlongtocook.data.remote.s3

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import java.net.URI

class S3Exception(val status: Int, val code: String?, message: String) : Exception(message)

/** Thrown when a conditional write loses a race (HTTP 412). */
class S3PreconditionFailed : Exception("Object changed concurrently")

/** Thrown when the provider rejects conditional-write headers (HTTP 501). */
class S3ConditionalWritesUnsupported : Exception("Conditional writes are not supported by this provider")

data class S3Object(val bytes: ByteArray, val etag: String?)

/**
 * Tiny S3 client covering exactly what sync needs: GET/PUT one object and a
 * connectivity check. Supports path-style and virtual-hosted-style addressing.
 */
class S3Client(
    private val http: HttpClient,
    endpoint: String,
    private val bucket: String,
    region: String,
    accessKeyId: String,
    secretAccessKey: String,
    private val pathStyle: Boolean,
) {
    private val signer = SigV4Signer(accessKeyId, secretAccessKey, region)
    private val endpointUri: URI = URI(endpoint.trimEnd('/')).also {
        require(it.scheme == "https" || it.scheme == "http") { "Endpoint must start with https:// or http://" }
        require(!it.host.isNullOrBlank()) { "Endpoint has no host" }
    }

    private data class Target(val url: String, val host: String, val path: String)

    private fun target(key: String?): Target {
        val port = endpointUri.port
        val defaultPort = (endpointUri.scheme == "https" && port == 443) || (endpointUri.scheme == "http" && port == 80)
        val portSuffix = if (port == -1 || defaultPort) "" else ":$port"
        val basePath = endpointUri.rawPath.orEmpty().trimEnd('/')
        val host: String
        val rawPath: String
        if (pathStyle) {
            host = endpointUri.host + portSuffix
            rawPath = "$basePath/$bucket" + (key?.let { "/$it" } ?: "/")
        } else {
            host = "$bucket.${endpointUri.host}$portSuffix"
            rawPath = "$basePath/" + (key ?: "")
        }
        val encodedPath = SigV4Signer.canonicalUri(rawPath)
        return Target("${endpointUri.scheme}://$host$encodedPath", host, rawPath)
    }

    suspend fun getObject(key: String): S3Object? {
        val t = target(key)
        val res = execute(HttpMethod.Get, t)
        return when (res.status.value) {
            200 -> S3Object(res.bodyAsBytes(), res.headers["ETag"])
            404 -> null
            else -> throw error(res)
        }
    }

    /**
     * Writes an object. With [ifMatch] the write only succeeds if the current ETag matches;
     * with [ifNoneMatch] it only succeeds if the object does not exist yet.
     */
    suspend fun putObject(
        key: String,
        body: ByteArray,
        contentType: String = "application/json",
        ifMatch: String? = null,
        ifNoneMatch: Boolean = false,
    ): String? {
        val t = target(key)
        // Content-Type is deliberately not signed: HTTP engines may normalize it.
        val headers = buildMap {
            ifMatch?.let { put("if-match", it) }
            if (ifNoneMatch) put("if-none-match", "*")
        }
        val res = execute(HttpMethod.Put, t, headers = headers, body = body, contentType = contentType)
        return when (res.status.value) {
            200, 201, 204 -> res.headers["ETag"]
            412 -> throw S3PreconditionFailed()
            501 -> throw S3ConditionalWritesUnsupported()
            else -> throw error(res)
        }
    }

    /** Verifies endpoint, credentials and bucket access with a 1-key listing. */
    suspend fun checkAccess(prefix: String) {
        val t = target(null)
        val query = mapOf("list-type" to "2", "max-keys" to "1", "prefix" to prefix)
        val res = execute(HttpMethod.Get, t, query = query)
        if (res.status.value != 200) throw error(res)
    }

    private suspend fun execute(
        method: HttpMethod,
        t: Target,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
    ): HttpResponse {
        val payloadHash = body?.let(SigV4Signer::sha256Hex) ?: SigV4Signer.EMPTY_SHA256
        val signed = signer.sign(
            SigV4Signer.Request(
                method = method.value,
                host = t.host,
                path = t.path,
                query = query,
                headers = headers,
                payloadSha256 = payloadHash,
            ),
        )
        val url = if (query.isEmpty()) t.url else t.url + "?" + SigV4Signer.canonicalQuery(query)
        return http.request(url) {
            this.method = method
            (headers + signed).forEach { (k, v) -> header(k, v) }
            if (body != null) {
                setBody(ByteArrayContent(body, ContentType.parse(contentType ?: "application/octet-stream")))
            }
        }
    }

    private suspend fun error(res: HttpResponse): S3Exception {
        val text = runCatching { res.bodyAsBytes().decodeToString() }.getOrDefault("")
        val code = Regex("<Code>(.*?)</Code>").find(text)?.groupValues?.get(1)
        val msg = Regex("<Message>(.*?)</Message>").find(text)?.groupValues?.get(1)
        val friendly = when (res.status.value) {
            401, 403 -> "Access denied — check the access key, secret and bucket permissions"
            404 -> if (code == "NoSuchBucket") "Bucket not found" else "Not found"
            else -> "S3 error ${res.status.value}"
        }
        return S3Exception(res.status.value, code, listOfNotNull(friendly, code, msg).distinct().joinToString(": "))
    }
}
