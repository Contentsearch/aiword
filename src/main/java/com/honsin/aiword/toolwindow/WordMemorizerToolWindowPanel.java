package com.honsin.aiword.toolwindow;

import com.honsin.aiword.model.WordEntry;
import com.honsin.aiword.service.WordbookService;
import com.honsin.aiword.service.YoudaoTtsService;
import com.honsin.aiword.settings.WordMemorizerSettingsState;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.internal.statistic.eventLog.util.StringUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WordMemorizerToolWindowPanel {
    private static final String KEY_CURRENT_PAGE = "wordMemorizer.currentPage";
    private static final String KEY_WORDS_PER_PAGE = "wordMemorizer.wordsPerPage";
    private JPanel mainPanel;
    private JSpinner wordCountSpinner;
    private JButton startButton;
    private JCheckBox hideTranslationCheckBox;
    private JBTable wordTable; // Use JBTable
    private JBScrollPane scrollPane; // Use JBScrollPane
    private JButton loadButton;
    private JButton previousPageButton;
    private JButton nextPageButton;
    private JLabel pageInfoLabel;
    private JComboBox selectDictComboBox;

    private final Project project;
    private final WordbookService wordbookService;
    private final YoudaoTtsService ttsService; // Add TTS service instance
    private WordTableModel tableModel;
    private List<WordEntry> allLoadedWords = new ArrayList<>(); // Store all words in order
    private int currentPage = 1;
    private int wordsPerPage = 20; // Default, will be loaded/set
    private int totalPages = 0;
    private String selectedWordbookName = null;
    private static final String KEY_SELECTED_WORDBOOK = "wordMemorizer.selectedWordbook";

    public WordMemorizerToolWindowPanel(Project project) {

        this.project = project;
        this.wordbookService = WordbookService.getInstance();
        this.ttsService = new YoudaoTtsService(); // Create an instance

        setupTable();
        setupSpinner();
        setupActionListeners();
        refreshWordbookList();

        // Initial load or refresh
        // Initial load or refresh based on saved state? Or trigger load explicitly?
        // Let's load books on startup and update view
        if (selectedWordbookName != null && selectedWordbookName.equals(selectDictComboBox.getSelectedItem())) {
            System.out.println("Attempting to auto-load last selected wordbook: " + selectedWordbookName);
            loadWordsFromSelectedFile(); // 尝试自动加载
        } else if (selectDictComboBox.getItemCount() > 0) {
            // 如果没有自动加载，提示用户操作
            showInfoNotification("请选择一个单词本并点击 '加载选中词库'。");
        }
        System.out.println("WordMemorizerToolWindowPanel constructor finished."); // 添加日志
    }


    private void updatePaginationState() {
        if (wordsPerPage <= 0) {
            totalPages = 0; // Avoid division by zero
        } else {
            totalPages = (int) Math.ceil((double) allLoadedWords.size() / wordsPerPage);
        }
        // Ensure currentPage is valid after recalculation (e.g., if wordsPerPage increased drastically)
        if (currentPage > totalPages && totalPages > 0) {
            currentPage = totalPages;
        } else if (totalPages == 0) {
            currentPage = 1; // Reset if no pages
        }
        System.out.println("Pagination state updated: totalPages=" + totalPages + ", currentPage=" + currentPage);


    }


    private void saveState() {
        WordMemorizerSettingsState settings = WordMemorizerSettingsState.getInstance();
        settings.setCurrentPage(this.currentPage);
        settings.setWordsPerPage(this.wordsPerPage);

        Object selectedItem = selectDictComboBox.getSelectedItem();
        String nameToSave = selectedItem instanceof String ? (String) selectedItem : null;
        settings.setSelectedWordbookName(nameToSave); // Save the selected filename or null

        System.out.println("Saved state: currentPage=" + currentPage + ", wordsPerPage=" + wordsPerPage + ", selectedWordbook=" + (selectedItem instanceof String ? selectedItem : "null"));
    }


    // Updates the table and pagination controls for the current page
    private void updateViewForCurrentPage() {
        if (allLoadedWords.isEmpty()) {
            tableModel.setWords(List.of()); // Show empty table
            pageInfoLabel.setText("0 / 0");
            previousPageButton.setEnabled(false);
            nextPageButton.setEnabled(false);
            System.out.println("Updating view: No words loaded.");
            return;
        }

        if (totalPages == 0) updatePaginationState(); // Recalculate if needed

        // Clamp currentPage just in case
        currentPage = Math.max(1, Math.min(currentPage, totalPages));

        int startIndex = (currentPage - 1) * wordsPerPage;
        int endIndex = Math.min(startIndex + wordsPerPage, allLoadedWords.size());

        if (startIndex < 0 || startIndex >= allLoadedWords.size()) {
            // Handle invalid startIndex, maybe show empty page or log error
            tableModel.setWords(List.of());
            System.err.println("Error: Invalid start index " + startIndex + " for page " + currentPage);
        } else {
            List<WordEntry> wordsForPage = allLoadedWords.subList(startIndex, endIndex);
            tableModel.setWords(wordsForPage); // Update table model with the sublist
            System.out.println("Displaying page " + currentPage + "/" + totalPages + " (Words " + (startIndex + 1) + " to " + endIndex + ")");
        }


        // Update page info label
        pageInfoLabel.setText(currentPage + " / " + totalPages);

        // Enable/disable buttons
        previousPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(currentPage < totalPages);
    }


    public JPanel getMainPanel() {
        return mainPanel;
    }

    // Called by Swing Designer Form Creator - DO NOT RENAME
    private void createUIComponents() {
        // Use JBTable and JBScrollPane for better IDEA integration look and feel
        wordTable = new JBTable();
        scrollPane = new JBScrollPane(wordTable);
    }


    private void setupTable() {
        tableModel = new WordTableModel();
        wordTable.setModel(tableModel);
        wordTable.setRowHeight(wordTable.getRowHeight() + 4); // Slightly taller rows
        wordTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Select one row at a time

        // --- Setup Button Column for Pronunciation ---
        TableColumn buttonColumn = wordTable.getColumnModel().getColumn(WordTableModel.PRONOUNCE_COLUMN_INDEX);
        ButtonColumn buttonRendererEditor = new ButtonColumn(wordTable, WordTableModel.PRONOUNCE_COLUMN_INDEX);
        buttonColumn.setCellRenderer(buttonRendererEditor);
        buttonColumn.setCellEditor(buttonRendererEditor);

        // --- Setup Column Widths (Optional but improves look) ---
        TableColumn wordColumn = wordTable.getColumnModel().getColumn(WordTableModel.WORD_COLUMN_INDEX);
        wordColumn.setPreferredWidth(150);
        wordColumn.setMinWidth(100);

        TableColumn translationColumn = wordTable.getColumnModel().getColumn(WordTableModel.TRANSLATION_COLUMN_INDEX);
        translationColumn.setPreferredWidth(250);
        translationColumn.setMinWidth(150);

        buttonColumn.setPreferredWidth(80);
        buttonColumn.setMinWidth(60);
        buttonColumn.setMaxWidth(100);

        // Make table header clickable (for future sorting maybe)
        wordTable.getTableHeader().setReorderingAllowed(false);
    }

    private void setupSpinner() {
        // Default 50 words, min 1, max 500, step 1
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(this.wordsPerPage, 1, 500, 1);
        wordCountSpinner.setModel(spinnerModel);

        // Add listener to react to changes in wordsPerPage
        wordCountSpinner.addChangeListener(e -> {
            int newWordsPerPage = (int) wordCountSpinner.getValue();
            if (newWordsPerPage != this.wordsPerPage) {
                this.wordsPerPage = newWordsPerPage;
                System.out.println("Words per page changed to: " + this.wordsPerPage);
                // When words per page changes, recalculate total pages and reset to page 1
                this.currentPage = 1; // Reset to page 1 for simplicity
                updatePaginationState();
                updateViewForCurrentPage();
                saveState(); // Save the new wordsPerPage and reset page
            }
        });
    }

    private void setupActionListeners() {
        // Load button action
        loadButton.addActionListener(e -> loadWordsFromSelectedFile());

        // Start/Refresh button action
        startButton.addActionListener(e -> {
            System.out.println("'Refresh View / Go to Page 1' button clicked.");
            this.currentPage = 1;
            updateViewForCurrentPage();
            saveState(); // Save page reset
            tableModel.setWords(wordbookService.getRandomWords(wordsPerPage, allLoadedWords));
        });

        // Hide/Show translation checkbox action
        hideTranslationCheckBox.addActionListener(e -> {
            boolean hide = hideTranslationCheckBox.isSelected();
            tableModel.setTranslationsHidden(hide);
        });

        // Add action listener to the ButtonColumn editor
        ((ButtonColumn) wordTable.getColumnModel().getColumn(WordTableModel.PRONOUNCE_COLUMN_INDEX).getCellEditor())
                .addActionListener(e -> {
                    int selectedRow = wordTable.convertRowIndexToModel(wordTable.getEditingRow()); // Important if table is sorted/filtered
                    if (selectedRow >= 0) {
                        WordEntry entry = tableModel.getWordEntryAt(selectedRow);
                        if (entry != null) {
                            // Call TTS Service
                            ttsService.pronounceWordAsync(project, entry.getWord());
                        }
                    }
                });

        // --- NEW: Add MouseListener for Double-Click on Rows ---
        wordTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Check for double-click (specifically left mouse button)
                if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1) {
                    // Get the row index at the click point
                    Point point = e.getPoint();
                    int viewRow = wordTable.rowAtPoint(point);

                    // Ensure the click was on a valid row (not header or empty space)
                    if (viewRow >= 0) {
                        // Convert view index to model index (important for sorting/filtering)
                        int modelRow = wordTable.convertRowIndexToModel(viewRow);
                        // Get the data for the clicked row from the TableModel
                        WordEntry entry = tableModel.getWordEntryAt(modelRow);

                        if (entry != null) {
                            // --- Show Hint ---
                            showWordDetailHint(entry, e);
                        }
                    }
                }
            }
        });
        previousPageButton.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                updateViewForCurrentPage();
                saveState();
            }
        });

        nextPageButton.addActionListener(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                updateViewForCurrentPage();
                saveState();
            }
        });
        selectDictComboBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 在鼠标按下时触发刷新，这通常发生在下拉菜单显示之前
                System.out.println("ComboBox mousePressed detected, refreshing wordbook list...");
                refreshWordbookList();
                // 注意：这里不需要手动调用 super.mousePressed(e) 或处理事件消耗，
                // 允许默认行为继续（即弹出下拉列表）。
            }
        });


    }

    private void loadWordsFromSelectedFile() {
        allLoadedWords.clear();
        Object selectedItem = selectDictComboBox.getSelectedItem();
        if (!(selectedItem instanceof String)) {
            showErrorNotification("未选择有效的单词本文件。（如果未配置请先到设置中设置单词本目录）");
            clearWordDisplay(); // Clear table and state
            return;
        }

        String selectedName = (String) selectedItem;
        String directoryPath = WordMemorizerSettingsState.getInstance().getWordbookDirectory();
        if (StringUtil.isEmptyOrSpaces(directoryPath)) {
            showErrorNotification("未配置单词本目录。(Wordbook directory not configured.)");
            clearWordDisplay();
            return;
        }

        Path filePath = Paths.get(directoryPath).resolve(selectedName);

        try {
            // Call the service to load words from this specific file
            this.allLoadedWords = wordbookService.loadWordsFromFile(filePath);

            // Reset pagination and update view
            this.currentPage = 1;
            updatePaginationState();
            updateViewForCurrentPage();
            // No need to saveState() here, as selection change already saved it.

            showInfoNotification("已加载单词本: " + selectedName + " (" + allLoadedWords.size() + " words)");

        } catch (IOException e) {
            System.err.println("加载单词本文件失败: " + filePath + " - " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
            showErrorNotification("加载单词本 '" + selectedName + "' 失败: " + e.getMessage());
            clearWordDisplay(); // Clear display on error
        }
    }


    // Helper method to clear the display when loading fails or no file selected
    private void clearWordDisplay() {
        this.allLoadedWords.clear();
        this.currentPage = 1;
        updatePaginationState(); // Recalculates totalPages (will be 0)
        updateViewForCurrentPage(); // Shows empty table
        // Optionally clear selection in combobox? Or leave it as is?
        // selectDictComboBox.setSelectedItem(null); // Be careful, this might trigger listener again
        this.selectedWordbookName = null; // Clear internal state
        saveState(); // Save the cleared state
    }

    // Helper methods for notifications (reuse or create)
    private void showInfoNotification(String message) {
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup("WordMemorizerNotifications")
                .createNotification("单词本操作", message, NotificationType.INFORMATION);
        Notifications.Bus.notify(notification, project);
        // Make it expire? Add Alarm logic if desired.
    }

    private void showErrorNotification(String message) {
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup("WordMemorizerNotifications")
                .createNotification("错误", message, NotificationType.WARNING); // Use WARNING or ERROR
        Notifications.Bus.notify(notification, project);
    }

    private void refreshWordbookList() {
        String directoryPath = WordMemorizerSettingsState.getInstance().getWordbookDirectory();
        if (StringUtil.isEmptyOrSpaces(directoryPath)) {
            // 不要在这里显示错误通知，因为用户可能只是点击下拉框，目录可能还没设置
            // 可以考虑在 loadButton 点击时再检查和提示
            selectDictComboBox.setModel(new DefaultComboBoxModel<>());
            return;
        }

        Path wordbookDir = Paths.get(directoryPath);
        if (!Files.isDirectory(wordbookDir)) {
            selectDictComboBox.setModel(new DefaultComboBoxModel<>());
            return;
        }

        // --- 获取当前选中的项，以便刷新后尝试恢复 ---
        Object previouslySelectedItem = selectDictComboBox.getSelectedItem();
        String previousSelectedName = (previouslySelectedItem instanceof String) ? (String) previouslySelectedItem : null;
        System.out.println("Refreshing list. Previously selected: " + previousSelectedName);


        List<String> jsonFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wordbookDir, "*.json")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    jsonFiles.add(entry.getFileName().toString());
                }
            }
        } catch (IOException e) {
            // 可以在这里显示短暂错误，或只在日志记录
             showErrorNotification("扫描单词本目录失败: " + e.getMessage());
            // 保持列表不变可能比清空更好
             selectDictComboBox.setModel(new DefaultComboBoxModel<>());
            return; // 扫描失败，不更新列表
        }

        Collections.sort(jsonFiles, String.CASE_INSENSITIVE_ORDER);

        // --- 更新模型并尝试恢复选择 ---
        DefaultComboBoxModel<String> newModel = new DefaultComboBoxModel<>(jsonFiles.toArray(new String[0]));
        selectDictComboBox.setModel(newModel); // 设置新模型

        boolean selectionRestored = false;
        if (previousSelectedName != null && jsonFiles.contains(previousSelectedName)) {
            // 如果之前的选项仍然存在于新列表中，重新选中它
            selectDictComboBox.setSelectedItem(previousSelectedName);
            selectionRestored = true;
            System.out.println("Selection restored: " + previousSelectedName);
        } else if (!jsonFiles.isEmpty()) {
            // 如果之前的选项不在了，或者之前就没选，默认选第一个
            selectDictComboBox.setSelectedIndex(0);
            System.out.println("Previous selection not found or null. Selected first item: " + selectDictComboBox.getSelectedItem());
        } else {
            System.out.println("No JSON files found after refresh.");
            // 如果刷新后列表为空，之前加载的内容还保留吗？取决于设计。
            // 也许应该在这里调用 clearWordDisplay() 来清空表格？
            // clearWordDisplay(); // 取消注释则清空表格和状态
        }

        // 可以在这里保存状态吗？如果在构造函数中调用 refreshWordbookList，
        // 可能会在 state 完全加载前就保存，导致问题。
        // 最好只在用户明确操作（如点击加载、翻页）或程序退出时保存。
        // saveState(); // 暂时不在这里保存

        // 如果没有恢复之前的选择，并且列表不为空，那么新的默认选择(第一个)可能需要用户手动加载
        if (!selectionRestored && !jsonFiles.isEmpty()) {
            // 也许提示一下用户需要点击加载按钮？
            showInfoNotification("Wordbook list refreshed. Click 'Load Selected' to load '" + selectDictComboBox.getSelectedItem() + "'.");
        }
    }


    // Helper to escape HTML special characters for safety if needed
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&")
                .replace("<", "<")
                .replace(">", ">")
                .replace("\"", " \"")
                .replace("'", "'");
    }

    private void showWordDetailHint(WordEntry entry, MouseEvent mouseEvent) {
        // Prepare the content for the hint

//        boolean hide = hideTranslationCheckBox.isSelected();
        String translationText = entry.getTranslation();
        // Use HTML for basic formatting within the label
        String hintContent = "<html>[  " + escapeHtml(translationText) + "   ]</html>";

        // Create a JLabel for the hint content - allows HTML rendering
        JComponent label = HintUtil.createInformationLabel(hintContent); // Use standard info style

        // Create the hint itself
//        LightweightHint hint = new LightweightHint(label);

        // Calculate where to show the hint (relative to the click event in the screen)
        Point pointOnScreen = mouseEvent.getLocationOnScreen();
        Point tablePoint = mouseEvent.getPoint(); // Point within table

        // Show the hint using HintManager
        // Flags control hint behavior (e.g., cancel on focus loss, key press)
        int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_MOUSEOVER | HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING;
        HintManagerImpl.getInstanceImpl().showHint(
                label,
                new RelativePoint(mouseEvent.getComponent(), tablePoint), // Position relative to click inside table
                flags,
                0 // Timeout (0 means no automatic timeout)
        );
    }


}

