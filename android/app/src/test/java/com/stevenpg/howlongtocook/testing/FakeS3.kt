package com.stevenpg.howlongtocook.testing

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

/**
 * In-memory S3 bucket implementing just enough of the API for sync tests, including
 * ETag conditional writes. [beforeNextPut] simulates another device writing first.
 */
class FakeS3(private val supportsConditionalWrites: Boolean = true) {
    data class Obj(val bytes: ByteArray, val etag: String)

    val objects = mutableMapOf<String, Obj>()
    var putCount = 0
        private set
    var conflicts = 0
        private set
    var beforeNextPut: (() -> Unit)? = null
    var denyAll = false
    private var etagCounter = 0

    fun write(path: String, text: String) {
        objects[path] = Obj(text.encodeToByteArray(), "\"e${++etagCounter}\"")
    }

    fun read(path: String): String? = objects[path]?.bytes?.decodeToString()

    val engine = MockEngine { req ->
        check(req.headers["Authorization"]?.startsWith("AWS4-HMAC-SHA256 Credential=") == true) { "unsigned request" }
        check(req.headers["x-amz-content-sha256"] != null)
        if (denyAll) {
            return@MockEngine respond("<Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>", HttpStatusCode.Forbidden)
        }
        val path = req.url.encodedPath
        when (req.method) {
            HttpMethod.Get -> when {
                req.url.parameters["list-type"] == "2" -> respond("<ListBucketResult></ListBucketResult>", HttpStatusCode.OK)
                else -> objects[path]?.let { respond(it.bytes, HttpStatusCode.OK, headersOf("ETag", it.etag)) }
                    ?: respond("<Error><Code>NoSuchKey</Code></Error>", HttpStatusCode.NotFound)
            }
            HttpMethod.Put -> {
                beforeNextPut?.also { beforeNextPut = null }?.invoke()
                val ifMatch = req.headers["If-Match"]
                val ifNoneMatch = req.headers["If-None-Match"]
                if (!supportsConditionalWrites && (ifMatch != null || ifNoneMatch != null)) {
                    return@MockEngine respond("<Error><Code>NotImplemented</Code></Error>", HttpStatusCode.NotImplemented)
                }
                val current = objects[path]
                if ((ifNoneMatch == "*" && current != null) || (ifMatch != null && current?.etag != ifMatch)) {
                    conflicts++
                    return@MockEngine respond("<Error><Code>PreconditionFailed</Code></Error>", HttpStatusCode.PreconditionFailed)
                }
                val body = (req.body as OutgoingContent.ByteArrayContent).bytes()
                val etag = "\"e${++etagCounter}\""
                objects[path] = Obj(body, etag)
                putCount++
                respond("", HttpStatusCode.OK, headersOf("ETag", etag))
            }
            else -> respond("", HttpStatusCode.MethodNotAllowed)
        }
    }
}
