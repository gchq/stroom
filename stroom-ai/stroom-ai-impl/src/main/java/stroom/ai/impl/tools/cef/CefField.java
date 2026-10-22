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

/// One Event value a CEF plan sends: where it is in the Event, the CEF key it goes to, a custom slot's label, and
/// how it is converted. The key is kept by its short name (`deviceCustomString6` is `cs6`), and a time key given as
/// text is sent as milliseconds since 1970, as the Python model's validator has it. Ported from the Stroom MCP
/// server's `CefField` model in `utils/cef.py`.
///
/// @param path      XPath from the Event, e.g. `EventDetail/Authorise/User/Name`; `*` for any one step.
/// @param key       CEF key (short or full name: `cs6` or `deviceCustomString6`).
/// @param label     For a custom slot (`cs1`-`6`, `cn1`-`3`, `cfp1`-`4`, `deviceCustomDate1`-`2`, `flexString1`-`2`,
///                  `flexDate1`): what it holds, sent as its Label key.
/// @param transform `text`; `epoch_ms`: a time as milliseconds since 1970 (rt, end, start and custom dates);
///                  `outcome`: Outcome/Success as success or failure (no Outcome counts as success, as in
///                  event-logging).
public record CefField(String path,
                       String key,
                       String label,
                       String transform) {

    /// The transforms.
    public static final List<String> TRANSFORMS = List.of("text", "epoch_ms", "outcome");
    /// The default transform.
    public static final String TEXT = "text";
    /// A time as milliseconds since 1970.
    public static final String EPOCH_MS = "epoch_ms";
    /// Outcome/Success as success or failure.
    public static final String OUTCOME = "outcome";

    /// The key by its short name; a time key's text as milliseconds.
    public CefField {
        key = CefKeys.keyOf(key);
        transform = transform == null
                ? TEXT
                : transform;
        final CefKeys.KeyInfo info = CefKeys.KEYS.get(key);
        if (info != null && "time".equals(info.type()) && TEXT.equals(transform)) {
            transform = EPOCH_MS;
        }
    }

    /// @param path The Event path.
    /// @param key  The key.
    /// @return The field, sent as text (or milliseconds, for a time key), with no label.
    public static CefField of(final String path, final String key) {
        return new CefField(path, key, null, TEXT);
    }

    /// @param json A field as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate.
    public static CefField fromJson(final Map<String, Object> json) {
        final CefJsonReader reader = new CefJsonReader();
        return reader.orThrow("CefField", read(reader, json, List.of()));
    }

    /// @param reader The reader, noting what is wrong.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return The field, or null if it doesn't validate.
    static CefField read(final CefJsonReader reader, final Object input, final List<Object> loc) {
        final Map<String, Object> data = reader.model(input, loc, "CefField");
        if (data == null) {
            return null;
        }
        final int before = reader.errorCount();
        final String path = reader.requiredString(data, loc, "path");
        final String key = reader.requiredString(data, loc, "key");
        final String label = reader.optionalString(data, loc, "label");
        final String transform = reader.literal(data, loc, "transform", TEXT, TRANSFORMS);
        return reader.errorCount() > before
                ? null
                : new CefField(path, key, label, transform);
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        final Map<String, Object> out = new LinkedHashMap<>();
        CefValue.put(out, mode, "path", path, null);
        CefValue.put(out, mode, "key", key, null);
        CefValue.put(out, mode, "label", label, null);
        CefValue.put(out, mode, "transform", transform, TEXT);
        return out;
    }
}
