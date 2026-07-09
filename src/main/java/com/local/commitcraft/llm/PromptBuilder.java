package com.local.commitcraft.llm;

import com.local.commitcraft.config.CommitCraftSettings;

public final class PromptBuilder {
    public String build(CommitCraftSettings.SettingsState settings, String diff) {
        return settings.promptTemplate
                .replace("{language}", safe(settings.language))
                .replace("{model}", safe(settings.model))
                .replace("{diff}", diff);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
