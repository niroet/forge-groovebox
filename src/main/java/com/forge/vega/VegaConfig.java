package com.forge.vega;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and saves the Anthropic API key for VEGA.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Environment variable {@code ANTHROPIC_API_KEY}</li>
 *   <li>Config file {@code ~/.forge/config.json} with JSON {@code {"apiKey": "sk-ant-..."}}</li>
 * </ol>
 */
public class VegaConfig {

    private static final Path CONFIG_DIR  = Path.of(System.getProperty("user.home"), ".forge");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private VegaConfig() {}

    /**
     * Returns the Anthropic API key, or {@code null} if not configured.
     */
    public static String getApiKey() {
        // 1. Environment variable
        String env = System.getenv("ANTHROPIC_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }

        // 2. Config file
        if (Files.isReadable(CONFIG_FILE)) {
            try (Reader r = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                if (obj.has("apiKey")) {
                    String key = obj.get("apiKey").getAsString();
                    if (key != null && !key.isBlank()) {
                        return key;
                    }
                }
            } catch (Exception e) {
                System.err.println("[VEGA] Failed to read config file: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Writes the API key to {@code ~/.forge/config.json}.
     * Creates the directory if it does not exist.
     */
    public static void saveApiKey(String key) {
        try {
            Files.createDirectories(CONFIG_DIR);

            // Load existing config if present so we don't clobber other keys
            JsonObject obj = new JsonObject();
            if (Files.isReadable(CONFIG_FILE)) {
                try (Reader r = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                    obj = JsonParser.parseReader(r).getAsJsonObject();
                } catch (Exception ignored) {
                    obj = new JsonObject();
                }
            }

            obj.addProperty("apiKey", key);

            try (Writer w = Files.newBufferedWriter(
                    CONFIG_FILE,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                new Gson().toJson(obj, w);
            }
        } catch (IOException e) {
            System.err.println("[VEGA] Failed to save config file: " + e.getMessage());
        }
    }

    /** Returns {@code true} if an API key is available and non-blank. */
    public static boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }
}
