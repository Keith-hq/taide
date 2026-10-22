package edu.cst3492.taide.action.automation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

final class ModelOutput {
    private static final int MAX_RESPONSE_CHARS = 120_000;

    private ModelOutput() {}

    static String prompt(String name) {
        try (var stream = ModelOutput.class.getResourceAsStream("/prompts/automation/" + name + ".md")) {
            if (stream == null) {
                throw new AutomationException("The automation prompt is missing. Reinstall TAIDE.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new AutomationException("The automation prompt could not be loaded.", error);
        }
    }

    static String text(String text) {
        if (text == null || text.isBlank()) {
            throw new AutomationException("The model returned an empty response. Please retry.");
        }
        if (text.length() > MAX_RESPONSE_CHARS) {
            throw new AutomationException("The model response is too large. Use a smaller selection.");
        }
        String result = text.strip();
        if (result.startsWith("```")) {
            int newline = result.indexOf('\n');
            if (newline < 0 || !result.endsWith("```")) {
                throw new AutomationException("The model returned an incomplete code block.");
            }
            result = result.substring(newline + 1, result.length() - 3).strip();
        }
        return result;
    }

    static JsonObject json(String text) {
        try (var reader = new JsonReader(new StringReader(text(text)))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new AutomationException("The model response must contain one JSON object.");
            }
            return element.getAsJsonObject();
        } catch (IOException | IllegalStateException | com.google.gson.JsonParseException error) {
            throw new AutomationException("The model returned invalid JSON. Please retry.", error);
        }
    }

    static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new AutomationException("The model response is missing a valid '" + key + "' field.");
        }
        return value.getAsString().strip();
    }

    static void checkLength(String text, int maximum, String label) {
        if (text == null || text.isBlank()) {
            throw new AutomationException(label + " is empty.");
        }
        if (text.length() > maximum) {
            throw new AutomationException(label + " is too large. Limit it to " + maximum + " characters.");
        }
    }
}