// Helper class for putting a button in a JTable cell
// Many versions exist online, this is a basic one.
class ButtonColumn extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final JButton renderButton;
    private final JButton editorButton;
    private final JTable table;
    private Object editorValue;
    private boolean isEditorActive = false;
    private final int column;

    public ButtonColumn(JTable table, int column) {
        this.table = table;
        this.column = column;
        this.renderButton = new JButton();
        this.editorButton = new JButton();

        // Action listener for the editor button
        editorButton.addActionListener(e -> {
            // Fire editing stopped AFTER the action is performed.
            // This allows the listener external to ButtonColumn (added in setupActionListeners)
            // to get the correct editing row before it's cleared.
            SwingUtilities.invokeLater(() -> fireEditingStopped());
        });

        // Set button properties (optional)
        renderButton.setFocusPainted(false);
        renderButton.setOpaque(true);
        editorButton.setFocusPainted(false);
        editorButton.setOpaque(true);
    }

    // Method to add external action listener
    public void addActionListener(java.awt.event.ActionListener listener) {
        editorButton.addActionListener(listener);
    }


    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        renderButton.setText((value == null) ? "" : value.toString());
        return renderButton;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        editorButton.setText((value == null) ? "" : value.toString());
        this.editorValue = value;
        this.isEditorActive = true;
        return editorButton;
    }

    @Override
    public Object getCellEditorValue() {
        return editorValue;
    }

    @Override
    public boolean stopCellEditing() {
        isEditorActive = false;
        return super.stopCellEditing();
    }

    @Override
    protected void fireEditingStopped() {
        // Important: Only fire if editing was actually active
        if (isEditorActive) {
            super.fireEditingStopped();
        }
        isEditorActive = false; // Ensure state is reset
    }

}
