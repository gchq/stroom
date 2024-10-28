/*
 * Copyright 2024 Crown Copyright
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

package stroom.pipeline.writer;

import stroom.meta.api.AttributeMap;
import stroom.util.shared.string.CIKey;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestCSVFormatter {

    @Test
    void testFormat() {
        final Map<String, String> map = Map.of(
                "a", "1",
                "b", "2",
                "c", "3",
                "d", "4");

        final AttributeMap attributeMap = new AttributeMap(CIKey.mapOf(map));

        final String str = CSVFormatter.format(map);
        final String str2 = CSVFormatter.format(attributeMap);

        assertThat(str)
                .isEqualTo(str2);

        assertThat(str)
                .isEqualTo("""
                        "a=1","b=2","c=3","d=4\"""");
    }
}
