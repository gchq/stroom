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

package stroom.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestNextNameGenerator {

    @Test
    void simple() {
        final List<String> names = Arrays.asList("New group", "New group (1)", "New group (2)");
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (3)");
    }

    @Test
    void badNumbers() {
        final List<String> names = Arrays.asList("New group",
                "New group (1)",
                "New group (2)",
                "New group (x)",
                "New group (99)");
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (100)");
    }

    @Test
    void nothingNew() {
        final List<String> names = Arrays.asList("Some name", "Some other name (343)", "Another name");
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (1)");
    }

    @Test
    void empty() {
        final ArrayList<String> names = new ArrayList<String>();
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (1)");
    }
}
