package com.vermouthx.stocker.utils

import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.enums.StockerMarketType
import java.io.StringWriter

data class StockerExportEntry(
    val code: String,
    val customName: String?,
    val group: String?
)

/**
 * Export payload.
 * @param exportGroups  when true the `groups` array is emitted in JSON (used for
 *                      "all" and "group" exports). For "selected stocks" this is false.
 */
data class StockerExportData(
    val exportGroups: Boolean,
    val groups: List<String>,
    val stocks: List<StockerExportEntry>
)

class StockerDataExporter {

    private val setting get() = StockerSetting.instance

    fun buildAllData(): StockerExportData {
        val stocks = mutableListOf<StockerExportEntry>()
        for (code in setting.aShareList) stocks.add(exportEntry(code))
        for (code in setting.hkStocksList) stocks.add(exportEntry(code))
        for (code in setting.usStocksList) stocks.add(exportEntry(code))
        for (code in setting.cryptoList) stocks.add(exportEntry(code))

        return StockerExportData(
            exportGroups = true,
            groups = setting.stockGroupNames.toList(),
            stocks = stocks
        )
    }

    /**
     * @param selectedGroups     group names chosen (multi-select)
     * @param selectedStocks     individually chosen stock codes
     * @param defaultGroupName   when only individual stocks are selected (no groups),
     *                           wrap them under this synthetic group so that the
     *                           export preserves grouping information.
     *
     *  - Groups export keeps `groups` and group info on every stock.
     *  - Individual stock export wraps all of them in `defaultGroupName`.
     */
    fun buildCombinedData(
        selectedGroups: Set<String>,
        selectedStocks: List<Pair<String, StockerMarketType>>,
        defaultGroupName: String? = null
    ): StockerExportData {
        val stocks = mutableListOf<StockerExportEntry>()
        val seen = HashSet<String>()
        val exportGroups = selectedGroups.isNotEmpty() || defaultGroupName != null
        val emittedGroups = LinkedHashSet<String>()

        // 1) Stocks from groups — keep group info
        for (groupName in selectedGroups) {
            for (code in setting.getGroupStocks(groupName)) {
                if (seen.add(code)) {
                    val g = setting.getStockGroup(code)
                    if (g != null) emittedGroups.add(g)
                    stocks.add(StockerExportEntry(code, setting.getCustomName(code), g))
                }
            }
        }

        // 2) Individually selected stocks — when a synthetic default-group name is
        //    supplied (the "specific stock(s)" scope), wrap every chosen stock
        //    under that one group and do NOT preserve each stock's real group.
        //    When no default-group name is supplied the per-stock group is kept
        //    (legacy behaviour — not exercised by the current UI).
        for ((code, _) in selectedStocks) {
            if (seen.add(code)) {
                val g = when {
                    defaultGroupName != null -> {
                        emittedGroups.add(defaultGroupName)
                        defaultGroupName
                    }
                    else -> setting.getStockGroup(code)
                }
                if (g != null) emittedGroups.add(g)
                stocks.add(StockerExportEntry(code, setting.getCustomName(code), g))
            }
        }

        return StockerExportData(
            exportGroups = exportGroups,
            groups = emittedGroups.toList(),
            stocks = stocks
        )
    }

    private fun exportEntry(code: String): StockerExportEntry {
        return StockerExportEntry(
            code = code,
            customName = setting.getCustomName(code),
            group = setting.getStockGroup(code)
        )
    }

    // ------------------------------------------------------------------ //
    //  Serialization  (pretty-printed)                                   //
    // ------------------------------------------------------------------ //

    fun serialize(data: StockerExportData): String {
        val sw = StringWriter()
        sw.append("{\n")
        var indent = 2

        if (data.exportGroups && data.groups.isNotEmpty()) {
            appendIndent(sw, indent)
            sw.append("\"groups\": [")
            if (data.groups.size == 1) {
                sw.append("\"").append(escapeJson(data.groups[0])).append("\"")
            } else {
                sw.append("\n")
                for ((i, g) in data.groups.withIndex()) {
                    appendIndent(sw, indent + 2)
                    sw.append("\"").append(escapeJson(g)).append("\"")
                    if (i < data.groups.lastIndex) sw.append(",")
                    sw.append("\n")
                }
                appendIndent(sw, indent)
            }
            sw.append("]")
            if (data.stocks.isNotEmpty()) sw.append(",")
            sw.append("\n")
        }

        appendIndent(sw, indent)
        sw.append("\"stocks\": [")
        if (data.stocks.isEmpty()) {
            sw.append("]\n}")
            return sw.toString()
        }
        sw.append("\n")
        for ((i, stock) in data.stocks.withIndex()) {
            appendIndent(sw, indent + 2)
            sw.append("{\n")
            appendIndent(sw, indent + 4)
            sw.append("\"code\": \"").append(escapeJson(stock.code)).append("\"")
            stock.customName?.let {
                sw.append(",\n")
                appendIndent(sw, indent + 4)
                sw.append("\"customName\": \"").append(escapeJson(it)).append("\"")
            }
            stock.group?.let {
                sw.append(",\n")
                appendIndent(sw, indent + 4)
                sw.append("\"group\": \"").append(escapeJson(it)).append("\"")
            }
            sw.append("\n")
            appendIndent(sw, indent + 2)
            sw.append("}")
            if (i < data.stocks.lastIndex) sw.append(",")
            sw.append("\n")
        }
        appendIndent(sw, indent)
        sw.append("]\n}")
        return sw.toString()
    }

