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

package stroom.visualisation.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisSelectionKey {

    @Test
    void testValueType() {
        // Names are read regardless of case
        assertThat(new VisSelectionKey("x", "X", "date_time").getValueType())
                .isEqualTo(VisInputType.DATE_TIME);
        assertThat(new VisSelectionKey("y", "Y", "NUMBER").getValueType())
                .isEqualTo(VisInputType.NUMBER);
    }

    @Test
    void testValueTypeUnknownOrMissing() {
        assertThat(new VisSelectionKey("x", null, "DATE").getValueType()).isNull();
        assertThat(new VisSelectionKey("x", null, null).getValueType()).isNull();
    }

    @Test
    void testJson() throws Exception {
        // As a visualisation's settings declare it
        final ObjectMapper mapper = new ObjectMapper();
        final VisSelectionKey key = mapper.readValue(
                "{\"key\": \"series\", \"label\": \"Series\", \"type\": \"TEXT\"}",
                VisSelectionKey.class);

        assertThat(key).isEqualTo(new VisSelectionKey("series", "Series", "TEXT"));
        assertThat(mapper.readValue(mapper.writeValueAsString(key), VisSelectionKey.class))
                .isEqualTo(key);
        // The value type is worked out, not stored
        assertThat(mapper.writeValueAsString(key))
                .isEqualTo("{\"key\":\"series\",\"label\":\"Series\",\"type\":\"TEXT\"}");
    }
}
