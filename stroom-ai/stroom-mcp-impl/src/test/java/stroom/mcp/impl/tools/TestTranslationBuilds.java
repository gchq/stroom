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

import stroom.ai.impl.tools.generation.EventRule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The form that asks the user to keep records as Unknown events says which records in plain words. Seen in
/// testing: "rule 'SEGOther': 1651 of 21503 sample records" and "as shown in the chat: e998d10dadc5" left the user
/// unsure which events it meant.
class TestTranslationBuilds {

    @Test
    void theCatchAllRuleIsNamedForTheRecordsItTakesWithWhatTheyHold() {
        final EventRule other = new EventRule("SEGOther", List.of(), List.of(), false, "no action in these lines",
                false, List.of());
        final Map<String, Map<String, Object>> sampled = Map.of("SEGOther", Map.of("records", 1651, "sample",
                "module: dkim, spf, tls ...; level: info; e.g. msg: \"connection closed\""));

        final Map<String, Object> details = TranslationBuilds.unknownFormDetails(List.of(other), sampled, 21503);

        assertThat(details).containsExactly(Map.entry("Records no other rule matches (rule 'SEGOther')",
                "1,651 of 21,503 sample records, holding module: dkim, spf, tls ...; level: info"));
    }

    @Test
    void aRuleWhoseRecordsHoldNothingToShowGivesItsCountAlone() {
        final EventRule other = new EventRule("rest", List.of(), List.of(), false, "unknown", false, List.of());

        final Map<String, Object> details = TranslationBuilds.unknownFormDetails(List.of(other),
                Map.of("rest", Map.of("records", 3, "sample", "")), "?");

        assertThat(details).containsExactly(Map.entry("Records no other rule matches (rule 'rest')",
                "3 of ? sample records"));
    }
}
