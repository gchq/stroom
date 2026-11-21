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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.fielddoc.Descriptions;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestBuildDocumentation {

    @Test
    void fieldsAreAskedToBeDescribedARoundAtATime() {
        // Seen in testing: 75 fields asked for at once, and the agent tried again and again to send every
        // description, with the markdown, in one call, which its context couldn't hold.
        final Descriptions described = Descriptions.asking(true);
        for (int i = 0; i < 60; i++) {
            described.of("field" + i, "EventDetail/Data[@Name='field" + i + "']/@Value", List.of("a", "b"), "");
        }
        final Map<String, Object> first = BuildDocumentation.needsDescriptions(described, List.of());

        assertThat((List<?>) first.get("fields")).hasSize(BuildDocumentation.DESCRIPTIONS_ROUND);
        assertThat(String.valueOf(first.get("hint"))).contains("the first 25 of the 60 to describe", "no markdown");

        // A last round of fewer says nothing of more to come.
        final Descriptions few = Descriptions.asking(true);
        few.of("one", "EventSource/User/Id", List.of("alice"), "");
        assertThat(String.valueOf(BuildDocumentation.needsDescriptions(few, List.of()).get("hint")))
                .doesNotContain("the rest follow");
    }

    @Test
    void descriptionsAreAskedNotToGuessCodesOrWhoseAnIdIs() {
        // Seen in chat 182: RecordType "15 for Exchange, 25 for Teams", not borne out by the sample, and the audit
        // record's Id described as the item the action was taken on.
        final Descriptions few = Descriptions.asking(true);
        few.of("RecordType", "EventDetail/Data[@Name='RecordType']/@Value", List.of("15", "25"), "");
        assertThat(String.valueOf(BuildDocumentation.needsDescriptions(few, List.of()).get("hint")))
                .contains("give no code a meaning (15 = Exchange) the values or source notes don't show",
                        "say whose an Id is only where its name or the source notes do");
    }

    @Test
    void errorsOfTheSameKindInSeveralStreamsAreOneRow() {
        // Seen in chat 182: "String value truncated to length 10000" and "did not parse as XML" each listed once
        // per stream, with a different example.
        final List<Map<String, Object>> groups = List.of(
                group("review", "jsonParser", "WARNING", 6, "String value truncated to length 10000, original "
                                                            + "length: 25486"),
                group("benign", "translationFilter", "WARNING", 3, "event.Body.Content did not parse as XML: "
                                                                  + "record 148"),
                group("review", "jsonParser", "WARNING", 2, "String value truncated to length 10000, original "
                                                            + "length: 27985"),
                group("benign", "translationFilter", "WARNING", 1, "event.Body.Content did not parse as XML: "
                                                                  + "record 1815"),
                group("review", "jsonParser", "ERROR", 1, "String value truncated to length 10000, original "
                                                          + "length: 1"));
        final List<Map<String, Object>> merged = BuildDocumentation.acrossStreams(groups);

        assertThat(merged).extracting(g -> g.get("severity") + " " + g.get("count")).containsExactly(
                "WARNING 8", "ERROR 1", "WARNING 4");
        final String section = BuildDocumentation.errorsSection(merged, List.of());
        assertThat(section).contains("| review | `jsonParser` | WARNING | 8 | String value truncated to length 10000, "
                                     + "original length: 25486 |",
                        "| benign | `translationFilter` | WARNING | 4 | event.Body.Content did not parse as XML: "
                        + "record 148 |")
                .doesNotContain("27985", "record 1815");
    }

    private static Map<String, Object> group(final String errorClass,
                                             final String element,
                                             final String severity,
                                             final int count,
                                             final String message) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("class", errorClass);
        group.put("severity", severity);
        group.put("element", element);
        group.put("count", count);
        group.put("examples", List.of(Map.of("message", message)));
        return group;
    }
}
