package com.local.commitcraft.config;

import com.local.commitcraft.license.LicenseCheck;
import com.local.commitcraft.license.LicenseVerifier;
import com.local.commitcraft.license.MachineCode;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;

public final class CommitCraftConfigurable implements Configurable {
    private static final String[] POPULAR_LANGUAGES = {
            CommitCraftSettings.DEFAULT_LANGUAGE,
            "繁體中文",
            "English",
            "日本語",
            "한국어",
            "Español",
            "Français",
            "Deutsch",
            "Português",
            "Русский",
            "العربية",
            "हिन्दी",
            "Bahasa Indonesia",
            "Türkçe",
            "Tiếng Việt",
            "ไทย",
            "Italiano",
            "Nederlands"
    };

    private JPanel panel;
    private JTextField endpointField;
    private JTextField modelField;
    private JPasswordField apiKeyField;
    private JComboBox<String> languageComboBox;
    private JSpinner maxDiffCharsSpinner;
    private JSpinner temperatureSpinner;
    private JTextArea promptTemplateArea;
    private JTextField machineCodeField;
    private JTextArea activationCodeArea;
    private JLabel licenseStatusLabel;

    @Override
    public @Nls String getDisplayName() {
        return "CommitCraft";
    }

    @Override
    public @Nullable JComponent createComponent() {
        endpointField = new JTextField();
        modelField = new JTextField();
        apiKeyField = new JPasswordField();
        languageComboBox = new JComboBox<>(POPULAR_LANGUAGES);
        maxDiffCharsSpinner = new JSpinner(new SpinnerNumberModel(12000, 2000, 80000, 1000));
        temperatureSpinner = new JSpinner(new SpinnerNumberModel(0.2d, 0.0d, 2.0d, 0.1d));
        promptTemplateArea = new JTextArea(12, 64);
        promptTemplateArea.setLineWrap(true);
        promptTemplateArea.setWrapStyleWord(true);
        machineCodeField = new JTextField(MachineCode.current());
        machineCodeField.setEditable(false);
        activationCodeArea = new JTextArea(3, 64);
        activationCodeArea.setLineWrap(true);
        activationCodeArea.setWrapStyleWord(true);
        activationCodeArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateLicenseStatus();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateLicenseStatus();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateLicenseStatus();
            }
        });
        licenseStatusLabel = new JLabel();

        JButton resetPromptButton = new JButton("Reset Prompt");
        resetPromptButton.addActionListener(event -> promptTemplateArea.setText(CommitCraftSettings.DEFAULT_PROMPT));
        JButton copyMachineCodeButton = new JButton("Copy");
        copyMachineCodeButton.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(machineCodeField.getText()), null));

        JPanel machineCodePanel = new JPanel(new BorderLayout(4, 0));
        machineCodePanel.add(machineCodeField, BorderLayout.CENTER);
        machineCodePanel.add(copyMachineCodeButton, BorderLayout.EAST);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Endpoint", endpointField);
        addRow(form, 1, "Model", modelField);
        addRow(form, 2, "API Key", apiKeyField);
        addRow(form, 3, "Output Language", languageComboBox);
        addRow(form, 4, "Max Diff Chars", maxDiffCharsSpinner);
        addRow(form, 5, "Temperature", temperatureSpinner);
        addRow(form, 6, "Machine Code", machineCodePanel);
        addRow(form, 7, "Activation Code", new JBScrollPane(activationCodeArea));
        addRow(form, 8, "License Status", licenseStatusLabel);

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
        CommitCraftSettings settings = CommitCraftSettings.getInstance();
        CommitCraftSettings.SettingsState state = settings.getState();
        return !Objects.equals(endpointField.getText(), state.endpoint)
                || !Objects.equals(modelField.getText(), state.model)
                || !Arrays.equals(apiKeyField.getPassword(), settings.getApiKey().toCharArray())
                || !Objects.equals(selectedLanguage(), normalizedLanguage(state.language))
                || !Objects.equals(maxDiffCharsSpinner.getValue(), state.maxDiffChars)
                || !Objects.equals(temperatureSpinner.getValue(), state.temperature)
                || !Objects.equals(promptTemplateArea.getText(), state.promptTemplate)
                || !Objects.equals(activationCodeArea.getText().trim(), nullToEmpty(state.activationCode));
    }

    @Override
    public void apply() throws ConfigurationException {
        CommitCraftSettings settings = CommitCraftSettings.getInstance();
        CommitCraftSettings.SettingsState state = settings.getState();

        String endpoint = endpointField.getText().trim();
        String model = modelField.getText().trim();
        String language = selectedLanguage();
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
        state.language = normalizedLanguage(language);
        state.maxDiffChars = ((Number) maxDiffCharsSpinner.getValue()).intValue();
        state.temperature = ((Number) temperatureSpinner.getValue()).doubleValue();
        state.promptTemplate = prompt;
        state.activationCode = activationCodeArea.getText().trim();
        settings.setApiKey(new String(apiKeyField.getPassword()));
    }

    @Override
    public void reset() {
        CommitCraftSettings settings = CommitCraftSettings.getInstance();
        CommitCraftSettings.SettingsState state = settings.getState();
        endpointField.setText(state.endpoint);
        modelField.setText(state.model);
        apiKeyField.setText(settings.getApiKey());
        selectLanguage(state.language);
        maxDiffCharsSpinner.setValue(state.maxDiffChars);
        temperatureSpinner.setValue(state.temperature);
        promptTemplateArea.setText(state.promptTemplate);
        machineCodeField.setText(MachineCode.current());
        activationCodeArea.setText(nullToEmpty(state.activationCode));
        updateLicenseStatus();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        endpointField = null;
        modelField = null;
        apiKeyField = null;
        languageComboBox = null;
        maxDiffCharsSpinner = null;
        temperatureSpinner = null;
        promptTemplateArea = null;
        machineCodeField = null;
        activationCodeArea = null;
        licenseStatusLabel = null;
    }

    private void updateLicenseStatus() {
        if (licenseStatusLabel == null || activationCodeArea == null) {
            return;
        }
        LicenseCheck check = new LicenseVerifier().verify(activationCodeArea.getText(), MachineCode.current());
        licenseStatusLabel.setText(check.message());
    }

    private String selectedLanguage() {
        Object selected = languageComboBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    private void selectLanguage(String language) {
        String value = normalizedLanguage(language);
        if (!containsLanguage(value)) {
            languageComboBox.addItem(value);
        }
        languageComboBox.setSelectedItem(value);
    }

    private static String normalizedLanguage(String language) {
        return language == null || language.isBlank() ? CommitCraftSettings.DEFAULT_LANGUAGE : language.trim();
    }

    private boolean containsLanguage(String language) {
        for (int index = 0; index < languageComboBox.getItemCount(); index++) {
            if (Objects.equals(languageComboBox.getItemAt(index), language)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
