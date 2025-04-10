package com.honsin.aiword.toolwindow;


import com.honsin.aiword.model.WordEntry;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class WordTableModel extends AbstractTableModel {

    // Column indices
    public static final int WORD_COLUMN_INDEX = 0;
    public static final int TRANSLATION_COLUMN_INDEX = 1;
    public static final int PRONOUNCE_COLUMN_INDEX = 2;

    private final String[] columnNames = {"单词 (Word)", "翻译 (Translation)", "发音 (Pronounce)"};
    private List<WordEntry> words = new ArrayList<WordEntry>();
    private boolean translationsHidden = false;

    public void setWords(List<WordEntry> words) {
        this.words = new ArrayList<>(words); // Create a copy
        fireTableDataChanged(); // Notify the table that the data has completely changed
    }

    public List<WordEntry> getWordsOnCurrentPage() {
        return this.words;
    }

    public void setTranslationsHidden(boolean hidden) {
        if (this.translationsHidden != hidden) {
            this.translationsHidden = hidden;
            // Notify the table that the data in the translation column might have changed
            fireTableDataChanged(); // Easiest way to refresh all cells
            // Or more specific: fireTableChanged(new TableModelEvent(this, 0, getRowCount() -1, TRANSLATION_COLUMN_INDEX, TableModelEvent.UPDATE));
        }
    }

    public boolean isTranslationsHidden() {
        return translationsHidden;
    }

    @Nullable
    public WordEntry getWordEntryAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < words.size()) {
            return words.get(rowIndex);
        }
        return null;
    }


    @Override
    public int getRowCount() {
        return words.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case WORD_COLUMN_INDEX:
            case TRANSLATION_COLUMN_INDEX:
                return String.class;
            case PRONOUNCE_COLUMN_INDEX:
                return JButton.class; // Tell JTable it's a button for rendering/editing
            default:
                return Object.class;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // Only the pronounce button column is "editable" (meaning clickable)
        return columnIndex == PRONOUNCE_COLUMN_INDEX;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= words.size()) {
            return null; // Should not happen with valid indices
        }
        WordEntry entry = words.get(rowIndex);

        switch (columnIndex) {
            case WORD_COLUMN_INDEX:
                return entry.getWord();
            case TRANSLATION_COLUMN_INDEX:
                return translationsHidden ? "****" : entry.getTranslation();
            case PRONOUNCE_COLUMN_INDEX:
                return "▶ 发音"; // Text displayed on the button
            default:
                return null;
        }
    }

// We don't need setValueAt unless we allow direct table editing (which we don't for now)
    /*
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        // Implement if you allow direct editing in the table
    }
    */
}