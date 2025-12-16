package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.ExchangeRatesTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.server.utils.LoyaltyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ExchangeRateService(
    private val redisService: RedisService,
    private val apiKey: String // Лучше передавать из конфига
) {
    private val logger = LoggerFactory.getLogger(ExchangeRateService::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 25 часов (чтобы пережить сбой крона, но удалять мусор)
    private val CACHE_TTL = 25 * 60 * 60

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            logger.info("🚀 ExchangeRateService started")
            while (isActive) { // Используем isActive для корректной остановки
                try {
                    updateRates()
                } catch (e: Exception) {
                    logger.error("❌ Failed to update exchange rates", e)
                }
                delay(4 * 60 * 60 * 1000L) // 4 hours
            }
        }
    }

    suspend fun getRate(fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return 1.0

        val key = "rate:$fromCurrency:$toCurrency"

        // 1. Try Redis (Hot Cache)
        try {
            val cached = redisService.get(key)
            if (cached != null) {
                return cached.toDouble()
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Redis unavailable, falling back to DB", e)
        }

        // 2. Try DB (Persistent Storage)
        val dbRate = dbQuery {
            ExchangeRatesTable.selectAll()
                .where { (ExchangeRatesTable.fromCurrency eq fromCurrency) and (ExchangeRatesTable.toCurrency eq toCurrency) }
                .singleOrNull()?.get(ExchangeRatesTable.rate)
        }

        if (dbRate != null) {
            // [FIX] Restore Redis Cache! (Self-healing)
            try {
                redisService.set(key, dbRate.toString(), CACHE_TTL)
            } catch (e: Exception) { logger.warn("Redis restore failed", e) }
            return dbRate
        }

        // 3. Lazy Fetch (API Fallback)
        logger.info("⚠️ Rate missing for $fromCurrency -> $toCurrency. Attempting lazy fetch...")

        return try {
            val rates = fetchRatesFromApi(fromCurrency)
            val targetRate = rates[toCurrency]
                ?: throw LoyaltyException(AppErrorCode.CURRENCY_RATE_NOT_FOUND, "Target currency not in API")

            // Async/Sync save (делаем синхронно для надежности)
            saveRatesToDbAndCache(fromCurrency, rates)

            targetRate
        } catch (e: Exception) {
            logger.error("❌ Lazy fetch failed for $fromCurrency -> $toCurrency", e)
            throw LoyaltyException(AppErrorCode.CURRENCY_RATE_NOT_FOUND, "Rate fetch failed")
        }
    }

    private suspend fun updateRates() {
        logger.info("🔄 Updating exchange rates...")

        // 1. Get Bases & Targets (Без изменений)
        val baseCurrencies = dbQuery {
            PartnersTable.slice(PartnersTable.baseCurrency)
                .select((PartnersTable.status eq PartnerStatus.ACTIVE) or (PartnersTable.status eq PartnerStatus.PENDING))
                .withDistinct()
                .map { it[PartnersTable.baseCurrency] }
                .filter { it.length == 3 }
        }

        val terminalCurrencies = dbQuery {
            TradingPointsTable.slice(TradingPointsTable.currency)
                .selectAll()
                .withDistinct()
                .map { it[TradingPointsTable.currency] }
                .filter { it.length == 3 }
        }.toSet() // В Set для быстрого поиска

        if (baseCurrencies.isEmpty()) return

        // 2. Process each base currency
        baseCurrencies.forEach { base ->
            try {
                val allRates = fetchRatesFromApi(base)
                // Фильтруем: оставляем только те валюты, которые используются в наших точках
                val relevantRates = allRates.filterKeys { it in terminalCurrencies }

                if (relevantRates.isNotEmpty()) {
                    saveRatesToDbAndCache(base, relevantRates)
                }
            } catch (e: Exception) {
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
                redisService.set(key, rate.toString(), CACHE_TTL) // [FIX] Передаем TTL
            }
        } catch (e: Exception) { logger.warn("Redis set failed", e) }

        // 2. Save to DB
        dbQuery {
            // Удаляем ТОЛЬКО записи для текущей базовой валюты
            ExchangeRatesTable.deleteWhere {
                ExchangeRatesTable.fromCurrency eq base
            }

            ExchangeRatesTable.batchInsert(rates.toList()) { (target, rate) ->
                this[ExchangeRatesTable.fromCurrency] = base
                this[ExchangeRatesTable.toCurrency] = target
                this[ExchangeRatesTable.rate] = rate
                this[ExchangeRatesTable.updatedAt] = System.currentTimeMillis()
            }
        }
    }

    private fun fetchRatesFromApi(base: String): Map<String, Double> {
        val url = "https://v6.exchangerate-api.com/v6/$apiKey/latest/$base"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("API Error: ${response.code}")

            val bodyString = response.body?.string() ?: "{}"
            val json = JSONObject(bodyString)

            if (json.optString("result") != "success") {
                throw Exception("API returned error: ${json.optString("error-type")}")
            }

            val conversionRates = json.getJSONObject("conversion_rates")
            val map = mutableMapOf<String, Double>()

            conversionRates.keys().forEach { key ->
                map[key] = conversionRates.getDouble(key)
            }
            return map
        }
    }
}
