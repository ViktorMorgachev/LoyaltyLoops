package io.loyaltyloop.server.service

import io.loyaltyloop.server.utils.json
import io.loyaltyloop.shared.models.CountryCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory

class GeoIpService(
    private val httpClient: OkHttpClient
) {
    private val logger = LoggerFactory.getLogger(GeoIpService::class.java)

    @Serializable
    data class GeoResponse(
        val countryCode: String? = null, // e.g. "KG", "RU"
        val status: String? = null
    )

    suspend fun getCountryByIp(ip: String): CountryCode? = withContext(Dispatchers.IO) {
        if (ip == "127.0.0.1" || ip == "0:0:0:0:0:0:0:1" || ip.startsWith("192.168.") || ip.startsWith("10.")) return@withContext null

        val url = "https://ip-api.com/json/$ip?fields=status,countryCode"

        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val geo = json.decodeFromString<GeoResponse>(body)
                logger.info("getCountryByIp: ip: $ip : countryCode:  ${geo.countryCode}")
                if (geo.status != "success") return@withContext null

                return@withContext try {
                    geo.countryCode?.let { CountryCode.valueOf(it.uppercase()) }
                } catch (e: Exception) {
                    logger.warn("getCountryByIp: ip: $ip ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("GeoIP lookup failed for $ip: ${e.message}")
            null
        }
    }
}