    private fun appendIndent(sw: StringWriter, spaces: Int) {
        for (i in 0 until spaces) sw.append(' ')
    }

    // ------------------------------------------------------------------ //
    //  Deserialization                                                   //
    // ------------------------------------------------------------------ //

    fun deserialize(text: String): StockerExportData? {
        return try {
            val json = trimJson(text)

            // -- groups (optional) --
            val groups = extractJsonArray(json, "groups").toList()
            val exportGroups = groups.isNotEmpty()
                    || json.contains("\"groups\"")

            // -- stocks (required) --
            // Dedupe by code, keeping the FIRST occurrence.  Older exports
            // (指定分组 + 指定股票 overlap, or the legacy synthetic "导出"
            // group) can otherwise repeat the same code more than once, which
            // then accumulates duplicates inside the target groups.
            val all = parseStocksArray(json)
            val seenCodes = HashSet<String>()
            val stocks = ArrayList<StockerExportEntry>(all.size)
            for (e in all) {
                if (seenCodes.add(e.code)) stocks.add(e)
            }

            StockerExportData(exportGroups = exportGroups, groups = groups, stocks = stocks)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStocksArray(json: String): List<StockerExportEntry> {
        val result = mutableListOf<StockerExportEntry>()
        val keyIdx = json.indexOf("\"stocks\"")
        if (keyIdx == -1) return emptyList()
        val arrStart = json.indexOf('[', keyIdx + 7)
        if (arrStart == -1) return emptyList()

        var depth = 0
        var objStart = -1
        for (i in arrStart + 1 until json.length) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart != -1) {
                        val objStr = json.substring(objStart, i + 1)
                        parseStockEntry(objStr)?.let { result.add(it) }
                        objStart = -1
                    }
                }
            }
        }
        return result
    }

    private fun parseStockEntry(objStr: String): StockerExportEntry? {
        val code = extractJsonString(objStr, "code") ?: return null
        val customName = extractJsonString(objStr, "customName")
        val group = extractJsonString(objStr, "group")
        return StockerExportEntry(code, customName, group)
    }

    // ------------------------------------------------------------------ //
    //  JSON helpers                                                      //
    // ------------------------------------------------------------------ //

    private fun trimJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun extractJsonString(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        val keyIndex = json.indexOf(keyPattern)
        if (keyIndex == -1) return null
        val colonIndex = json.indexOf(':', keyIndex + keyPattern.length)
        if (colonIndex == -1) return null

        val afterColon = json.substring(colonIndex + 1).trimStart()
        if (afterColon.startsWith("null") || afterColon.startsWith(",")) return null
        if (!afterColon.startsWith("\"")) return null

        val sb = StringBuilder()
        var i = 1
        while (i < afterColon.length) {
            val c = afterColon[i]
            if (c == '\\' && i + 1 < afterColon.length) {
                when (afterColon[i + 1]) {
                    '"'  -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/'  -> sb.append('/')
                    'n'  -> sb.append('\n')
                    'r'  -> sb.append('\r')
                    't'  -> sb.append('\t')
                    else -> { sb.append('\\'); sb.append(afterColon[i + 1]) }
                }
                i += 2
            } else if (c == '"') {
                break
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun extractJsonArray(json: String, key: String): Array<String> {
        val keyPattern = "\"$key\""
        val keyIndex = json.indexOf(keyPattern)
        if (keyIndex == -1) return emptyArray()
        val bracketStart = json.indexOf('[', keyIndex + keyPattern.length)
        if (bracketStart == -1) return emptyArray()
        val bracketEnd = json.indexOf(']', bracketStart)
        if (bracketEnd == -1) return emptyArray()

        val arrayContent = json.substring(bracketStart + 1, bracketEnd).trim()
        if (arrayContent.isEmpty()) return emptyArray()

        return arrayContent.split(",").mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
                trimmed.substring(1, trimmed.length - 1)
            } else null
        }.toTypedArray()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
