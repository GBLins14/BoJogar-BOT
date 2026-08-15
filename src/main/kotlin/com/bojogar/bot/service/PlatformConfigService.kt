package com.bojogar.bot.service

import com.bojogar.bot.entity.PlatformConfig
import com.bojogar.bot.repository.PlatformConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference

@Service
class PlatformConfigService(
    private val repository: PlatformConfigRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(PlatformConfigService::class.java)
        private const val KEY = "SINGLETON"
    }

    private val cache = AtomicReference<PlatformConfig?>(null)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun getConfig(): PlatformConfig {
        cache.get()?.let { return it }
        val config = repository.findById(KEY).orElseGet {
            log.info("Creating default platform config")
            repository.save(PlatformConfig())
        }
        cache.set(config)
        return config
    }

    fun getMinPrice(): BigDecimal = getConfig().minPrice
    fun getMaxPrice(): BigDecimal = getConfig().maxPrice
    fun getPlatformFeePercent(): Int = getConfig().platformFeePercent

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateMinPrice(value: BigDecimal): PlatformConfig {
        val config = repository.findById(KEY).orElseGet { repository.save(PlatformConfig()) }
        config.minPrice = value
        val saved = repository.save(config)
        cache.set(saved)
        log.info("Platform minPrice updated to {}", value)
        return saved
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateMaxPrice(value: BigDecimal): PlatformConfig {
        val config = repository.findById(KEY).orElseGet { repository.save(PlatformConfig()) }
        config.maxPrice = value
        val saved = repository.save(config)
        cache.set(saved)
        log.info("Platform maxPrice updated to {}", value)
        return saved
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updatePlatformFeePercent(value: Int): PlatformConfig {
        val config = repository.findById(KEY).orElseGet { repository.save(PlatformConfig()) }
        config.platformFeePercent = value
        val saved = repository.save(config)
        cache.set(saved)
        log.info("Platform fee updated to {}%", value)
        return saved
    }
}
