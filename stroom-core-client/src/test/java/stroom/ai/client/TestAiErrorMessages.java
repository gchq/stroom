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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiErrorMessages {

    @Test
    void anErrorStartingWithAShortPhraseIsHeadedByIt() {
        // As a model provider answered when its endpoint was paused.
        assertThat(AiErrorMessages.format(object(
                "error", "Bad Request: The endpoint is paused, ask a maintainer to restart it",
                "code", "BAD_REQUEST")))
                .isEqualTo("""
                        #### Bad Request

                        The endpoint is paused, ask a maintainer to restart it

                        - **Code:** BAD_REQUEST""");
    }

    @Test
    void aWrappedErrorIsHeadedByItsKind() {
        assertThat(AiErrorMessages.format(object(
                "type", "error",
                "error", object("type", "overloaded_error", "message", "Overloaded"),
                "request_id", "req_1")))
                .isEqualTo("""
                        #### Overloaded error

                        Overloaded

                        - **Request id:** req_1""");
    }

    @Test
    void aTitleIsTheHeadingAndNestedDetailsAreListed() {
        assertThat(AiErrorMessages.format(object(
                "title", "Invalid request",
                "detail", "The model isn't known. Pick another.",
                "status", "400",
                "param", null,
                "errors", List.of(object("field", "model"), "unknown"),
                "allowed", List.of("a", "b"))))
                .isEqualTo("""
                        #### Invalid request

                        The model isn't known. Pick another.

                        - **Status:** 400
                        - **Errors:**
                          -
                            - **Field:** model
                          - unknown
                        - **Allowed:** a, b""");
    }

    @Test
    void anErrorWithoutAMessageOrKindIsHeadedError() {
        assertThat(AiErrorMessages.format(object("retryAfter", "30")))
                .isEqualTo("""
                        #### Error

                        - **Retry after:** 30""");
        // A colon late in the message, or after a sentence, isn't a heading.
        assertThat(AiErrorMessages.format(object("message", "It failed. Reason: none")))
                .isEqualTo("""
                        #### Error

                        It failed. Reason: none""");
    }

    @Test
    void aListOfErrorsShowsEach() {
        assertThat(AiErrorMessages.format(List.of(object("message", "One"), object("message", "Two"))))
                .isEqualTo("""
                        #### Error

                        One

                        #### Error

                        Two""");
    }

    @Test
    void anythingButAnErrorObjectIsNotFormatted() {
        assertThat(AiErrorMessages.format("text")).isNull();
        assertThat(AiErrorMessages.format(List.of())).isNull();
        assertThat(AiErrorMessages.format(List.of(object("message", "One"), "two"))).isNull();
        assertThat(AiErrorMessages.format(null)).isNull();
    }

    @Test
    void textThatIsNotJsonIsShownAsItIs() {
        assertThat(AiErrorMessages.markdown("Error calling tool 'survey_feed': list index out of range"))
                .isEqualTo("Error calling tool 'survey_feed': list index out of range");
        assertThat(AiErrorMessages.markdown(null)).isNull();
    }

    @Test
    void namesAreShownAsWords() {
        assertThat(AiErrorMessages.humanise("error_description")).isEqualTo("Error description");
        assertThat(AiErrorMessages.humanise("requestId")).isEqualTo("Request id");
        assertThat(AiErrorMessages.humanise("BAD_REQUEST")).isEqualTo("Bad request");
        assertThat(AiErrorMessages.humanise("_")).isEqualTo("_");
    }

    // Keeps the fields in the order given, which Map.of doesn't, and allows nulls.
    private static Map<String, Object> object(final Object... keysAndValues) {
        final List<Object> list = Arrays.asList(keysAndValues);
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            map.put((String) list.get(i), list.get(i + 1));
        }
        return map;
    }
}
