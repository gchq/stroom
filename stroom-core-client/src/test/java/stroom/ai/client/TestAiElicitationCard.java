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

package stroom.ai.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiElicitationCard {

    @Test
    void theTitleIsTheQuestionsFirstLine() {
        assertThat(AiElicitationCard.firstLine("""
                **Records to translate as Unknown events**

                An Unknown event says what happened isn't known.""", "A form"))
                .isEqualTo("Records to translate as Unknown events");
        assertThat(AiElicitationCard.firstLine("\n## Create the feed?\nIt will receive data.", "A form"))
                .isEqualTo("Create the feed?");
        assertThat(AiElicitationCard.firstLine("Which pipeline name?", "A form")).isEqualTo("Which pipeline name?");
    }

    @Test
    void aFieldThatAsksWhatTheCardsTitleAsksIsNotRepeated() {
        final String title = AiElicitationCard.firstLine("""
                **How should the new index's fields be named?**

                Choose a convention.""", "A form");
        assertThat(AiElicitationCard.isCardTitle("How should the new index's fields be named?", title)).isTrue();
        assertThat(AiElicitationCard.isCardTitle("**How should the new index's fields be named?** ", title))
                .isTrue();
        assertThat(AiElicitationCard.isCardTitle("Which cluster?", title)).isFalse();
        assertThat(AiElicitationCard.isCardTitle("Which cluster?", null)).isFalse();
        assertThat(AiElicitationCard.isCardTitle(null, title)).isFalse();
    }

    @Test
    void withoutAQuestionTheFallbackIsUsed() {
        assertThat(AiElicitationCard.firstLine(null, "A form from fastmcp")).isEqualTo("A form from fastmcp");
        assertThat(AiElicitationCard.firstLine(" \n** **\n", "A form from fastmcp")).isEqualTo("A form from fastmcp");
    }

    @Test
    void theRecommendedOptionsComeFirst() {
        final List<String> values = List.of("3.x", "4.0.2", "4.1.0", "5.0");

        assertThat(AiElicitationCard.recommendedFirst(values, List.of("4.1.0"))).containsExactly(2, 0, 1, 3);
        // Several, as for a choice of several: in the order given.
        assertThat(AiElicitationCard.recommendedFirst(values, List.of("5.0", "4.0.2"))).containsExactly(1, 3, 0, 2);
        // None recommended, or one that isn't an option: as given.
        assertThat(AiElicitationCard.recommendedFirst(values, List.of())).containsExactly(0, 1, 2, 3);
        assertThat(AiElicitationCard.recommendedFirst(values, List.of("6"))).containsExactly(0, 1, 2, 3);
    }
}
