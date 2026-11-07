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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.sample.TimePatterns;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// What onboarding a domain controller's Security log ([DomainControllerLog]) needs of the draft, in general terms
/// (the work package's E1, E2, E4 and E6): a rule for each of many kinds, the actor among several user fields, and
/// times to seven digits of a second. Nothing here is Windows knowledge in the tools: the log is only the test's
/// source.
class TestDraftDomainController extends DraftFixtures {

    private static final String WEF = DomainControllerLog.wef(600, 1);
    private static final String BEAT = DomainControllerLog.winlogbeat(600, 1);

    // E1.
    @Test
    void aKindsFieldWithManyValuesHasARuleForEach() {
        final Map<String, Map<String, Object>> rules = rules(draft(WEF));
        // Each of the ids in the sample (all of them: each is written at least once).
        final List<String> ids = DomainControllerLog.IDS.stream().map(String::valueOf).toList();
        assertThat(rules.keySet()).containsAll(ids).contains("other");
    }

    // E1, the same records as JSON.
    @Test
    void manyKindsAsJsonHaveARuleForEachToo() {
        final Map<String, Map<String, Object>> rules = rules(draft(BEAT));
        assertThat(rules.keySet()).contains("4624", "5136", "4720", "4662");
    }

    // E2.
    @Test
    void theActorAmongSeveralUserFieldsIsTheEventSourcesUser() {
        final Map<String, Object> draft = draft(WEF);
        final Map<String, Object> user = common(draft).get("EventSource/User/Id");
        assertThat(String.valueOf(user.get("field"))).contains("SubjectUserName");
        assertThat(notes(draft)).anyMatch(note -> note.startsWith("Records hold several user fields")
                                                  && note.contains("TargetUserName") && note.contains("(affected)"));
    }

    // E4.
    @Test
    void recordsTiedByACorrelationIdAreMappedAloneWithANote() {
        final Map<String, Object> draft = draft(WEF);
        assertThat(rules(draft)).containsKey("5136");
        assertThat(notes(draft)).anyMatch(note -> note.contains("OpCorrelationID") && note.contains("ties records"));
        // Not where no id is shared.
        assertThat(notes(DraftMapping.draft(List.of(resource("event-time/t05_json_object_times.log")), "", null,
                null, null, null, null))).noneMatch(note -> note.contains("ties records"));
    }

    // E6.
    @Test
    void timesToSevenDigitsOfASecondAreRead() {
        final Map<String, Object> draft = draft(WEF);
        final Map<String, Object> time = common(draft).get("EventTime/TimeCreated");
        final String format = (String) time.get("time_format");
        final List<String> values = DraftMapping.records(Map.of("wef", WEF), null).records().stream()
                .map(record -> record.get(String.valueOf(time.get("field"))))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        assertThat(values).isNotEmpty().allMatch(value -> value.matches(".*\\.\\d{7}Z"));
        assertThat(TimePatterns.checkTimeFormat(format, values)).isNull();
    }

    private static Map<String, Object> draft(final String text) {
        return DraftMapping.draft(List.of(text), "", null, null, null, null, null);
    }
}
