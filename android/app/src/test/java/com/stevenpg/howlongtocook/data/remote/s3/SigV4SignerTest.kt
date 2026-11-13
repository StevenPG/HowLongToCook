package com.stevenpg.howlongtocook.data.remote.s3

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors from the AWS S3 "Signature Calculations for the Authorization Header" examples. */
class SigV4SignerTest {
    private val signer = SigV4Signer(
        accessKeyId = "AKIAIOSFODNN7EXAMPLE",
        secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        region = "us-east-1",
    )
    private val time = Instant.parse("2013-05-24T00:00:00Z")

    @Test
    fun `GET object example`() {
        val headers = signer.sign(
            SigV4Signer.Request(
                method = "GET",
                host = "examplebucket.s3.amazonaws.com",
                path = "/test.txt",
                headers = mapOf("Range" to "bytes=0-9"),
            ),
            time,
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            headers["Authorization"],
        )
        assertEquals("20130524T000000Z", headers["x-amz-date"])
    }

    @Test
    fun `PUT object example`() {
        val body = "Welcome to Amazon S3.".toByteArray()
        val headers = signer.sign(
            SigV4Signer.Request(
                method = "PUT",
                host = "examplebucket.s3.amazonaws.com",
                path = "/test\$file.text",
                headers = mapOf(
                    "Date" to "Fri, 24 May 2013 00:00:00 GMT",
                    "x-amz-storage-class" to "REDUCED_REDUNDANCY",
                ),
                payloadSha256 = SigV4Signer.sha256Hex(body),
            ),
            time,
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class, " +
                "Signature=98ad721746da40c64f1a55b78f14c238d841ea1380cd77a1b5971af0ece108bd",
            headers["Authorization"],
        )
    }

    @Test
    fun `GET bucket lifecycle example with empty query value`() {
        val headers = signer.sign(
            SigV4Signer.Request(
                method = "GET",
                host = "examplebucket.s3.amazonaws.com",
                path = "/",
                query = mapOf("lifecycle" to ""),
            ),
            time,
        )
        assertEquals(
            "fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543",
            headers["Authorization"]!!.substringAfter("Signature="),
        )
    }

    @Test
    fun `list objects example with sorted query`() {
        val headers = signer.sign(
            SigV4Signer.Request(
                method = "GET",
                host = "examplebucket.s3.amazonaws.com",
                path = "/",
                query = mapOf("prefix" to "J", "max-keys" to "2"),
            ),
            time,
        )
        assertEquals(
            "34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7",
            headers["Authorization"]!!.substringAfter("Signature="),
        )
    }

    @Test
    fun `uri encoding follows RFC 3986`() {
        assertEquals("a%20b%2Fc~d-e_f.g", SigV4Signer.uriEncode("a b/c~d-e_f.g"))
        assertEquals("/bucket/my%20folder/library.json", SigV4Signer.canonicalUri("/bucket/my folder/library.json"))
        assertEquals("%E2%82%AC", SigV4Signer.uriEncode("€"))
        assertEquals("a=1&b=&c=x%2By", SigV4Signer.canonicalQuery(mapOf("c" to "x+y", "a" to "1", "b" to "")))
    }
}
