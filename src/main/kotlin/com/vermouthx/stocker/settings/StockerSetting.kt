package com.vermouthx.stocker.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteColorPattern
import com.vermouthx.stocker.enums.StockerQuoteProvider
import com.vermouthx.stocker.enums.StockerTableColumn
import com.vermouthx.stocker.utils.StockerPinyinUtil

@State(name = "StockerPlus", storages = [Storage("stocker-plus-config.xml")])
class StockerSetting : PersistentStateComponent<StockerSettingState> {
    private var myState = StockerSettingState()

    private val log = Logger.getInstance(javaClass)

    companion object {
        val instance: StockerSetting
            get() = ApplicationManager.getApplication().getService(StockerSetting::class.java)
    }

    var version: String
        get() = myState.version
        set(value) {
            myState.version = value
            log.info("StockerPlus updated to $value")
        }

    var quoteProvider: StockerQuoteProvider
        get() = myState.quoteProvider
        set(value) {
            myState.quoteProvider = value
            log.info("StockerPlus stock quote provider switched to ${value.title}")
        }

    var cryptoQuoteProvider: StockerQuoteProvider
        get() = myState.cryptoQuoteProvider
        set(value) {
            myState.cryptoQuoteProvider = value
            log.info("StockerPlus crypto quote provider switched to ${value.title}")
        }

    var quoteColorPattern: StockerQuoteColorPattern
        get() = myState.quoteColorPattern
        set(value) {
            myState.quoteColorPattern = value
            log.info("StockerPlus quote color pattern switched to ${value.title}")
        }

    var displayNameWithPinyin: Boolean
        get() = myState.displayNameWithPinyin
        set(value) {
            myState.displayNameWithPinyin = value
            log.info("StockerPlus display name with pinyin set to $value")
        }

    var languageOverride: String
        get() = myState.languageOverride
        set(value) {
            myState.languageOverride = value
            log.info("StockerPlus language override set to $value")
        }

    var visibleTableColumns: List<String>
        get() {
            val stored = myState.visibleTableColumns
            if (stored.isEmpty()) return StockerTableColumn.defaultVisibleNames()
            if (stored.all { name -> StockerTableColumn.fromName(name) != null }) return stored
            val migrated = stored.mapNotNull { StockerTableColumn.migrateLocalizedTitle(it) }
            if (migrated.isNotEmpty()) {
                myState.visibleTableColumns = migrated.toMutableList()
                log.info("Migrated visibleTableColumns from localized titles to enum names: $migrated")
                return migrated
            }
            return StockerTableColumn.defaultVisibleNames()
        }
        set(value) {
            myState.visibleTableColumns = value.toMutableList()
            log.info("StockerPlus visible table columns updated: $value")
        }

    var refreshInterval: Long
        get() = myState.refreshInterval
        set(value) {
            myState.refreshInterval = value
            log.info("StockerPlus refresh interval set to $value")
        }

    var aShareList: MutableList<String>
        get() = myState.aShareList
        set(value) {
            myState.aShareList = value
        }

    var hkStocksList: MutableList<String>
        get() = myState.hkStocksList
        set(value) {
            myState.hkStocksList = value
        }

    var usStocksList: MutableList<String>
        get() = myState.usStocksList
        set(value) {
            myState.usStocksList = value
        }

    var cryptoList: MutableList<String>
        get() = myState.cryptoList
        set(value) {
            myState.cryptoList = value
        }

    var customStockNames: MutableMap<String, String>
        get() = myState.customStockNames
        set(value) {
            myState.customStockNames = value
        }

    var stockCostPrices: MutableMap<String, Double>
        get() = myState.stockCostPrices
        set(value) {
            myState.stockCostPrices = value
        }

    var stockHoldings: MutableMap<String, Int>
        get() = myState.stockHoldings
        set(value) {
            myState.stockHoldings = value
        }

