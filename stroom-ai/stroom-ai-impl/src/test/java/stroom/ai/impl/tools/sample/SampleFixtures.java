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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.ToolText;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The sample test fixtures: the samples of the formats sources send and the samples of the evaluation cases,
/// copied as JSON.
final class SampleFixtures {

    private static final Map<String, Object> FORMAT_SAMPLES = read("format_samples.json");
    private static final Map<String, Object> EVAL_SAMPLES = read("eval_samples.json");
    // What the profile and survey must make of each sample, recorded.
    private static final Map<String, Object> PYTHON_SURVEY = read("stored_survey.json");

    private SampleFixtures() {
        // Static methods only.
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(final String name) {
        try (final InputStream in = SampleFixtures.class.getResourceAsStream(name)) {
            return (Map<String, Object>) ToolJson.loads(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param name A format sample's name, e.g. `csv_quoted`.
    /// @return Its text.
    static String format(final String name) {
        return (String) FORMAT_SAMPLES.get(name);
    }

    /// @return Every format sample's name.
    static List<String> formatNames() {
        return List.copyOf(FORMAT_SAMPLES.keySet());
    }

    /// @param name An evaluation case's file name without `.yaml`, e.g. `12_xml_fragments_events`.
    /// @return Its samples.
    @SuppressWarnings("unchecked")
    static List<String> eval(final String name) {
        return (List<String>) EVAL_SAMPLES.get(name);
    }

    /// @return Every evaluation case's name that has samples.
    static List<String> evalNames() {
        return List.copyOf(EVAL_SAMPLES.keySet());
    }

    /// @return What the profile and survey must make of each sample, recorded (its name as [#corpusText(String)]
    /// takes it): the
    /// profile's `profile_format` and `profile_records`, the split's `split_format` and `split_records`, and the
    /// survey's `shapes` (signature to count).
    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> pythonSurvey() {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) PYTHON_SURVEY;
    }

    /// @param name A sample's name in [#pythonSurvey()]: `format:<name>`, `eval:<case>:<n>` or `extra:<name>`,
    ///             followed by `:head` (its first two thirds), `:third:head` or `:most:head` (nine tenths) for
    ///             the head of it a survey of a big stream reads.
    /// @return Its text.
    static String corpusText(final String name) {
        final String[] parts = name.split(":");
        final String base = switch (parts[0]) {
            case "format" -> format(parts[1]);
            case "eval" -> eval(parts[1]).get(Integer.parseInt(parts[2]));
            default -> PYTHON_SURVEY.get(parts[0] + ":" + parts[1]) instanceof final Map<?, ?> entry
                    ? (String) entry.get("text")
                    : null;
        };
        final int length = ToolText.len(base);
        if (name.endsWith(":third:head")) {
            return ToolText.head(base, length / 3);
        }
        if (name.endsWith(":most:head")) {
            return ToolText.head(base, length * 9 / 10);
        }
        if (name.endsWith(":head")) {
            return ToolText.head(base, length * 2 / 3);
        }
        return base;
    }

    /// @param profile A sample's profile.
    /// @return Its fields, by name.
    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> fieldsByName(final Map<String, Object> profile) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        ((List<Map<String, Object>>) profile.get("fields")).forEach(f -> out.put((String) f.get("field"), f));
        return out;
    }
}
