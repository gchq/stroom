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

package stroom.data.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataInfoSection {

    private final ObjectMapper mapper = new ObjectMapper();

    /// Reads entries produced before optional help text was introduced.
    @Test
    void testReadLegacyEntry() throws Exception {
        final DataInfoSection.Entry entry = mapper.readValue(
                "{\"key\":\"Feed\",\"value\":\"TEST-FEED\"}", DataInfoSection.Entry.class);

        assertThat(entry.getKey()).isEqualTo("Feed");
        assertThat(entry.getValue()).isEqualTo("TEST-FEED");
        assertThat(entry.getHelpText()).isNull();
    }

    /// Leaves help text absent when the existing two-argument constructor is used.
    @Test
    void testOmitAbsentHelpText() {
        final JsonNode json = mapper.valueToTree(new DataInfoSection.Entry("Feed", "TEST-FEED"));

        assertThat(json.path("key").asText()).isEqualTo("Feed");
        assertThat(json.path("value").asText()).isEqualTo("TEST-FEED");
        assertThat(json.has("helpText")).isFalse();
    }

    /// Preserves optional help text through the nested section response format.
    @Test
    void testRoundTripHelpText() throws Exception {
        final String helpText = "The feed's name, including <tags> & \"quotes\".";
        final DataInfoSection section = new DataInfoSection("Stream", List.of(
                new DataInfoSection.Entry("Feed", "TEST-FEED", helpText)));
        final DataInfoSection copy = mapper.readValue(mapper.writeValueAsString(section), DataInfoSection.class);

        assertThat(copy.getTitle()).isEqualTo("Stream");
        assertThat(copy.getEntries()).hasSize(1);
        assertThat(copy.getEntries().getFirst().getKey()).isEqualTo("Feed");
        assertThat(copy.getEntries().getFirst().getValue()).isEqualTo("TEST-FEED");
        assertThat(copy.getEntries().getFirst().getHelpText()).isEqualTo(helpText);
    }
}
