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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What standing instructions (AGENTS docs) say about CEF output: mappings (path -> key, with a label), whether keys
/// outside the dictionary are allowed, the topic, the vendor, and the pipeline template they name.
public final class CefInstructions {

    /// The mappings found, as [CefOverride]s.
    public static final String OVERRIDES = "overrides";
    /// Whether keys outside the dictionary are allowed (a [Boolean]).
    public static final String CUSTOM_KEYS = "custom_keys";
    /// The Kafka topic.
    public static final String TOPIC = "topic";
    /// The Device Vendor.
    public static final String VENDOR = "vendor";
    /// The pipeline template.
    public static final String TEMPLATE = "template";

    private static final int UNICODE = Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern MAPPING_LINE = Pattern.compile(
            "((?:EventSource|EventDetail|EventTime|Meta)/[\\w/@\\[\\]='*.:-]+)\\s*(?:->|=>|→|maps? to|goes? "
            + "to)\\s*`?([A-Za-z][A-Za-z0-9]*)`?(?:\\s*\\(?\\s*(?:label(?:led)?|as)\\s*[:=]?\\s*['\"“]"
            + "([^'\"”]+)['\"”]\\)?)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | UNICODE);
    private static final Pattern NOT_ALLOWED = Pattern.compile(
            "custom (cef )?(keys|fields)[^.\\n]*(not allowed|aren't allowed|are not allowed|forbidden|never)", UNICODE);
    private static final Pattern NONE = Pattern.compile(
            "(no|never|don't use|do not use) custom (cef )?(keys|fields)", UNICODE);
    private static final Pattern ALLOWED = Pattern.compile(
            "custom (cef )?(keys|fields)[^.\\n]*(allowed|may be used|are fine|permitted)", UNICODE);
    private static final Pattern TOPIC_LINE = Pattern.compile("(?:kafka )?topic\\s*[:=]\\s*`?([A-Za-z0-9._-]+)`?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | UNICODE);
    private static final Pattern VENDOR_LINE = Pattern.compile("(?:device )?vendor\\s*[:=]\\s*`?([^`\\n]+?)`?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE | UNICODE);
    private static final Pattern TEMPLATE_LINE = Pattern.compile(
            "(?:pipeline )?template\\s*[:=]\\s*`?([^`\\n]+?)`?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE | UNICODE);
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    private CefInstructions() {
        // Static only.
    }

    /// What standing instructions say about CEF output.
    ///
    /// @param texts The instructions' texts (nulls allowed).
    /// @return [#OVERRIDES] (a list of [CefOverride], always), then what was found of [#CUSTOM_KEYS], [#TOPIC],
    /// [#VENDOR] and [#TEMPLATE], in the order first found (a later text's word replaces an earlier one's).
    public static Map<String, Object> fromInstructions(final List<String> texts) {
        final List<CefOverride> overrides = new ArrayList<>();
        final Map<String, Object> found = new LinkedHashMap<>();
        for (final String given : texts) {
            final String text = given == null
                    ? ""
                    : given;
            final Matcher mapping = MAPPING_LINE.matcher(text);
            while (mapping.find()) {
                final String key = CefKeys.keyOf(mapping.group(2));
                if (CefKeys.KEYS.containsKey(key) || KEY.matcher(key).matches()) {
                    overrides.add(CefOverride.to(mapping.group(1), key, mapping.group(3)));
                }
            }
            final String lowered = text.toLowerCase(Locale.ROOT);
            if (NOT_ALLOWED.matcher(lowered).find() || NONE.matcher(lowered).find()) {
                found.put(CUSTOM_KEYS, false);
            } else if (ALLOWED.matcher(lowered).find()) {
                found.put(CUSTOM_KEYS, true);
            }
            final Matcher topic = TOPIC_LINE.matcher(text);
            if (topic.find()) {
                found.put(TOPIC, topic.group(1));
            }
            final Matcher vendor = VENDOR_LINE.matcher(text);
            if (vendor.find()) {
                found.put(VENDOR, vendor.group(1).strip());
            }
            final Matcher template = TEMPLATE_LINE.matcher(text);
            if (template.find()) {
                found.put(TEMPLATE, template.group(1).strip());
            }
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(OVERRIDES, overrides);
        out.putAll(found);
        return out;
    }
}
