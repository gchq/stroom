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

/// How survey_feed reads the user's answer to whether to read deeper, what it suggests, and how a part's depth is
/// kept.
class TestSurveyDepth {

    private static final SurveyDepth.Part PART = new SurveyDepth.Part(7, 0, 10_000, List.of(List.of(12_000L, 500L)),
            null, "key=value", 6_000_000, 25_000, false, null);

    @Test
    void theUsersAnswerIsReadAsStopMoreOrToTheEnd() {
        assertThat(SurveyDepth.decide(SurveyDepth.STOP_HERE).stop()).isTrue();
        assertThat(SurveyDepth.decide("no, that's enough").stop()).isTrue();
        assertThat(SurveyDepth.decide("Read to the end (about 15,000 records, 3.8 MB)").toEnd()).isTrue();
        assertThat(SurveyDepth.decide("read the rest").toEnd()).isTrue();
        // A number more, with commas or a k.
        assertThat(SurveyDepth.decide("Read 10,000 more").choiceFor(PART)).isEqualTo("20000");
        assertThat(SurveyDepth.decide("another 5k").choiceFor(PART)).isEqualTo("15000");
        // A number to read up to; never less than already read.
        assertThat(SurveyDepth.decide("up to 30,000").choiceFor(PART)).isEqualTo("30000");
        assertThat(SurveyDepth.decide("first 5000").choiceFor(PART)).isEqualTo("10000");
        // Words naming no number nor the end: the usual more.
        assertThat(SurveyDepth.decide("a bit further please").choiceFor(PART)).isEqualTo("20000");
        assertThat(SurveyDepth.decide("to the end").choiceFor(PART)).isEqualTo("-1");
    }

    @Test
    void readingToTheEndIsOfferedOnlyWhenAffordable() {
        assertThat(SurveyDepth.suggestions(List.of(PART))).containsExactly(SurveyDepth.STOP_HERE, "Read 10,000 more",
                "Read to the end (about 15,000 records, 3.6 MB)");
        final SurveyDepth.Part huge = new SurveyDepth.Part(8, 1, 10_000, List.of(), null, "key=value",
                2_000_000_000L, 9_000_000, false, null);
        assertThat(SurveyDepth.suggestions(List.of(PART, huge))).containsExactly(SurveyDepth.STOP_HERE,
                "Read 10,000 more of each");
    }

    @Test
    void aPartsDepthIsKeptAndReadBack() {
        final SurveyDepth.Part chosen = PART.withChoice("-1");
        final Map<String, Object> kept = chosen.toMap();
        assertThat(kept).containsEntry("records_read", 10_000L).containsEntry("about", 25_000L)
                .containsEntry("choice", "-1");
        final SurveyDepth.Part back = SurveyDepth.Part.fromMap(kept);
        assertThat(back).isEqualTo(chosen);
        assertThat(back.target()).isEqualTo(Long.MAX_VALUE);
        assertThat(back.settled()).isFalse();
        assertThat(PART.withChoice(SurveyDepth.STOP).settled()).isTrue();
        assertThat(SurveyDepth.intro("ACME", List.of(PART))).isEqualTo("""
                Survey of feed 'ACME': 1 part(s) read only in part.
                Stream 7: 10,000 of about 25,000 records read.
                Kinds of event further in are only found by reading deeper.""");
    }
}
