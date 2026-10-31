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

package stroom.ai.impl.tools;

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Keeps credentials out of what the tools give the model: the fields of a document that hold secrets (e.g. an
/// Elastic cluster's API key secret and CA certificate) are given as `<redacted>`, wherever they are in the
/// document.
public final class SecretRedactor {

    /// The fields never given to the model.
    public static final Set<String> SECRET_FIELDS = Set.of("apiKeySecret", "password", "caCertificate");
    /// What a secret is given as.
    public static final String REDACTED = "<redacted>";

    private SecretRedactor() {
        // Static only.
    }

    /// @param value A document as JSON-like values: maps, lists and scalars.
    /// @return It with every secret field that has a value given as [#REDACTED]. The maps and lists are copies;
    /// the value given isn't changed.
    public static Object redact(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, SECRET_FIELDS.contains(String.valueOf(key)) && hasValue(item)
                    ? REDACTED
                    : redact(item)));
            return copy;
        }
        if (value instanceof final List<?> list) {
            final List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(redact(item)));
            return copy;
        }
        return value;
    }

    /// @param text A document's part, e.g. its JSON.
    /// @return The part with its secrets redacted, if it is JSON holding any; otherwise the text as it was.
    public static String redactJson(final String text) {
        final String stripped = NullSafe.string(text).strip();
        if (!(stripped.startsWith("{") || stripped.startsWith("["))
            || SECRET_FIELDS.stream().noneMatch(stripped::contains)) {
            return text;
        }
        final Object parsed;
        try {
            parsed = JsonUtil.readValue(stripped, Object.class);
        } catch (final RuntimeException e) {
            return text;
        }
        final Object redacted = redact(parsed);
        return redacted.equals(parsed)
                ? text
                : JsonUtil.writeValueAsString(redacted, true);
    }

    // An empty value (null, false, zero, or an empty text, list or map) is no secret to hide.
    private static boolean hasValue(final Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof final String string) {
            return !string.isEmpty();
        }
        if (value instanceof final Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof final List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof final Number number) {
            return number.doubleValue() != 0;
        }
        return true;
    }
}
