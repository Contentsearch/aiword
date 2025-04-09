package com.honsin.aiword.settings;


import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class WordMemorizerSettingsState {
    // --- Keys for PropertiesComponent ---
    private static final String WORDBOOK_DIRECTORY_KEY = "wordmemorizer.wordbook.directory";
    private static final String CURRENT_PAGE_KEY = "wordmemorizer.pagination.currentPage";
    private static final String WORDS_PER_PAGE_KEY = "wordmemorizer.pagination.wordsPerPage";
    private static final String SELECTED_WORDBOOK_KEY = "wordmemorizer.selectedWordbook";

    // --- Default Values ---
    private static final int DEFAULT_CURRENT_PAGE = 1;
    private static final int DEFAULT_WORDS_PER_PAGE = 50;
    private static final String DEFAULT_SELECTED_WORDBOOK = null; // No default selection

    private static WordMemorizerSettingsState instance;
    private final PropertiesComponent propertiesComponent;

    private WordMemorizerSettingsState() {
        // 使用 Application 级别的 PropertiesComponent
        this.propertiesComponent = PropertiesComponent.getInstance();
    }

    public static synchronized WordMemorizerSettingsState getInstance() {
        if (instance == null) {
            instance = new WordMemorizerSettingsState();
        }
        return instance;
    }

    // --- Wordbook Directory ---
    @NotNull
    public String getWordbookDirectory() {
        // 使用 Configurable 中的默认值或空字符串作为最终默认
        String defaultValue = WordMemorizerSettingsConfigurable.getDefaultDirectory(); // 假设 Configurable 有这个静态方法
        // 或者直接用空字符串: String defaultValue = "";
        return propertiesComponent.getValue(WORDBOOK_DIRECTORY_KEY, defaultValue);
    }

    public void setWordbookDirectory(@NotNull String directory) {
        propertiesComponent.setValue(WORDBOOK_DIRECTORY_KEY, directory);
    }

    // --- Current Page ---
    public int getCurrentPage() {
        // propertiesComponent 存储的是字符串，需要转换
        // 使用 getIntValue 更方便，它处理了解析和默认值
        return propertiesComponent.getInt(CURRENT_PAGE_KEY, DEFAULT_CURRENT_PAGE);
    }

    public void setCurrentPage(int page) {
        // 确保页码至少为 1
        propertiesComponent.setValue(CURRENT_PAGE_KEY, Math.max(1, page), DEFAULT_CURRENT_PAGE);
    }

    // --- Words Per Page ---
    public int getWordsPerPage() {
        return propertiesComponent.getInt(WORDS_PER_PAGE_KEY, DEFAULT_WORDS_PER_PAGE);
    }

    public void setWordsPerPage(int count) {
        // 确保每页单词数至少为 1
        propertiesComponent.setValue(WORDS_PER_PAGE_KEY, Math.max(1, count), DEFAULT_WORDS_PER_PAGE);
    }

    // --- Selected Wordbook ---
    public String getSelectedWordbookName() {
        // 直接获取字符串值，默认就是 null
        return propertiesComponent.getValue(SELECTED_WORDBOOK_KEY);
    }

    public void setSelectedWordbookName(String wordbookName) {
        // 如果传入 null 或空字符串，则移除该键或设置为空值
        if (StringUtil.isEmptyOrSpaces(wordbookName)) {
            // propertiesComponent.unsetValue(SELECTED_WORDBOOK_KEY); // 或者设置为空字符串
            propertiesComponent.setValue(SELECTED_WORDBOOK_KEY, "");
        } else {
            propertiesComponent.setValue(SELECTED_WORDBOOK_KEY, wordbookName);
        }
    }
}