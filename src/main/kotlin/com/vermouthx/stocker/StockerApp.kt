package com.vermouthx.stocker

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketIndex
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class StockerApp(private val project: Project) {

    private val log = Logger.getInstance(javaClass)
    private val setting = StockerSetting.instance
    private val messageBus = project.messageBus

    private var scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    private var scheduleInitialDelay: Long = 3
    private val schedulePeriod: Long = StockerSetting.instance.refreshInterval
    @Volatile
    private var refreshActive: Boolean = false

    fun schedule() {
        if (scheduledExecutorService.isShutdown) {
            scheduledExecutorService = Executors.newScheduledThreadPool(1)
            scheduleInitialDelay = 0
        }
        refreshActive = true
        // Use single consolidated task instead of multiple overlapping tasks
        // This reduces HTTP requests by 50% and prevents redundant data fetching
        scheduledExecutorService.scheduleAtFixedRate(
            createConsolidatedUpdateThread(),
            scheduleInitialDelay,
            schedulePeriod,
            TimeUnit.SECONDS
        )
    }

    fun shutdown() {
        refreshActive = false
        // Graceful shutdown: let in-flight HTTP requests finish instead of
        // interrupting them. shutdownNow() interrupts the fetch thread mid-request,
        // which leaves stale connections in the shared PoolingHttpClientConnectionManager.
        // On restart those stale connections are reused and hang — the symptom is a
        // "unresponsive" tool window in other IDEA instances sharing the same pool.
        scheduledExecutorService.shutdown()
        try {
            if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduledExecutorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    fun isShutdown(): Boolean {
        return scheduledExecutorService.isShutdown
    }

    private fun clear() {
        // Each syncPublisher().clear() delivers synchronously to listeners.
        // If any listener throws, the exception would propagate back to the
        // caller (e.g. an action's actionPerformed), preventing schedule() from
        // running and leaving the tool window permanently unresponsive. Wrap
        // each call so one failing listener cannot break the others or the caller.
        safeClear(STOCK_ALL_QUOTE_RELOAD_TOPIC)
        safeClear(STOCK_CN_QUOTE_RELOAD_TOPIC)
        safeClear(STOCK_HK_QUOTE_RELOAD_TOPIC)
        safeClear(STOCK_US_QUOTE_RELOAD_TOPIC)
        safeClear(STOCK_CRYPTO_QUOTE_RELOAD_TOPIC)
    }

    private fun safeClear(topic: com.intellij.util.messages.Topic<StockerQuoteReloadNotifier>) {
        try {
            messageBus.syncPublisher(topic).clear()
        } catch (e: Exception) {
            log.warn("Failed to clear topic ${topic.displayName}", e)
        }
    }

    fun shutdownThenClear() {
        shutdown()
        clear()
    }

    /**
     * Consolidated update thread that fetches all market data once and publishes to all relevant topics.
     * This eliminates redundant HTTP requests that were previously made by separate per-market tasks.
     */
    private fun createConsolidatedUpdateThread(): Runnable {
        return Runnable {
            try {
                if (!shouldContinueRefresh()) {
                    return@Runnable
                }

                val quoteProvider = setting.quoteProvider
                val cryptoQuoteProvider = setting.cryptoQuoteProvider

                // Fetch all market data once
                val aShareQuotes = fetchQuotesIfActive(StockerMarketType.AShare, quoteProvider, setting.aShareList) ?: return@Runnable
                val hkStocksQuotes = fetchQuotesIfActive(StockerMarketType.HKStocks, quoteProvider, setting.hkStocksList) ?: return@Runnable
                val usStocksQuotes = fetchQuotesIfActive(StockerMarketType.USStocks, quoteProvider, setting.usStocksList) ?: return@Runnable
                val cryptoQuotes = fetchQuotesIfActive(StockerMarketType.Crypto, cryptoQuoteProvider, setting.cryptoList) ?: return@Runnable

                val aShareIndices = fetchQuotesIfActive(StockerMarketType.AShare, quoteProvider, StockerMarketIndex.CN.codes) ?: return@Runnable
                val hkStocksIndices = fetchQuotesIfActive(StockerMarketType.HKStocks, quoteProvider, StockerMarketIndex.HK.codes) ?: return@Runnable
                val usStocksIndices = fetchQuotesIfActive(StockerMarketType.USStocks, quoteProvider, StockerMarketIndex.US.codes) ?: return@Runnable
                val cryptoIndices = fetchQuotesIfActive(StockerMarketType.Crypto, cryptoQuoteProvider, StockerMarketIndex.Crypto.codes) ?: return@Runnable

                if (!shouldContinueRefresh()) {
                    return@Runnable
                }

                // Publish to individual market topics
                // Always publish indices, but only publish quotes when there are favorites
                val cnPublisher = messageBus.syncPublisher(STOCK_CN_QUOTE_UPDATE_TOPIC)
                if (setting.aShareList.isNotEmpty()) {
                    cnPublisher.syncQuotes(aShareQuotes, setting.aShareList.size)
                }
                cnPublisher.syncIndices(aShareIndices)

                val hkPublisher = messageBus.syncPublisher(STOCK_HK_QUOTE_UPDATE_TOPIC)
                if (setting.hkStocksList.isNotEmpty()) {
                    hkPublisher.syncQuotes(hkStocksQuotes, setting.hkStocksList.size)
                }
                hkPublisher.syncIndices(hkStocksIndices)

                val usPublisher = messageBus.syncPublisher(STOCK_US_QUOTE_UPDATE_TOPIC)
                if (setting.usStocksList.isNotEmpty()) {
                    usPublisher.syncQuotes(usStocksQuotes, setting.usStocksList.size)
                }
                usPublisher.syncIndices(usStocksIndices)

                val cryptoPublisher = messageBus.syncPublisher(CRYPTO_QUOTE_UPDATE_TOPIC)
                if (setting.cryptoList.isNotEmpty()) {
                    cryptoPublisher.syncQuotes(cryptoQuotes, setting.cryptoList.size)
                }
                cryptoPublisher.syncIndices(cryptoIndices)

                // Publish to "all" topic
                val allStockQuotes = listOf(aShareQuotes, hkStocksQuotes, usStocksQuotes, cryptoQuotes).flatten()
                val allStockIndices = listOf(aShareIndices, hkStocksIndices, usStocksIndices, cryptoIndices).flatten()
                val allPublisher = messageBus.syncPublisher(STOCK_ALL_QUOTE_UPDATE_TOPIC)
                allPublisher.syncQuotes(allStockQuotes, setting.allStockListSize)
                allPublisher.syncIndices(allStockIndices)
            } catch (e: Exception) {
                // A scheduled task that throws an uncaught exception is silently
                // suppressed by ScheduledExecutorService forever — the tool window
                // never refreshes again ("unresponsive"). Catch all exceptions here
                // so the task survives and retries on the next cycle.
                log.warn("StockerApp consolidated update failed; will retry next cycle", e)
            }
        }
    }

    private fun fetchQuotesIfActive(
        marketType: StockerMarketType,
        quoteProvider: StockerQuoteProvider,
        codes: List<String>
    ): List<StockerQuote>? {
        if (!shouldContinueRefresh()) {
            return null
        }
        return StockerQuoteHttpUtil.get(marketType, quoteProvider, codes)
    }

    private fun shouldContinueRefresh(): Boolean {
        return refreshActive && !Thread.currentThread().isInterrupted
    }

}
