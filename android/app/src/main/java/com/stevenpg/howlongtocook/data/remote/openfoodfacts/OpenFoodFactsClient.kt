package com.stevenpg.howlongtocook.data.remote.openfoodfacts

import com.stevenpg.howlongtocook.BuildConfig
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ProductSuggestion(val name: String?, val brand: String?)

@Serializable
private data class OffResponse(val status: Int = 0, val product: OffProduct? = null)

@Serializable
private data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
)

/**
 * Looks up a barcode on Open Food Facts (https://world.openfoodfacts.org), a free and open
 * database. Only the barcode is sent. Failures are swallowed: lookup is a convenience.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor(private val http: HttpClient) {
    suspend fun lookup(barcode: String): ProductSuggestion? = runCatching {
        val res = http.get("https://world.openfoodfacts.org/api/v2/product/${barcode.encodeURLPathPart()}") {
            parameter("fields", "product_name,brands")
            header(HttpHeaders.UserAgent, "HowLongToCook-Android/${BuildConfig.VERSION_NAME}")
        }
        if (res.status.value != 200) return@runCatching null
        val body = AppJson.decodeFromString(OffResponse.serializer(), res.bodyAsText())
        val p = body.product?.takeIf { body.status == 1 } ?: return@runCatching null
        val name = p.productName?.trim()?.takeIf { it.isNotEmpty() }
        val brand = p.brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        if (name == null && brand == null) null else ProductSuggestion(name, brand)
    }.getOrNull()
}
