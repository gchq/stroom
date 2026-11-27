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

class TestAiToolMessages {

    @Test
    void callsWithoutArgumentsHaveNone() {
        assertThat(AiToolMessages.hasArguments(null)).isFalse();
        assertThat(AiToolMessages.hasArguments(" ")).isFalse();
        assertThat(AiToolMessages.hasArguments("{}")).isFalse();
        assertThat(AiToolMessages.hasArguments("{ \n }")).isFalse();
        assertThat(AiToolMessages.hasArguments("{\"name\":\"ACME\"}")).isTrue();
    }

    @Test
    void anIndexTemplateUnderReviewIsShownAsAJsonBlockWithItsNotes() {
        // Seen in testing: the form asked the user to confirm an index template the agent never showed them.
        final String shown = AiToolMessages.devToolsMarkdown("PUT _index_template/acme\n{\"priority\": 500}\n",
                List.of("user.name: keyword, as the example"), List.of("composes ecs@mappings"));

        assertThat(shown)
                .contains("```json\nPUT _index_template/acme\n{\"priority\": 500}\n```\n")
                .contains("**From your example:**\n\n- user.name: keyword, as the example\n")
                .contains("**Notes:**\n\n- composes ecs@mappings\n")
                .contains("not to go on the cluster until you have confirmed it");
        assertThat(AiToolMessages.devToolsMarkdown(null, List.of(), List.of())).isNull();
        assertThat(AiToolMessages.devToolsMarkdown(" ", List.of(), List.of())).isNull();
    }

    @Test
    void aTemplateHoldingBackticksCantEndItsBlock() {
        final String shown = AiToolMessages.devToolsMarkdown("PUT x\n{\"description\": \"```\"}", List.of(),
                List.of());

        assertThat(shown).contains("````json\n").endsWith("\n````\n").doesNotContain("Notes");
    }
}
