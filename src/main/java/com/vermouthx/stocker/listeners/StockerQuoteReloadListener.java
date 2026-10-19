package com.vermouthx.stocker.listeners;

import com.vermouthx.stocker.views.StockerTableView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class StockerQuoteReloadListener implements StockerQuoteReloadNotifier {
    private final StockerTableView myTableView;

    public StockerQuoteReloadListener(StockerTableView myTableView) {
        this.myTableView = myTableView;
    }

    @Override
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            DefaultTableModel tableModel = myTableView.getTableModel();
            synchronized (tableModel) {
                // clear all table rows
                tableModel.setRowCount(0);
                myTableView.clearSortState();
            }
        });
    }
}
