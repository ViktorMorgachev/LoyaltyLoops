package io.loyaltyloop.server.service

import io.ktor.server.config.*
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Duration

class RedisService(config: ApplicationConfig) {

    private val logger = LoggerFactory.getLogger(RedisService::class.java)
    private val host = config.property("redis.host").getString()
    private val port = config.property("redis.port").getString().toInt()
    private val password = config.property("redis.password").getString().ifEmpty { null }
    private val timeout = config.property("redis.timeout").getString().toInt()


    private val pool: JedisPool

    init {
        // Read pool tuning from config (with sane defaults)
        val poolMaxTotal = config.int("redis.pool.maxTotal", 128)
        val poolMaxIdle = config.int("redis.pool.maxIdle", 128)
        val poolMinIdle = config.int("redis.pool.minIdle", 32)

        val testOnBorrow = config.bool("redis.pool.testOnBorrow", false)
        val testOnReturn = config.bool("redis.pool.testOnReturn", false)
        val testWhileIdle = config.bool("redis.pool.testWhileIdle", true)

        val minEvictableIdleTimeMs = config.long("redis.pool.minEvictableIdleTimeMs", 60_000L)
        val timeBetweenEvictionRunsMs = config.long("redis.pool.timeBetweenEvictionRunsMs", 30_000L)
        val numTestsPerEvictionRun = config.int("redis.pool.numTestsPerEvictionRun", 32)

        val blockWhenExhausted = config.bool("redis.pool.blockWhenExhausted", true)
        val maxWaitMs = config.long("redis.pool.maxWaitMs", 2_000L)
        val jmxEnabled = config.bool("redis.pool.jmxEnabled", true)

        val poolConfig = JedisPoolConfig().apply {
            maxTotal = poolMaxTotal
            maxIdle = poolMaxIdle
            minIdle = poolMinIdle

            this.testOnBorrow = testOnBorrow
            this.testOnReturn = testOnReturn
            this.testWhileIdle = testWhileIdle

            minEvictableIdleTime = Duration.ofMillis(minEvictableIdleTimeMs)
            timeBetweenEvictionRuns = Duration.ofMillis(timeBetweenEvictionRunsMs)
            this.numTestsPerEvictionRun = numTestsPerEvictionRun

            this.blockWhenExhausted = blockWhenExhausted
            setMaxWait(Duration.ofMillis(maxWaitMs))

            this.jmxEnabled = jmxEnabled
        }

        pool = if (password != null) {
            JedisPool(poolConfig, host, port, timeout, password)
        } else {
            JedisPool(poolConfig, host, port, timeout)

        }
        logger.info("✅ RedisService initialized: $host:$port")
    }

    fun <T> use(block: (redis.clients.jedis.Jedis) -> T): T {
        return pool.resource.use { jedis ->
            block(jedis)
        }
    }

    fun get(key: String): String? {
        return use { jedis ->
             jedis.get(key)
        }
    }

    // Updated signature to match usage in ExchangeRateService.kt
    // If ttlSeconds is null, no expiration is set
    fun set(key: String, value: String, ttlSeconds: Int? = null) {
        use { jedis ->
            if (ttlSeconds != null) {
                jedis.setex(key, ttlSeconds.toLong(), value)
            } else {
                jedis.set(key, value)
            }
        }
    }

    fun del(key: String) {
        use { jedis ->
            jedis.del(key)
        }
    }
    
    fun close() {
        pool.close()
    }

    private fun ApplicationConfig.int(path: String, default: Int): Int =
        propertyOrNull(path)?.getString()?.toIntOrNull() ?: default

    private fun ApplicationConfig.long(path: String, default: Long): Long =
        propertyOrNull(path)?.getString()?.toLongOrNull() ?: default

    private fun ApplicationConfig.bool(path: String, default: Boolean): Boolean =
        propertyOrNull(path)?.getString()?.toBooleanStrictOrNull() ?: default
}
