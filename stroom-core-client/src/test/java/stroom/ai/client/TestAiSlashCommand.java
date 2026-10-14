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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiSlashCommand {

    @Test
    void namedArguments() {
        assertThat(AiSlashCommand.parseArguments("feed", "feed=ACME-FW note=\"two words\""))
                .isEqualTo(Map.of("feed", "ACME-FW", "note", "two words"));
    }

    @Test
    void plainTextIsTheFirstArgument() {
        assertThat(AiSlashCommand.parseArguments("feed", "ACME firewall events"))
                .isEqualTo(Map.of("feed", "ACME firewall events"));
        // A value with '=' in it, among plain words, is still plain text.
        assertThat(AiSlashCommand.parseArguments("query", "where a=b"))
                .isEqualTo(Map.of("query", "where a=b"));
    }

    @Test
    void nothingGiven() {
        assertThat(AiSlashCommand.parseArguments("feed", "  ")).isEmpty();
        assertThat(AiSlashCommand.parseArguments(null, null)).isEmpty();
        assertThat(AiSlashCommand.parseArguments(null, "words")).isEmpty();
    }
}
