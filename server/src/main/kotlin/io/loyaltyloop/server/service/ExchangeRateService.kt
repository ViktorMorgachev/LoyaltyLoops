package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.ExchangeRatesTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.PartnerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.math.BigDecimal


// TODO checked
class ExchangeRateService(
    private val redisService: RedisService,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient,
) {
    private val logger = LoggerFactory.getLogger(ExchangeRateService::class.java)

    private companion object {
        const val UPDATE_INTERVAL_MS = 4L * 60 * 60 * 1000
        const val CURRENCY_CODE_LENGTH = 3
    }

    // 25 часов (чтобы пережить сбой крона, но удалять мусор)
    private val cacheTtlSeconds = 25 * 60 * 60

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch {
            logger.info("🚀 ExchangeRateService started")
            while (isActive) { // Используем isActive для корректной остановки
                try {
                    updateRates()
                } catch (e: Exception) {
                    logger.error("❌ Failed to update exchange rates", e)
                }
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    suspend fun getRate(fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return 1.0

        val key = "rate:$fromCurrency:$toCurrency"

        try {
            val cached = redisService.get(key)
            if (cached != null) {
                return cached.toDouble()
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Redis unavailable, falling back to DB", e)
        }

        val dbRate = dbQuery {
            ExchangeRatesTable
                .slice(ExchangeRatesTable.rate)
                .select {
                    (ExchangeRatesTable.fromCurrency eq fromCurrency) and
                            (ExchangeRatesTable.toCurrency eq toCurrency)
                }
                .singleOrNull()
                ?.get(ExchangeRatesTable.rate)
        }

        if (dbRate != null) {
            val rateValue = dbRate.toDouble()

            try {
                redisService.set(key, rateValue.toString(), cacheTtlSeconds)
            } catch (e: Exception) {
                logger.warn("Redis restore failed", e)
            }
            return rateValue
        }

        logger.info("⚠️ Rate missing for $fromCurrency -> $toCurrency. Attempting lazy fetch...")

        return try {
            val rates = fetchRatesFromApi(fromCurrency)
            val targetRate = rates[toCurrency]
                ?: throw LoyaltyException(
                    AppErrorCode.CURRENCY_RATE_NOT_FOUND,
                    "Target currency not in API"
                )

            saveRatesToDbAndCache(fromCurrency, rates)

            targetRate
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("❌ Lazy fetch failed for $fromCurrency -> $toCurrency", e)
            throw LoyaltyException(AppErrorCode.CURRENCY_RATE_NOT_FOUND, "Rate fetch failed", e)
        }
    }

    private suspend fun updateRates() {
        logger.info("🔄 Updating exchange rates...")

        val baseCurrencies = dbQuery {
            PartnersTable.slice(PartnersTable.baseCurrency)
                .select((PartnersTable.status eq PartnerStatus.ACTIVE) or (PartnersTable.status eq PartnerStatus.PENDING))
                .withDistinct()
                .map { it[PartnersTable.baseCurrency] }
                .filter { it.length == CURRENCY_CODE_LENGTH }
        }

        val terminalCurrencies = dbQuery {
            TradingPointsTable.slice(TradingPointsTable.currency)
                .selectAll()
                .withDistinct()
                .map { it[TradingPointsTable.currency] }
                .filter { it.length == CURRENCY_CODE_LENGTH }
        }.toSet()

        if (baseCurrencies.isEmpty()) return

        // 2. Process each base currency
        baseCurrencies.forEach { base ->
            try {
                val allRates = fetchRatesFromApi(base)
                // Оставляем только те валюты, которые используются в точках
                val relevantRates = allRates.filterKeys { it in terminalCurrencies }

                if (relevantRates.isNotEmpty()) {
                    saveRatesToDbAndCache(base, relevantRates)
                }
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                logger.error("❌ Failed to update rates for base $base", e)
            }
        }
        logger.info("✅ Exchange rates update cycle finished.")
    }

    // Вынес логику сохранения в отдельный метод, чтобы использовать и в Job, и в LazyFetch
    private suspend fun saveRatesToDbAndCache(base: String, rates: Map<String, Double>) {
        // 1. Save to Redis
        try {
            rates.forEach { (target, rate) ->
                val key = "rate:$base:$target"
                redisService.set(key, rate.toString(), cacheTtlSeconds)
            }
        } catch (e: Exception) {
            logger.warn("Redis set failed", e)
        }

        // 2. Save to DB
        dbQuery {
            val now = nowUtc()

            // Удаляем старые курсы только для этой базовой валюты
            ExchangeRatesTable.deleteWhere {
                ExchangeRatesTable.fromCurrency eq base
            }

            ExchangeRatesTable.batchInsert(rates.toList()) { (target, rate) ->
                this[ExchangeRatesTable.fromCurrency] = base
                this[ExchangeRatesTable.toCurrency] = target

                this[ExchangeRatesTable.rate] = BigDecimal.valueOf(rate)

                this[ExchangeRatesTable.updatedAt] = now
            }
        }
    }

    private suspend fun fetchRatesFromApi(base: String): Map<String, Double> = withContext(Dispatchers.IO) {
        val url = "https://v6.exchangerate-api.com/v6/$apiKey/latest/$base"
        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("API Error: ${response.code}")

            val bodyString = response.body?.string() ?: "{}"
            val json = JSONObject(bodyString)

            if (json.optString("result") != "success") {
                error("API returned error: ${json.optString("error-type")}")
            }

            val conversionRates = json.getJSONObject("conversion_rates")
            val map = mutableMapOf<String, Double>()

            conversionRates.keys().forEach { key ->
                map[key] = conversionRates.getDouble(key)
            }
            return@withContext map
        }
    }
}
