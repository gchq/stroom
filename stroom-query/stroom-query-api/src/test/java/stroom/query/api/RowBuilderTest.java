/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RowBuilderTest {
    @Test
    void doesBuild() {
        final Integer depth = 3;
        final List<String> values = Arrays.asList("qwerty", "asdfg");
        final String groupKey = "someGroup";

        final Row row = Row
                .builder()
                .depth(depth)
                .values(values)
                .groupKey(groupKey)
                .build();

        assertThat(row.getDepth()).isEqualTo(depth);
        assertThat(row.getGroupKey()).isEqualTo(groupKey);
        assertThat(row.getValues()).isEqualTo(values);
    }
}
