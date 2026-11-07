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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimeZones {

    @Test
    void aTimeReadFromSeveralFieldsNamesThemAll() {
        // Seen in the event-time e2e test: a record's own time with its container's as the fallback (any_of) failed
        // the draft with a NullPointerException where the zone question named what it reads.
        assertThat(TimeZones.readFrom(Map.of("path", "EventTime/TimeCreated", "any_of", List.of("rt", "time"))))
                .isEqualTo("rt' or 'time");
        assertThat(TimeZones.readFrom(Map.of("field", "ts"))).isEqualTo("ts");
        assertThat(TimeZones.readFrom(Map.of("xpath", "concat(date, ' ', time)"))).isEqualTo("concat(date, ' ', time)");
    }

    @Test
    void theOffsetGuessReadsEachFieldOfAnyOf() {
        // The latest time an hour ahead of when Stroom received it.
        final List<Map<String, Object>> entries = List.of(Map.of("any_of", List.of("rt", "time"), "time_format",
                "[MMM dd yyyy HH:mm:ss][yyyy-MM-dd HH:mm:ss]"));
        assertThat(TimeZones.guess(entries, Map.of("rt", List.of("Nov 06 2026 10:00:00")),
                List.of("2026-11-06T09:00:00Z"))).hasValueSatisfying(guess -> assertThat(guess.offset())
                .isEqualTo("+01:00"));
    }
}
