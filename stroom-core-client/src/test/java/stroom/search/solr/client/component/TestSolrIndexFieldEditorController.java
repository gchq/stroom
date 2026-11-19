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

package stroom.search.solr.client.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestSolrIndexFieldEditorController {

    @Test
    void testNoName() {
        assertThat(SolrIndexFieldEditorController.problemWith("", Set.of()))
                .isEqualTo("An index field must have a name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1abc", "a b", "a-b"})
    void testNameSolrRefuses(final String name) {
        assertThat(SolrIndexFieldEditorController.problemWith(name, Set.of()))
                .startsWith("An index field name must conform to the pattern");
    }

    @Test
    void testNameInUse() {
        assertThat(SolrIndexFieldEditorController.problemWith("EventTime", Set.of("EventTime")))
                .isEqualTo("An index field with this name already exists");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EventTime", "_id", "a_b1"})
    void testNameAllowed(final String name) {
        assertThat(SolrIndexFieldEditorController.problemWith(name, Set.of("Other")))
                .isNull();
    }
}
