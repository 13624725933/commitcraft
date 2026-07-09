package com.local.gitcommitai.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;

public final class GitCommitAiConfigurable implements Configurable {
    private JPanel panel;
    private JTextField endpointField;
    private JTextField modelField;
    private JPasswordField apiKeyField;
    private JTextField languageField;
    private JSpinner maxDiffCharsSpinner;
    private JSpinner temperatureSpinner;
    private JTextArea promptTemplateArea;

    @Override
    public @Nls String getDisplayName() {
        return "Git Commit AI";
    }

    @Override
    public @Nullable JComponent createComponent() {
        endpointField = new JTextField();
        modelField = new JTextField();
        apiKeyField = new JPasswordField();
        languageField = new JTextField();
        maxDiffCharsSpinner = new JSpinner(new SpinnerNumberModel(12000, 2000, 80000, 1000));
        temperatureSpinner = new JSpinner(new SpinnerNumberModel(0.2d, 0.0d, 2.0d, 0.1d));
        promptTemplateArea = new JTextArea(12, 64);
        promptTemplateArea.setLineWrap(true);
        promptTemplateArea.setWrapStyleWord(true);

        JButton resetPromptButton = new JButton("Reset Prompt");
        resetPromptButton.addActionListener(event -> promptTemplateArea.setText(GitCommitAiSettings.DEFAULT_PROMPT));

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Endpoint", endpointField);
        addRow(form, 1, "Model", modelField);
        addRow(form, 2, "API Key", apiKeyField);
        addRow(form, 3, "Output Language", languageField);
        addRow(form, 4, "Max Diff Chars", maxDiffCharsSpinner);
        addRow(form, 5, "Temperature", temperatureSpinner);

        JPanel promptHeader = new JPanel(new BorderLayout());
        promptHeader.add(new JLabel("Prompt Template"), BorderLayout.WEST);
        promptHeader.add(resetPromptButton, BorderLayout.EAST);

        JPanel promptPanel = new JPanel(new BorderLayout(0, 4));
        promptPanel.add(promptHeader, BorderLayout.NORTH);
        promptPanel.add(new JBScrollPane(promptTemplateArea), BorderLayout.CENTER);

        panel = new JPanel(new BorderLayout(0, 8));
        panel.add(form, BorderLayout.NORTH);
        panel.add(promptPanel, BorderLayout.CENTER);

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        GitCommitAiSettings settings = GitCommitAiSettings.getInstance();
        GitCommitAiSettings.SettingsState state = settings.getState();
        return !Objects.equals(endpointField.getText(), state.endpoint)
                || !Objects.equals(modelField.getText(), state.model)
                || !Arrays.equals(apiKeyField.getPassword(), settings.getApiKey().toCharArray())
                || !Objects.equals(languageField.getText(), state.language)
                || !Objects.equals(maxDiffCharsSpinner.getValue(), state.maxDiffChars)
                || !Objects.equals(temperatureSpinner.getValue(), state.temperature)
                || !Objects.equals(promptTemplateArea.getText(), state.promptTemplate);
    }

    @Override
    public void apply() throws ConfigurationException {
        GitCommitAiSettings settings = GitCommitAiSettings.getInstance();
        GitCommitAiSettings.SettingsState state = settings.getState();

        String endpoint = endpointField.getText().trim();
        String model = modelField.getText().trim();
        String language = languageField.getText().trim();
        String prompt = promptTemplateArea.getText().trim();

        if (endpoint.isEmpty()) {
            throw new ConfigurationException("Endpoint is required.");
        }
        if (model.isEmpty()) {
            throw new ConfigurationException("Model is required.");
        }
        if (!prompt.contains("{diff}")) {
            throw new ConfigurationException("Prompt template must contain {diff}.");
        }

        state.endpoint = endpoint;
        state.model = model;
        state.language = language.isEmpty() ? "简体中文" : language;
        state.maxDiffChars = ((Number) maxDiffCharsSpinner.getValue()).intValue();
        state.temperature = ((Number) temperatureSpinner.getValue()).doubleValue();
        state.promptTemplate = prompt;
        settings.setApiKey(new String(apiKeyField.getPassword()));
    }

    @Override
    public void reset() {
        GitCommitAiSettings settings = GitCommitAiSettings.getInstance();
        GitCommitAiSettings.SettingsState state = settings.getState();
        endpointField.setText(state.endpoint);
        modelField.setText(state.model);
        apiKeyField.setText(settings.getApiKey());
        languageField.setText(state.language);
        maxDiffCharsSpinner.setValue(state.maxDiffChars);
        temperatureSpinner.setValue(state.temperature);
        promptTemplateArea.setText(state.promptTemplate);
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        endpointField = null;
        modelField = null;
        apiKeyField = null;
        languageField = null;
        maxDiffCharsSpinner = null;
        temperatureSpinner = null;
        promptTemplateArea = null;
    }

    private static void addRow(JPanel form, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 8, 8);
        form.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 8, 0);
        form.add(component, fieldConstraints);
    }
}
