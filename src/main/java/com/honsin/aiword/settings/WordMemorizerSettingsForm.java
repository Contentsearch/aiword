package com.honsin.aiword.settings;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;

public class WordMemorizerSettingsForm {
    private TextFieldWithBrowseButton wordbookDirectoryTextField;
    private JPanel rootPanel;
    private JButton downloadButton;
    private JLabel downloadStatus;

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public void setRootPanel(JPanel rootPanel) {
        this.rootPanel = rootPanel;
    }

    public TextFieldWithBrowseButton getWordbookDirectoryTextField() {
        return wordbookDirectoryTextField;
    }

    public void setWordbookDirectoryTextField(TextFieldWithBrowseButton wordbookDirectoryTextField) {
        this.wordbookDirectoryTextField = wordbookDirectoryTextField;
    }

    public JButton getDownloadButton() {
        return downloadButton;
    }

    public void setDownloadButton(JButton downloadButton) {
        this.downloadButton = downloadButton;
    }

    public JLabel getDownloadStatus() {
        return downloadStatus;
    }

    public void setDownloadStatus(JLabel downloadStatus) {
        this.downloadStatus = downloadStatus;
    }
}
