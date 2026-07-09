package com.local.gitcommitai.llm;

import com.local.gitcommitai.config.GitCommitAiSettings;

public final class PromptBuilder {
    public String build(GitCommitAiSettings.SettingsState settings, String diff) {
        return settings.promptTemplate
                .replace("{language}", safe(settings.language))
                .replace("{model}", safe(settings.model))
                .replace("{diff}", diff);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
