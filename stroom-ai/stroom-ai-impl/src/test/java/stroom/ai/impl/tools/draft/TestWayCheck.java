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

import stroom.ai.impl.tools.generation.Condition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A draft's records recognised by a way's conditions, each record's extracted values worked out once for every way
/// that tests it, by the texts they are read from.
class TestWayCheck extends DraftFixtures {

    private static final Map<String, Object> DRAFT_MAPPING = json("""
            {"input": "json", "json_layout": "lines",
             "extract": [{"field": "msg", "regex": "user=(\\\\S+)", "names": ["user"]},
                         {"field": "msg", "regex": "act=(\\\\S+)", "names": ["act"]},
                         {"field": "act", "regex": "^(\\\\w)", "names": ["act_first"]}]}""");

    @Test
    void eachWayIsToldAsTheEvaluatorTellsItWhicheverWaysCameFirst() {
        final WayCheck check = WayCheck.of(DRAFT_MAPPING);
        final List<Condition> login = conditions("[{\"field\": \"act\", \"equals\": \"login\"}]");
        final List<Condition> alice = conditions("[{\"field\": \"user\", \"equals\": \"alice\"},"
                                                 + " {\"field\": \"act_first\", \"equals\": \"l\"}]");
        final List<Map<String, Object>> records = List.of(
                record("user=alice act=login", "a"),
                record("user=alice act=login", "b"),
                record("user=bob act=logout", "c"),
                record(null, "d"));

        assertThat(check.recognised(login, records)).containsExactly(records.get(0), records.get(1));
        assertThat(check.recognised(alice, records)).containsExactly(records.get(0), records.get(1));
        assertThat(check.recognised(conditions("[{\"field\": \"act\", \"equals\": \"logout\"}]"), records))
                .containsExactly(records.get(2));
    }

    @Test
    void aRecordWhoseTextChangesIsReadAgain() {
        final WayCheck check = WayCheck.of(DRAFT_MAPPING);
        final List<Condition> login = conditions("[{\"field\": \"act\", \"equals\": \"login\"}]");
        final Map<String, Object> record = record("user=alice act=login", "a");
        assertThat(check.recognises(login, record)).isTrue();

        record.put("msg", "user=alice act=logout");

        assertThat(check.recognises(login, record)).isFalse();
    }

    // A condition that can't be told here (a dictionary) places a way as if it held, but counts as unknown.
    @Test
    void aConditionNotToldHereIsTakenAsHoldingToPlaceAWayButIsUnknownToCountIt() {
        final WayCheck check = WayCheck.of(DRAFT_MAPPING);
        final List<Condition> admins = conditions("[{\"field\": \"act\", \"equals\": \"login\"},"
                                                  + " {\"field\": \"user\", \"in_dictionary\": \"Admins\"}]");
        final Map<String, Object> login = record("user=alice act=login", "a");
        final Map<String, Object> logout = record("user=alice act=logout", "b");

        assertThat(check.recognises(admins, login)).isTrue();
        assertThat(check.verdict(admins, login)).isEqualTo(Verdict.UNKNOWN);
        assertThat(check.verdict(admins, logout)).as("one condition fails: it doesn't hold, whatever the other")
                .isEqualTo(Verdict.DOESNT);
        assertThat(check.verdict(conditions("[{\"field\": \"act\", \"equals\": \"login\"}]"), login))
                .isEqualTo(Verdict.HOLDS);
    }

    private static Map<String, Object> record(final String msg, final String id) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        if (msg != null) {
            record.put("msg", msg);
        }
        return record;
    }

    private static List<Condition> conditions(final String when) {
        return WayCheck.conditions(json(when));
    }
}
