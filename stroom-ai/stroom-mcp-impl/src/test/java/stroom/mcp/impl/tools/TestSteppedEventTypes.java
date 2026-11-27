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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.processing.ErrorTriage;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogued event types the records step_sample stepped wrote, and what that says of the groups of its verdict.
class TestSteppedEventTypes {

    private static final String UNREAD = "Log - No rule reads field %s (map it, or list it in ignored_fields to "
                                         + "leave it out)";

    // Chat 194 (an endpoint log): step_sample said 'app' was in 59 records that no rule reads, the build that none
    // of the records of nine rules hold it. Both were right, of different records, and the agent, told neither which,
    // added 'app' to all 12 rules. The group now says which event types the records holding it are of.
    @Test
    @SuppressWarnings("unchecked")
    void aFieldNoRuleReadsIsSaidWithTheEventTypesOfTheRecordsHoldingIt() {
        final SteppedEventTypes types = SteppedEventTypes.of(catalogue());
        final List<ErrorTriage.Marker> markers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final String record = "73344:" + i;
            final String typeId = switch (i % 3) {
                case 0 -> "Logon-User";
                case 1 -> "Focus-Window";
                default -> "Unfocus-Window";
            };
            types.count(record, "<Events><Event><EventDetail><TypeId>" + typeId + "</TypeId></EventDetail></Event>"
                                + "</Events>");
            if (i % 3 != 0) {
                markers.add(marker(record, "app"));
            }
        }
        // A record of the catch-all, holding another field no rule reads.
        types.count("73344:6", "<Events><Event><EventDetail><TypeId>Unknown</TypeId></EventDetail></Event></Events>");
        markers.add(marker("73344:6", "extra"));
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("severity", "WARNING");
        group.put("element", "translationFilter");
        final Map<String, Object> other = new LinkedHashMap<>(Map.of("severity", "ERROR", "element", "schemaFilter"));
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("groups", new ArrayList<>(List.of(group, other)));

        types.addUnreadIn(summary, markers);

        assertThat((List<String>) group.get(SteppedEventTypes.UNREAD_IN)).containsExactly(
                "'app' is in 4 record(s) stepped, of Focus-Window (2), Unfocus-Window (2): no rule taking them reads "
                + "it. Map it in those rules (or list it in ignored_fields), not in every rule.",
                "'extra' is in 1 record(s) stepped, of uncatalogued records (1): no rule taking them reads it. Map it "
                + "in those rules (or list it in ignored_fields), not in every rule.");
        assertThat(other).doesNotContainKey(SteppedEventTypes.UNREAD_IN);
    }

    private static ErrorTriage.Marker marker(final String record, final String field) {
        return new ErrorTriage.Marker("WARNING", "translationFilter", UNREAD.formatted(field), null, record, null);
    }

    @SuppressWarnings("unchecked")
    private static Catalogue catalogue() {
        return Catalogue.fromJson(JsonUtil.readValue("""
                {"event_types": [
                  {"type_id": "Logon-User", "event_detail": "Authenticate", "action": "Logon",
                   "recognised_by": [{"when": [{"field": "evt", "equals": "logon"}]}], "status": "confirmed"},
                  {"type_id": "Focus-Window", "event_detail": "View",
                   "recognised_by": [{"when": [{"field": "evt", "equals": "focus.in"}]}], "status": "confirmed"},
                  {"type_id": "Unfocus-Window", "event_detail": "View",
                   "recognised_by": [{"when": [{"field": "evt", "equals": "focus.out"}]}], "status": "confirmed"}],
                 "catch_all": {"status": "confirmed"}}""", Map.class));
    }
}
