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
        val poolConfig = JedisPoolConfig().apply {
            // --- 1. РАЗМЕР ПУЛА (Scaling) ---
            // Для High-Load лучше держать размер пула фиксированным (maxTotal == maxIdle).
            // Это предотвращает постоянное создание и уничтожение объектов соединений при скачках трафика.
            // Если у тебя Ktor (Netty) обрабатывает много запросов, 128 - хорошее начало.
            maxTotal = 128
            maxIdle = 128
            minIdle = 32   // Всегда держим горячий запас

            // --- 2. ПРОИЗВОДИТЕЛЬНОСТЬ (Speed) ---
            // КРИТИЧНО: Отключаем проверки "на лету".
            // Мы не хотим пинговать Redis при каждом запросе клиента.
            testOnBorrow = false
            testOnReturn = false

            // --- 3. ФОНОВОЕ ЗДОРОВЬЕ (Health Check) ---
            // Включаем "Санитара", который работает в отдельном потоке.
            testWhileIdle = true

            // Как часто запускать проверку (раз в 30 сек)
            timeBetweenEvictionRuns = Duration.ofSeconds(30)

            // Математика для High-Load:
            // У нас 128 соединений. Мы хотим проверить их все примерно за 2 минуты,
            // чтобы балансировщик Railway не успел их убить (обычно таймаут 5-10 мин).
            // 128 соединений / 4 запуска (2 мин / 30 сек) = 32.
            // Проверяем по 32 соединения за раз.
            numTestsPerEvictionRun = 32

            // Если соединение просто висит без дела 60 секунд - проверяем его пингом.
            minEvictableIdleTime = Duration.ofSeconds(60)

            // --- 4. ЗАЩИТА ОТ ЗАВИСАНИЙ (Fail Fast) ---
            // Если все 128 соединений заняты, ждем максимум 2 секунды.
            // Если не дождались — кидаем ошибку.
            // Лучше упасть с ошибкой "Server Busy", чем подвесить поток Ktor навсегда.
            blockWhenExhausted = true
            setMaxWait(Duration.ofSeconds(2))

            // Включаем JMX мониторинг (полезно, если подключишь Prometheus/Grafana)
            jmxEnabled = true
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
}
