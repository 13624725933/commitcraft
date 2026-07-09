package com.local.commitcraft.ui;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.StringSelection;

public final class GeneratedMessageDialog extends DialogWrapper {
    private final JTextArea messageArea;
    private final String details;

    public GeneratedMessageDialog(@Nullable Project project, String message, String details) {
        super(project);
        this.details = details;
        this.messageArea = new JTextArea(message, 10, 72);
        this.messageArea.setLineWrap(true);
        this.messageArea.setWrapStyleWord(true);
        setTitle("Generated Commit Message");
        setOKButtonText("Copy");
        setCancelButtonText("Close");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        if (details != null && !details.isBlank()) {
            panel.add(new JLabel(details), BorderLayout.NORTH);
        }
        JBScrollPane scrollPane = new JBScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(720, 260));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected void doOKAction() {
        CopyPasteManager.getInstance().setContents(new StringSelection(messageArea.getText().trim()));
        super.doOKAction();
    }
}
