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

package stroom.planb.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestBucketGranularity {

    @Test
    void displayValues() {
        assertThat(BucketGranularity.values())
                .extracting(BucketGranularity::getDisplayValue)
                .containsExactly("Hour", "Day", "Week");
    }

    @Test
    void storedAsItsName() throws Exception {
        // Documents already hold the names, so the display values mustn't change what is written or read
        final ObjectMapper mapper = new ObjectMapper();
        for (final BucketGranularity granularity : BucketGranularity.values()) {
            final String json = mapper.writeValueAsString(granularity);
            assertThat(json)
                    .isEqualTo("\"" + granularity.name() + "\"");
            assertThat(mapper.readValue(json, BucketGranularity.class))
                    .isEqualTo(granularity);
        }
    }
}
