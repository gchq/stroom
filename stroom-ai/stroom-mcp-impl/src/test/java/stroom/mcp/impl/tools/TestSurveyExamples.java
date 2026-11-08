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

/// The records the survey found further into a sample's streams than the sample is read to.
class TestSurveyExamples {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n"
                                      + "2026-10-01T10:01:00Z,bob,logout\n2026-10-01T10:02:00Z,ca";

    @Test
    void recordsFurtherInAreAddedAfterTheHeaderLine() {
        final SurveyExamples.Added added = SurveyExamples.added(Map.of("stream 7", CSV),
                List.of(new SampleStreamTexts.Cut("stream 7", 7, 0)), List.of(state(
                        example("2026-10-01T10:00:00Z,alice,login", 7, 0),
                        example("2026-10-01T23:59:00Z,erin,upload", 7, 0),
                        example("2026-10-01T23:59:30Z,frank,csrf", 8, 0),
                        example("2026-10-01T23:59:40Z,gina,purge", 7, 1))));

        // Only the record the sample's text doesn't hold, from this stream's part.
        assertThat(added.texts()).containsExactly("time,user,action\n2026-10-01T23:59:00Z,erin,upload\n");
        assertThat(added.records()).isEqualTo(1);
        assertThat(added.streams()).containsExactly(7L);
    }

    @Test
    void nothingIsAddedForPartsReadWholeOrRecordsThatArentALineEach() {
        final List<Map<String, Object>> states = List.of(state(example("{\"a\": 2}", 7, 0)));

        assertThat(SurveyExamples.added(Map.of("stream 7", "[{\"a\": 1}, {\"a\": 3"),
                List.of(new SampleStreamTexts.Cut("stream 7", 7, 0)), states).texts()).isEmpty();
        assertThat(SurveyExamples.added(Map.of("stream 7", "{\"a\": 1}\n"), List.of(), states).texts()).isEmpty();
        assertThat(SurveyExamples.added(Map.of("stream 7", "{\"a\": 1}\n{\"a\": 3"),
                List.of(new SampleStreamTexts.Cut("stream 7", 7, 0)), states).texts())
                .containsExactly("{\"a\": 2}\n");
    }

    private static Map<String, Object> state(final Map<String, Object>... examples) {
        return Map.of("shapes", Map.of("shape", Map.of("count", examples.length, "examples", List.of(examples))));
    }

    private static Map<String, Object> example(final String text, final long stream, final int part) {
        return Map.of("text", text, "location", Map.of("stream", stream, "part", part, "record", 900));
    }
}
