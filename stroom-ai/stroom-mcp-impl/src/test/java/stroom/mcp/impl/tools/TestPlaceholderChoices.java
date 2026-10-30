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

import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.tools.sample.Placeholders;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestPlaceholderChoices {

    @Test
    void eachValueIsAQuestionOfItsOwnWhenAFormCanHoldThemAll() {
        final List<Placeholders.Found> found = found(ElicitationSchema.MAX_FIELDS);

        final Map<String, Consent.Question> questions = PlaceholderChoices.questions(found, PlaceholderChoices.OPTIONS);

        assertThat(questions).hasSize(ElicitationSchema.MAX_FIELDS)
                .doesNotContainKey(PlaceholderChoices.OTHERS)
                .containsKeys("v1", "v20");
        assertThat(questions.get("v1").options()).isEqualTo(PlaceholderChoices.OPTIONS);
    }

    @Test
    void tooManyValuesForAFormAreAskedAboutTheMostCommonOneByOneAndTheRestTogether() {
        // Seen: a sample of ~150 fields with dozens of stand-ins, whose form was declined as too large.
        final List<Placeholders.Found> found = found(25);

        final Map<String, Consent.Question> questions = PlaceholderChoices.questions(found, PlaceholderChoices.OPTIONS);

        assertThat(questions).hasSize(ElicitationSchema.MAX_FIELDS);
        assertThat(questions.keySet()).startsWith("v1", "v2").contains("v19").doesNotContain("v20");
        assertThat(questions.get(PlaceholderChoices.OTHERS).text())
                .isEqualTo("The other 6 values ('v20', 'v21', 'v22', ...), all the same way");
        assertThat(questions.get(PlaceholderChoices.OTHERS).options()).isEqualTo(PlaceholderChoices.OPTIONS);
    }

    @Test
    void theAnswerForTheRestGoesToEachOfThem() {
        final List<Placeholders.Found> found = found(25);

        final Map<String, String> byValue = PlaceholderChoices.byValue(found, Map.of(
                "v1", PlaceholderChoices.KEEP,
                PlaceholderChoices.OTHERS, PlaceholderChoices.LEAVE_OUT));

        assertThat(byValue).hasSize(25)
                .containsEntry("v1", PlaceholderChoices.KEEP)
                .containsEntry("v2", PlaceholderChoices.LEAVE_OUT)
                .containsEntry("v25", PlaceholderChoices.LEAVE_OUT);
    }

    @Test
    void aValueWithNoAnswerIsLeftOut() {
        final List<Placeholders.Found> found = found(3);

        assertThat(PlaceholderChoices.byValue(found, Map.of("v2", PlaceholderChoices.KEEP)))
                .containsExactly(Map.entry("v2", PlaceholderChoices.KEEP));
        assertThat(PlaceholderChoices.byValue(found, Map.of())).isEmpty();
    }

    // Stand-ins v1 to vN, each in a field of its own, the first in the most records.
    private static List<Placeholders.Found> found(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(n -> new Placeholders.Found("v" + n, List.of("f" + n), 1_000 - n, "numbers"))
                .toList();
    }
}