    var stockGroupNames: MutableList<String>
        get() = myState.stockGroupNames
        set(value) {
            myState.stockGroupNames = value
        }

    var stockGroupMap: MutableMap<String, MutableList<String>>
        get() = myState.stockGroupMap
        set(value) {
            myState.stockGroupMap = value
        }

    var lastSelectedGroup: String
        get() = myState.lastSelectedGroup
        set(value) {
            myState.lastSelectedGroup = value
        }

    fun ensureGroupsMigrated() {
        if (stockGroupMap.isEmpty() && allStockListSize > 0) {
            val defaultName = "默认"
            val allCodes = (aShareList + hkStocksList + usStocksList + cryptoList).toMutableList()
            stockGroupNames.clear()
            stockGroupNames.add(defaultName)
            stockGroupMap.clear()
            stockGroupMap[defaultName] = allCodes
            log.info("Migrated ${allCodes.size} stocks into default group")
        }
    }

    fun addGroup(name: String): Boolean {
        if (name.isBlank() || stockGroupNames.contains(name)) return false
        stockGroupNames.add(name)
        stockGroupMap[name] = mutableListOf()
        log.info("Added group: $name")
        return true
    }

    fun removeGroup(name: String) {
        stockGroupNames.remove(name)
        stockGroupMap.remove(name)
        if (lastSelectedGroup == name) lastSelectedGroup = ""
        log.info("Removed group: $name")
    }

    fun renameGroup(oldName: String, newName: String): Boolean {
        if (oldName == newName) return true
        if (newName.isBlank() || stockGroupNames.contains(newName)) return false
        val idx = stockGroupNames.indexOf(oldName)
        if (idx < 0) return false
        stockGroupNames[idx] = newName
        val codes = stockGroupMap.remove(oldName)
        if (codes != null) {
            stockGroupMap[newName] = codes
        }
        if (lastSelectedGroup == oldName) lastSelectedGroup = newName
        log.info("Renamed group: $oldName -> $newName")
        return true
    }

    fun getGroupStocks(groupName: String): List<String> {
        return stockGroupMap[groupName] ?: emptyList()
    }

    fun getStockGroup(code: String): String? {
        for ((groupName, codes) in stockGroupMap) {
            if (codes.contains(code)) return groupName
        }
        return null
    }

    fun assignStockToGroup(code: String, groupName: String) {
        // Remove from previous group first
        removeStockFromGroup(code)
        val codes = stockGroupMap[groupName]
        if (codes != null) {
            if (!codes.contains(code)) codes.add(code)
        } else {
            stockGroupMap[groupName] = mutableListOf(code)
            if (!stockGroupNames.contains(groupName)) stockGroupNames.add(groupName)
        }
    }

    fun removeStockFromGroup(code: String) {
        for ((_, codes) in stockGroupMap) {
            codes.remove(code)
        }
    }

    fun clearGroup(groupName: String) {
        val codes = stockGroupMap[groupName] ?: return
        for (code in codes.toList()) {
            removeCodeByCode(code)
        }
        stockGroupMap.remove(groupName)
        stockGroupNames.remove(groupName)
        if (lastSelectedGroup == groupName) lastSelectedGroup = ""
        log.info("Cleared group: $groupName")
    }

    fun clearAllStocks() {
        aShareList.clear()
        hkStocksList.clear()
        usStocksList.clear()
        cryptoList.clear()
        stockGroupNames.clear()
        stockGroupMap.clear()
        lastSelectedGroup = ""
        log.info("Cleared all stocks and groups")
    }

    fun cleanupGroupMap() {
        val allCodes = (aShareList + hkStocksList + usStocksList + cryptoList).toSet()
        for ((_, codes) in stockGroupMap) {
            codes.removeAll { it !in allCodes }
        }
        stockGroupMap.entries.removeAll { it.value.isEmpty() }
        stockGroupNames.removeAll { !stockGroupMap.containsKey(it) }
    }

