package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class StockerHttpClientPool(
    private val log: Logger,
) {

    @Volatile
    private var connectionManager: PoolingHttpClientConnectionManager? = null

    @Volatile
    private var httpClient: CloseableHttpClient? = null

    private var evictionScheduler: ScheduledExecutorService? = null

    @Synchronized
    fun client(): CloseableHttpClient {
        httpClient?.let { return it }

        val connectionManager = PoolingHttpClientConnectionManager().apply {
            maxTotal = 20
            defaultMaxPerRoute = 10
            // Validate connections after 30s of inactivity before reuse, so a
            // connection left stale by an interrupted request is discarded instead
            // of being reused (which would hang the next fetch).
            setValidateAfterInactivity(30000)
        }
        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(10000)
            .setSocketTimeout(15000)
            .setConnectionRequestTimeout(5000)
            .build()
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .useSystemProperties()
            .build()

        this.connectionManager = connectionManager
        this.httpClient = httpClient

        // Background eviction: periodically close expired and idle connections so
        // a stale connection from an interrupted request does not accumulate.
        if (evictionScheduler == null) {
            evictionScheduler = Executors.newSingleThreadScheduledExecutor().also {
                it.scheduleAtFixedRate({
                    runCatching {
                        connectionManager.closeExpiredConnections()
                        connectionManager.closeIdleConnections(30, TimeUnit.SECONDS)
                    }.onFailure(log::warn)
                }, 30L, 30L, TimeUnit.SECONDS)
            }
        }

        return httpClient
    }

    @Synchronized
    fun close() {
        val client = httpClient
        val manager = connectionManager
        httpClient = null
        connectionManager = null

        runCatching { client?.close() }
            .onFailure(log::warn)
        runCatching { manager?.shutdown() }
            .onFailure(log::warn)

        evictionScheduler?.let { scheduler ->
            runCatching { scheduler.shutdownNow() }
            evictionScheduler = null
        }
    }
}
