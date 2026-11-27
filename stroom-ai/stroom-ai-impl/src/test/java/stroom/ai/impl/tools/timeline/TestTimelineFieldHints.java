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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.ai.impl.tools.timeline.TimelineFields.Origin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// A flat index's likely fields, by each thing's own words, never used unasked.
class TestTimelineFieldHints {

    private static final List<String> FLAT = List.of("EventTime", "UserName", "Host", "EventCode", "Object",
            "Text", "Process", "process.command_line", "StreamId", "EventId", "SessionId");
    private static final Set<String> SKIP = Set.of("EventTime", "StreamId", "EventId");

    @Test
    void eachThingNotMappedIsMatchedByItsOwnWordsWithAtMostOneMore() {
        final Map<String, List<String>> hints = TimelineFieldHints.unmapped(Map.of(), FLAT, SKIP);

        assertThat(hints).containsExactly(
                Map.entry(InstructionRules.USER_FIELD, List.of("UserName")),
                Map.entry(InstructionRules.EVENT_TYPE_FIELD, List.of("EventCode")),
                Map.entry(InstructionRules.HOST_FIELD, List.of("Host")),
                Map.entry(InstructionRules.OBJECT_FIELD, List.of("Object")),
                // The command line has two words more than 'process': not the process.
                Map.entry(InstructionRules.APPLICATION_FIELD, List.of("Process")),
                Map.entry(InstructionRules.TEXT_FIELD, List.of("Text")),
                Map.entry(InstructionRules.SESSION_FIELD, List.of("SessionId")));
    }

    @Test
    void aThingMappedOrAFieldTakenIsNotNamedAndTheNoteSaysWhatToGive() {
        final Map<String, List<String>> hints = TimelineFieldHints.unmapped(Map.of(InstructionRules.USER_FIELD,
                new Found("UserName", Origin.CALL, null)), List.of("EventTime", "UserName", "Host", "EventCode"),
                SKIP);

        assertThat(hints).containsOnlyKeys(InstructionRules.EVENT_TYPE_FIELD, InstructionRules.HOST_FIELD);
        assertThat(TimelineFieldHints.without(hints, List.of("EventCode"))).containsOnlyKeys(
                InstructionRules.HOST_FIELD);
        assertThat(TimelineFieldHints.said(hints)).isEqualTo("not mapped: event type (maybe EventCode), host "
                                                             + "(maybe Host); give them in the call's sources "
                                                             + "({index, eventTypeField, host}) or an AGENTS doc's "
                                                             + "## Fields lines");
        assertThat(TimelineFieldHints.said(Map.of())).isNull();
        assertThat(TimelineFieldHints.unmapped(Map.of(), List.of(), SKIP)).isEmpty();
    }
}
