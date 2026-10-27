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

package stroom.ai.impl;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// What testing a model checks of its endpoint's answers: the model must be one the endpoint serves, and what is
/// shown is that model's own entry, not the whole list.
class TestAiServiceImpl {

    private static final String LISTED = """
            {"object": "list", "data": [
              {"id": "gpt-4o", "object": "model", "created": 1715367049, "owned_by": "system"},
              {"id": "qwen3:32b", "object": "model", "created": 1730000000, "owned_by": "library",
               "context_length": 40960}
            ]}""";

    @Test
    void aListedModelIsFoundAndOnlyItsEntryIsGiven() {
        final Optional<JsonNode> entry = AiServiceImpl.modelEntry(LISTED, "qwen3:32b");

        assertThat(entry).isPresent();
        assertThat(entry.get().path("context_length").asInt()).isEqualTo(40960);
        final String shown = JsonUtil.getMapper(true).writeValueAsString(entry.get());
        assertThat(shown).as("the model's parameters, not every model")
                .contains("\"owned_by\" : \"library\"")
                .doesNotContain("gpt-4o");
    }

    @Test
    void aModelTheEndpointDoesNotListIsNotFound() {
        // The endpoint was reached, but chats with this model would all fail.
        assertThat(AiServiceImpl.modelEntry(LISTED, "gpt-5")).isEmpty();
        assertThat(AiServiceImpl.modelEntry(LISTED, "GPT-4O")).as("IDs are compared exactly").isEmpty();
        assertThat(AiServiceImpl.modelIds(LISTED)).containsExactly("gpt-4o", "qwen3:32b");
    }

    @Test
    void aBodyThatIsNotAModelListListsNothing() {
        assertThat(AiServiceImpl.modelIds("<html>Not found</html>")).isEmpty();
        assertThat(AiServiceImpl.modelIds("{\"error\": \"no\"}")).isEmpty();
        assertThat(AiServiceImpl.modelIds("")).isEmpty();
        assertThat(AiServiceImpl.modelIds(null)).isEmpty();
        assertThat(AiServiceImpl.modelEntry("[1, 2]", "gpt-4o")).isEmpty();
    }

    @Test
    void aModelFoundByItsIdIsDescribedByWhatTheEndpointSaid() {
        assertThat(AiServiceImpl.modelDescription("{\"id\": \"router/any\", \"owned_by\": \"gateway\"}",
                "router/any")).contains("\"owned_by\" : \"gateway\"");
        assertThat(AiServiceImpl.modelDescription("OK", "router/any")).isEqualTo("Model 'router/any' was found.");
    }
}
