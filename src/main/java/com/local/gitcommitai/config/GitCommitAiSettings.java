package com.local.gitcommitai.config;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "GitCommitAiSettings", storages = @Storage("gitCommitAi.xml"))
public final class GitCommitAiSettings implements PersistentStateComponent<GitCommitAiSettings.SettingsState> {
    public static final String DEFAULT_PROMPT = """
            你是一名资深软件工程师。请根据下面的 git diff 生成一条清晰、简洁、可直接使用的提交信息。

            要求：
            - 输出语言：{language}
            - 优先使用 Conventional Commits 格式，例如 feat(scope): summary 或 fix: summary
            - 如果改动较多，可以包含 2-4 条正文要点
            - 不要输出 Markdown 代码块，不要解释生成过程

            git diff:
            {diff}
            """;

    private static final String API_KEY_SERVICE = "Git Commit AI";
    private static final String API_KEY_USER = "openai-compatible-api-key";

    private SettingsState state = new SettingsState();

    public static GitCommitAiSettings getInstance() {
        return ApplicationManager.getApplication().getService(GitCommitAiSettings.class);
    }

    @Override
    public SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(SettingsState state) {
        this.state = state == null ? new SettingsState() : state;
    }

    public String getApiKey() {
        Credentials credentials = PasswordSafe.getInstance().get(apiKeyAttributes());
        return credentials == null ? "" : nullToEmpty(credentials.getPasswordAsString());
    }

    public void setApiKey(String apiKey) {
        String trimmed = nullToEmpty(apiKey).trim();
        Credentials credentials = trimmed.isEmpty() ? null : new Credentials(API_KEY_USER, trimmed);
        PasswordSafe.getInstance().set(apiKeyAttributes(), credentials);
    }

    private CredentialAttributes apiKeyAttributes() {
        return new CredentialAttributes(API_KEY_SERVICE, API_KEY_USER);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static final class SettingsState {
        public String endpoint = "https://api.openai.com/v1/chat/completions";
        public String model = "gpt-4.1-mini";
        public String language = "简体中文";
        public int maxDiffChars = 12000;
        public double temperature = 0.2d;
        public String promptTemplate = DEFAULT_PROMPT;
    }
}
