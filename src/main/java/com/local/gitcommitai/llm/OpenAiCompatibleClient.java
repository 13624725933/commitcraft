package com.local.gitcommitai.llm;

import com.local.gitcommitai.config.GitCommitAiSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OpenAiCompatibleClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public String generate(String apiKey, GitCommitAiSettings.SettingsState settings, String prompt)
            throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", settings.model);
        requestBody.addProperty("temperature", settings.temperature);

        JsonArray messages = new JsonArray();
        messages.add(message("system", "Generate only the final commit message. Do not include explanations."));
        messages.add(message("user", prompt));
        requestBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeEndpoint(settings.endpoint)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LLM request failed with HTTP " + response.statusCode() + ": " + clip(response.body()));
        }

        return cleanup(parseContent(response.body()));
    }

    private JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private String normalizeEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private String parseContent(String body) throws IOException {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("LLM response does not contain choices.");
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        if (message != null && message.has("content")) {
            return message.get("content").getAsString();
        }
        if (choice.has("text")) {
            return choice.get("text").getAsString();
        }
        throw new IOException("LLM response does not contain message content.");
    }

    private String cleanup(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z0-9_-]*\\R?", "");
            trimmed = trimmed.replaceFirst("\\R?```$", "");
        }
        return trimmed.trim();
    }

    private String clip(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 800 ? body : body.substring(0, 800) + "...";
    }
}
