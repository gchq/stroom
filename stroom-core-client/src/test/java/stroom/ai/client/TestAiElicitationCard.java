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
}
