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

package stroom.ai.impl.tools.fielddoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDescriptions {

    @Test
    void aMeaningFromTheAgentsKnowledgeSaysSo() {
        // Work package E7: the agent's own notes beside the user's documentation, never passed off as the vendor's.
        final Descriptions descriptions = new Descriptions(Map.of(), false, List.of(
                Map.of("field", "LogonType", "meaning", "How the account logged on", "basis", "agent_knowledge",
                        "confidence", "medium"),
                Map.of("field", "IpAddress", "meaning", "The client's address")));
        assertThat(descriptions.vendor(List.of("LogonType"), null))
                .isEqualTo("How the account logged on (the agent's knowledge, medium confidence).");
        assertThat(descriptions.vendor(List.of("IpAddress"), null)).isEqualTo("The client's address.");
    }
}
