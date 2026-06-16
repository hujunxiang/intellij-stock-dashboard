package com.vermouthx.stocker.listeners;

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

    public void setGroupFilter(String groupFilter) {
        this.groupFilter = (groupFilter != null && !groupFilter.isEmpty()) ? groupFilter : null;
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

        // Filter by group if a group filter is active
        String currentGroup = groupFilter;
        final List<StockerQuote> filteredQuotes;
        if (currentGroup != null) {
            List<String> groupCodes = setting.getGroupStocks(currentGroup);
            filteredQuotes = quotes.stream()
                    .filter(q -> groupCodes.contains(q.getCode()))
                    .collect(Collectors.toList());
        } else {
            filteredQuotes = quotes;
        }

        filteredQuotes.forEach(quote -> {
            synchronized (tableModel) {
                String displayName = setting.getDisplayName(quote.getCode(), quote.getName());
                int rowIndex = StockerTableModelUtil.existAt(tableModel, quote.getCode());
                if (rowIndex != -1) {
                    // Update existing row - check each column
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
                    // Add new row
                    Double costPrice = setting.getCostPrice(quote.getCode());
                    Integer holdings = setting.getHoldings(quote.getCode());
                    tableModel.addRow(new Object[]{
                            quote.getCode(),
                            displayName,
                            quote.getCurrent(),
                            quote.getOpening(),
                            quote.getClose(),
                            quote.getLow(),
                            quote.getHigh(),
                            quote.getChange(),
                            quote.getPercentage() + "%",
                            formatCostPrice(costPrice),
                            formatHoldings(holdings),
                            formatNetProfit(quote, costPrice, holdings),
                            formatDailyProfit(quote, holdings)
                    });
                    myTableView.clearSortState();
                }
            }
        });
    }

    @Override
    public void syncIndices(List<StockerQuote> indices) {
        synchronized (myTableView) {
            myTableView.syncIndices(indices);
        }
    }

}