    private fun removeCodeByCode(code: String) {
        aShareList.remove(code)
        hkStocksList.remove(code)
        usStocksList.remove(code)
        cryptoList.remove(code)
    }

    val allStockListSize: Int
        get() = aShareList.size + hkStocksList.size + usStocksList.size + cryptoList.size

    fun setCustomName(code: String, customName: String) {
        customStockNames[code] = customName
        log.info("Custom name set for $code: $customName")
    }

    fun getCustomName(code: String): String? {
        return customStockNames[code]
    }

    fun removeCustomName(code: String) {
        customStockNames.remove(code)
        log.info("Custom name removed for $code")
    }

    fun setCostPrice(code: String, costPrice: Double) {
        val rounded = Math.round(costPrice * 1000.0) / 1000.0
        stockCostPrices[code] = rounded
        log.info("Cost price set for $code: $rounded")
    }

    fun getCostPrice(code: String): Double? {
        return stockCostPrices[code]
    }

    fun removeCostPrice(code: String) {
        stockCostPrices.remove(code)
        log.info("Cost price removed for $code")
    }

    fun setHoldings(code: String, holdings: Int) {
        stockHoldings[code] = holdings
        log.info("Holdings set for $code: $holdings")
    }

    fun getHoldings(code: String): Int? {
        return stockHoldings[code]
    }

    fun removeHoldings(code: String) {
        stockHoldings.remove(code)
        log.info("Holdings removed for $code")
    }

    fun getDisplayName(code: String, originalName: String): String {
        // Priority: Custom name > Pinyin mode > Original name
        customStockNames[code]?.let { return it }
        if (displayNameWithPinyin) {
            return StockerPinyinUtil.toPinyin(originalName)
        }
        return originalName
    }

    fun isTableColumnVisible(column: StockerTableColumn): Boolean {
        return visibleTableColumns.contains(column.name)
    }

    fun containsCode(code: String): Boolean {
        return aShareList.contains(code) ||
                hkStocksList.contains(code) ||
                usStocksList.contains(code) ||
                cryptoList.contains(code)
    }

    fun marketOf(code: String): StockerMarketType? {
        if (aShareList.contains(code)) {
            return StockerMarketType.AShare
        }
        if (hkStocksList.contains(code)) {
            return StockerMarketType.HKStocks
        }
        if (usStocksList.contains(code)) {
            return StockerMarketType.USStocks
        }
        if (cryptoList.contains(code)) {
            return StockerMarketType.Crypto
        }
        return null
    }

    fun removeCode(market: StockerMarketType, code: String) {
        when (market) {
            StockerMarketType.AShare -> {
                synchronized(aShareList) {
                    aShareList.remove(code)
                }
            }

            StockerMarketType.HKStocks -> {
                synchronized(hkStocksList) {
                    hkStocksList.remove(code)
                }
            }

            StockerMarketType.USStocks -> {
                synchronized(usStocksList) {
                    usStocksList.remove(code)
                }
            }

            StockerMarketType.Crypto -> {
                synchronized(cryptoList) {
                    cryptoList.remove(code)
                }
            }
        }
    }

    var enabledMarkets: MutableList<String>
        get() = myState.enabledMarkets
        set(value) {
            myState.enabledMarkets = value
        }

    fun isMarketEnabled(market: StockerMarketType): Boolean {
        if (!myState.marketsConfigured) return true // Legacy: all enabled
        return myState.enabledMarkets.contains(market.name)
    }

    fun setMarketEnabled(market: StockerMarketType, enabled: Boolean) {
        myState.marketsConfigured = true
        val stored = myState.enabledMarkets
        if (enabled) {
            if (!stored.contains(market.name)) stored.add(market.name)
        } else {
            stored.remove(market.name)
        }
    }

    override fun getState(): StockerSettingState {
        return myState
    }

    override fun loadState(state: StockerSettingState) {
        myState = state
    }

}
