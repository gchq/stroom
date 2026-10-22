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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.TranslationMapping;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/// What is said of a mapping that doesn't validate (the Stroom MCP server's `tools/generation.py`
/// `mapping_problems` and `_described`, as `tests/test_generation.py` and `tests/test_build_feedback.py` expect).
class TestMappingProblems {

    @Test
    void aMissingKeyIsSaidWithWhatItIs() {
        assertThat(MappingProblems.described(List.of("events", 1, "name")))
                .isEqualTo("Short name for this kind of event, e.g. 'logon'.");
        // A long description is cut at a word, near 160 characters.
        assertThat(MappingProblems.described(List.of("input"))).startsWith("What the XSLT reads: data_splitter")
                .endsWith(" …").hasSizeLessThanOrEqualTo(152);
        // A key a mapping has no place for has no description.
        assertThat(MappingProblems.described(List.of("rules"))).isNull();
    }

    @Test
    void eachErrorIsSaidWhereItIsWithTheValueGiven() {
        final MappingValidationException e = catchThrowableOfType(MappingValidationException.class, () ->
                TranslationMapping.parse(Map.of("input", "json", "events", List.of(Map.of("name", "x",
                        "fields", "all of them")))));

        assertThat(MappingProblems.problems(e, List.of("rules")))
                .contains("events[0].fields: Input should be a valid list (given \"all of them\")")
                .contains("keys that are not part of a mapping: ['rules'] (it has "
                          + PyText.list(MappingProblems.mappingKeys()) + ")");
        assertThat(MappingProblems.structural(e)).isTrue();
    }

    @Test
    void theMappingKeysAreTheModelsSorted() {
        assertThat(MappingProblems.mappingKeys()).contains("common", "events", "input", "extract").isSorted();
    }
}
