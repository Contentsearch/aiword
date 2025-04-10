package com.honsin.aiword.toolwindow;


import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class WordMemorizerToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create instance of our main UI panel
        WordMemorizerToolWindowPanel toolWindowPanel = new WordMemorizerToolWindowPanel(project);

        JPanel mainPanel = toolWindowPanel.getMainPanel(); // <--- 获取 Panel

        // --- 添加调试代码 ---
        if (mainPanel == null) {
            System.err.println("ERROR: toolWindowPanel.getMainPanel() returned NULL!");
            // 或者使用 LOG.error(...)
        } else {
            System.out.println("DEBUG: mainPanel instance: " + mainPanel);
            System.out.println("DEBUG: mainPanel component count: " + mainPanel.getComponentCount());
            // 临时给它一个背景色，看看它是否真的显示了但内容透明或尺寸不对
            mainPanel.setBackground(java.awt.Color.YELLOW);
            mainPanel.setOpaque(true);
            // 检查首选尺寸是否合理
            System.out.println("DEBUG: mainPanel preferred size: " + mainPanel.getPreferredSize());
            if (mainPanel.getPreferredSize() != null && (mainPanel.getPreferredSize().width <= 0 || mainPanel.getPreferredSize().height <= 0)) {
                System.err.println("WARNING: mainPanel preferred size might be zero or negative!");
                // 强制给一个大小试试
                mainPanel.setPreferredSize(new java.awt.Dimension(300, 200));
            }
        }
        // --- 结束调试代码 ---

        // Get the content factory
        ContentFactory contentFactory = ContentFactory.getInstance();
        // Create content using the panel's root component
        Content content = contentFactory.createContent(toolWindowPanel.getMainPanel(), "", false); // No title, not closeable

        // Add the content to the tool window
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        // Tool window is always available
        return true;
    }

    @Override
    public void init(@NotNull ToolWindow window) {
        // Optional initialization when tool window first shows
        window.setStripeTitle("AiWord Memorizer"); // Title shown on the tool window stripe button
    }
}