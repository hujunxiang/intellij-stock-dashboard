package com.vermouthx.stocker.listeners;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.IconLoader;
import com.vermouthx.stocker.StockerBundle;
import com.vermouthx.stocker.entities.StockerQuote;
import com.vermouthx.stocker.settings.StockerSetting;
import com.vermouthx.stocker.utils.StockerTableModelUtil;
import com.vermouthx.stocker.views.StockerTableView;

import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class StockerQuoteUpdateListener implements StockerQuoteUpdateNotifier {
    private final StockerTableView myTableView;
    private volatile String groupFilter = null; // null = show all
    private volatile List<StockerQuote> storedQuotes = new CopyOnWriteArrayList<>();
    private final java.util.Map<String, Double> lastNotifiedPct = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Double> lastFetchedPrice = new java.util.concurrent.ConcurrentHashMap<>();

    public void setGroupFilter(String groupFilter) {
        this.groupFilter = groupFilter;
    }

    /**
     * Called when the group filter changes. Clears the table and re-applies
     * the filter using stored quotes from the last scheduler update.
     */
    public void refreshGroupFilter() {
        List<StockerQuote> snapshot = storedQuotes;
        if (!snapshot.isEmpty()) {
            myTableView.clearTable();
            applyGroupFilter(snapshot);
        }
        // If storedQuotes is empty, next syncQuotes() will populate with filtered data
    }

    private static String formatCostPrice(Double costPrice) {
        return costPrice != null ? String.format("%.3f", costPrice) : "-";
    }

    private static Object formatHoldings(Integer holdings) {
        return holdings != null ? holdings : "-";
    }

    private static Object formatNetProfit(StockerQuote quote, Double costPrice, Integer holdings) {
        if (costPrice == null || holdings == null) {
            return "-";
        }
        return String.format("%.3f", (quote.getCurrent() - costPrice) * holdings);
    }

    private static Object formatDailyProfit(StockerQuote quote, Integer holdings) {
        if (holdings == null) {
            return "-";
        }
        return String.format("%.3f", quote.getChange() * holdings);
    }

    public StockerQuoteUpdateListener(StockerTableView myTableView) {
        this.myTableView = myTableView;
    }

    @Override
    public void syncQuotes(List<StockerQuote> quotes, int size) {
        // Store all quotes atomically (replace, don't clear+add)
        storedQuotes = new CopyOnWriteArrayList<>(quotes);
        applyGroupFilter(quotes);
    }

    /**
     * Apply group filter and update the table with the given quotes.
     * Called from syncQuotes() on scheduler update and from refreshGroupFilter()
     * when the group changes.
     */
    private void applyGroupFilter(List<StockerQuote> quotes) {
        DefaultTableModel tableModel = myTableView.getTableModel();
        StockerSetting setting = StockerSetting.Companion.getInstance();

        String currentGroup = groupFilter;
        final List<StockerQuote> filteredQuotes;

        if (currentGroup != null && currentGroup.isEmpty()) {
            // "全部": aggregate all groups, deduplicate, preserve order
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (String groupName : setting.getStockGroupNames()) {
                seen.addAll(setting.getGroupStocks(groupName));
            }
            List<String> allCodes = new java.util.ArrayList<>(seen);
            java.util.Map<String, Integer> position = new java.util.HashMap<>();
            for (int i = 0; i < allCodes.size(); i++) {
                position.put(allCodes.get(i), i);
            }
            filteredQuotes = quotes.stream()
                    .filter(q -> position.containsKey(q.getCode()))
                    .sorted((q1, q2) -> Integer.compare(
                            position.getOrDefault(q1.getCode(), Integer.MAX_VALUE),
                            position.getOrDefault(q2.getCode(), Integer.MAX_VALUE)))
                    .collect(Collectors.toList());

            synchronized (tableModel) {
                tableModel.setRowCount(0);
                for (StockerQuote quote : filteredQuotes) {
                    Double costPrice = setting.getCostPrice(quote.getCode());
                    Integer holdings = setting.getHoldings(quote.getCode());
                    String displayName = setting.getDisplayName(quote.getCode(), quote.getName());
                    tableModel.addRow(new Object[]{
                            quote.getCode(), displayName,
                            quote.getCurrent(), quote.getOpening(), quote.getClose(),
                            quote.getLow(), quote.getHigh(),
                            quote.getChange(), quote.getPercentage() + "%",
                            formatCostPrice(costPrice), formatHoldings(holdings),
                            formatNetProfit(quote, costPrice, holdings),
                            formatDailyProfit(quote, holdings)
                    });
                }
            }
        } else if (currentGroup != null) {
            List<String> groupCodes = setting.getGroupStocks(currentGroup);
            java.util.Map<String, Integer> groupPosition = new java.util.HashMap<>();
            for (int i = 0; i < groupCodes.size(); i++) {
                groupPosition.put(groupCodes.get(i), i);
            }
            filteredQuotes = quotes.stream()
                    .filter(q -> groupCodes.contains(q.getCode()))
                    .sorted((q1, q2) -> {
                        Integer p1 = groupPosition.get(q1.getCode());
                        Integer p2 = groupPosition.get(q2.getCode());
                        return Integer.compare(
                                p1 != null ? p1 : Integer.MAX_VALUE,
                                p2 != null ? p2 : Integer.MAX_VALUE);
                    })
                    .collect(Collectors.toList());

            synchronized (tableModel) {
                tableModel.setRowCount(0);
                for (StockerQuote quote : filteredQuotes) {
                    Double costPrice = setting.getCostPrice(quote.getCode());
                    Integer holdings = setting.getHoldings(quote.getCode());
                    String displayName = setting.getDisplayName(quote.getCode(), quote.getName());
                    tableModel.addRow(new Object[]{
                            quote.getCode(), displayName,
                            quote.getCurrent(), quote.getOpening(), quote.getClose(),
                            quote.getLow(), quote.getHigh(),
                            quote.getChange(), quote.getPercentage() + "%",
                            formatCostPrice(costPrice), formatHoldings(holdings),
                            formatNetProfit(quote, costPrice, holdings),
                            formatDailyProfit(quote, holdings)
                    });
                }
            }
        } else {
            filteredQuotes = quotes;
            filteredQuotes.forEach(quote -> {
                synchronized (tableModel) {
                    String displayName = setting.getDisplayName(quote.getCode(), quote.getName());
                    int rowIndex = StockerTableModelUtil.existAt(tableModel, quote.getCode());
                    if (rowIndex != -1) {
                        if (!tableModel.getValueAt(rowIndex, 1).equals(displayName)) {
                            tableModel.setValueAt(displayName, rowIndex, 1);
                            tableModel.fireTableCellUpdated(rowIndex, 1);
                        }
                        if (!tableModel.getValueAt(rowIndex, 2).equals(quote.getCurrent())) {
                            tableModel.setValueAt(quote.getCurrent(), rowIndex, 2);
                            tableModel.fireTableCellUpdated(rowIndex, 2);
                        }
                        if (!tableModel.getValueAt(rowIndex, 3).equals(quote.getOpening())) {
                            tableModel.setValueAt(quote.getOpening(), rowIndex, 3);
                            tableModel.fireTableCellUpdated(rowIndex, 3);
                        }
                        if (!tableModel.getValueAt(rowIndex, 4).equals(quote.getClose())) {
                            tableModel.setValueAt(quote.getClose(), rowIndex, 4);
                            tableModel.fireTableCellUpdated(rowIndex, 4);
                        }
                        if (!tableModel.getValueAt(rowIndex, 5).equals(quote.getLow())) {
                            tableModel.setValueAt(quote.getLow(), rowIndex, 5);
                            tableModel.fireTableCellUpdated(rowIndex, 5);
                        }
                        if (!tableModel.getValueAt(rowIndex, 6).equals(quote.getHigh())) {
                            tableModel.setValueAt(quote.getHigh(), rowIndex, 6);
                            tableModel.fireTableCellUpdated(rowIndex, 6);
                        }
                        if (!tableModel.getValueAt(rowIndex, 7).equals(quote.getChange())) {
                            tableModel.setValueAt(quote.getChange(), rowIndex, 7);
                            tableModel.fireTableCellUpdated(rowIndex, 7);
                        }
                        if (!tableModel.getValueAt(rowIndex, 8).equals(quote.getPercentage())) {
                            tableModel.setValueAt(quote.getPercentage() + "%", rowIndex, 8);
                            tableModel.fireTableCellUpdated(rowIndex, 8);
                        }
                        Double costPrice = setting.getCostPrice(quote.getCode());
                        String costPriceStr = formatCostPrice(costPrice);
                        if (!costPriceStr.equals(tableModel.getValueAt(rowIndex, 9))) {
                            tableModel.setValueAt(costPriceStr, rowIndex, 9);
                            tableModel.fireTableCellUpdated(rowIndex, 9);
                        }
                        Integer holdings = setting.getHoldings(quote.getCode());
                        Object holdingsVal = formatHoldings(holdings);
                        if (!holdingsVal.equals(tableModel.getValueAt(rowIndex, 10))) {
                            tableModel.setValueAt(holdingsVal, rowIndex, 10);
                            tableModel.fireTableCellUpdated(rowIndex, 10);
                        }
                        Object netProfitVal = formatNetProfit(quote, costPrice, holdings);
                        if (!netProfitVal.equals(tableModel.getValueAt(rowIndex, 11))) {
                            tableModel.setValueAt(netProfitVal, rowIndex, 11);
                            tableModel.fireTableCellUpdated(rowIndex, 11);
                        }
                        Object dailyProfitVal = formatDailyProfit(quote, holdings);
                        if (!dailyProfitVal.equals(tableModel.getValueAt(rowIndex, 12))) {
                            tableModel.setValueAt(dailyProfitVal, rowIndex, 12);
                            tableModel.fireTableCellUpdated(rowIndex, 12);
                        }
                    } else {
                        Double costPrice = setting.getCostPrice(quote.getCode());
                        Integer holdings = setting.getHoldings(quote.getCode());
                        tableModel.addRow(new Object[]{
                                quote.getCode(), displayName,
                                quote.getCurrent(), quote.getOpening(), quote.getClose(),
                                quote.getLow(), quote.getHigh(),
                                quote.getChange(), quote.getPercentage() + "%",
                                formatCostPrice(costPrice), formatHoldings(holdings),
                                formatNetProfit(quote, costPrice, holdings),
                                formatDailyProfit(quote, holdings)
                        });
                        myTableView.clearSortState();
                    }
                }
            });
        }

        checkThresholdAlerts(filteredQuotes, setting);
    }

    private void checkThresholdAlerts(List<StockerQuote> quotes, StockerSetting setting) {
        int rise = setting.getRiseThreshold();
        int fall = setting.getFallThreshold();
        if (rise == 0 && fall == 0) return;

        // Determine colors based on color pattern setting
        String riseColor;
        String fallColor;
        switch (setting.getQuoteColorPattern()) {
            case GREEN_UP_RED_DOWN:
                riseColor = "#008000"; // green
                fallColor = "#CC0000"; // red
                break;
            case NONE:
                riseColor = "#808080"; // gray
                fallColor = "#808080";
                break;
            default: // RED_UP_GREEN_DOWN
                riseColor = "#CC0000"; // red
                fallColor = "#008000"; // green
                break;
        }

        java.util.List<String> riseItems = new java.util.ArrayList<>();
        java.util.List<String> fallItems = new java.util.ArrayList<>();

        for (StockerQuote quote : quotes) {
            String code = quote.getCode();
            double currentPrice = quote.getCurrent();
            Double prevPrice = lastFetchedPrice.get(code);
            lastFetchedPrice.put(code, currentPrice);

            if (prevPrice == null || prevPrice == 0) continue; // First fetch, no previous price to compare

            double pct = (currentPrice - prevPrice) / prevPrice * 100;

            if (rise > 0 && pct >= rise) {
                Double lastPct = lastNotifiedPct.get(code);
                if (lastPct != null && Math.abs(lastPct - pct) < 0.01) continue;
                lastNotifiedPct.put(code, pct);
                riseItems.add(String.format("<span style='color:%s'>&#x25B2; %s <b>+%s%%</b></span>",
                        riseColor, quote.getName(), String.format("%.2f", pct)));
            } else if (fall < 0 && pct <= fall) {
                Double lastPct = lastNotifiedPct.get(code);
                if (lastPct != null && Math.abs(lastPct - pct) < 0.01) continue;
                lastNotifiedPct.put(code, pct);
                fallItems.add(String.format("<span style='color:%s'>&#x25BC; %s <b>%s%%</b></span>",
                        fallColor, quote.getName(), String.format("%.2f", pct)));
            } else {
                lastNotifiedPct.remove(code);
            }
        }

        if (riseItems.isEmpty() && fallItems.isEmpty()) return;

        // Get IDE theme colors for background
        String bgColor = javax.swing.UIManager.getColor("Panel.background") != null
                ? String.format("#%06x", javax.swing.UIManager.getColor("Panel.background").getRGB() & 0xFFFFFF)
                : "#FFFFFF";

        StringBuilder html = new StringBuilder("<html><body style='width:280px;line-height:1.6;background:" + bgColor + "'>");
        for (String item : riseItems) html.append(item).append("<br>");
        for (String item : fallItems) html.append(item).append("<br>");
        html.append("</body></html>");

        String title = StockerBundle.msg("alert.title");

        try {
            com.intellij.notification.Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("StockerPlus")
                    .createNotification(title, html.toString(), NotificationType.INFORMATION)
                    .setIcon(IconLoader.getIcon("/icons/logo.png", getClass()));
            notification.notify(null);

            // Auto-dismiss after 1 second
            new javax.swing.Timer(1000, e -> {
                try { notification.expire(); } catch (Exception ignored) {}
            }) {{ setRepeats(false); }}.start();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void syncIndices(List<StockerQuote> indices) {
        synchronized (myTableView) {
            myTableView.syncIndices(indices);
        }
    }

}
