/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.generation.DumpMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A mapping the user (or a standing instruction) gives: where an Event value goes, or that it is not sent. Ported
/// from the Stroom MCP server's `Override` model in `utils/cef.py`.
///
/// @param path      The Event path (`*` for any one step).
/// @param key       The CEF key it goes to, or null.
/// @param label     A custom slot's label, or null.
/// @param eventType `event_type`: only for this kind of event (its action element); null for wherever the path
///                  occurs.
/// @param drop      True if the value is not sent.
public record CefOverride(String path,
                          String key,
                          String label,
                          String eventType,
                          boolean drop) {

    /// @param path  The Event path.
    /// @param key   The CEF key.
    /// @param label A custom slot's label, or null.
    /// @return Where the value goes, wherever the path occurs.
    public static CefOverride to(final String path, final String key, final String label) {
        return new CefOverride(path, key, label, null, false);
    }

    /// @param path      The Event path.
    /// @param eventType The kind of event, or null for every kind.
    /// @return That the value is not sent.
    public static CefOverride dropped(final String path, final String eventType) {
        return new CefOverride(path, null, null, eventType, true);
    }

    /// @param json An override as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate.
    public static CefOverride fromJson(final Map<String, Object> json) {
        final CefJsonReader reader = new CefJsonReader();
        final Map<String, Object> data = reader.model(json, List.of(), "Override");
        if (data == null) {
            return reader.orThrow("Override", null);
        }
        final CefOverride override = new CefOverride(reader.requiredString(data, List.of(), "path"),
                reader.optionalString(data, List.of(), "key"), reader.optionalString(data, List.of(), "label"),
                reader.optionalString(data, List.of(), "event_type"), reader.bool(data, List.of(), "drop", false));
        return reader.orThrow("Override", override);
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        final Map<String, Object> out = new LinkedHashMap<>();
        CefValue.put(out, mode, "path", path, null);
        CefValue.put(out, mode, "key", key, null);
        CefValue.put(out, mode, "label", label, null);
        CefValue.put(out, mode, "event_type", eventType, null);
        CefValue.put(out, mode, "drop", drop, false);
        return out;
    }
}
