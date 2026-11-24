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

package stroom.query.api.datasource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQueryOptionDefinition {

    private static final QueryOptionDefinition BOOLEAN_OPTION = QueryOptionDefinition.builder()
            .id("bool")
            .displayName("Bool")
            .booleanType(false)
            .build();
    private static final QueryOptionDefinition CHOICE_OPTION = QueryOptionDefinition.builder()
            .id("choice")
            .displayName("Choice")
            .choiceType("A")
            .addChoice("A", "Option A")
            .addChoice("B", "Option B")
            .build();
    private static final QueryOptionDefinition INTEGER_OPTION = QueryOptionDefinition.builder()
            .id("int")
            .displayName("Int")
            .integerType(5, 1, 10)
            .build();

    @ParameterizedTest
    @CsvSource(value = {
            "true, true",
            "false, true",
            "TRUE, false",
            "yes, false",
            "'', false"
    })
    void testIsValid_boolean(final String value, final boolean expected) {
        assertThat(BOOLEAN_OPTION.isValid(value))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "A, true",
            "B, true",
            "C, false",
            "a, false"
    })
    void testIsValid_choice(final String value, final boolean expected) {
        assertThat(CHOICE_OPTION.isValid(value))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1, true",
            "10, true",
            "0, false",
            "11, false",
            "-1, false",
            "2.5, false",
            "abc, false"
    })
    void testIsValid_integer(final String value, final boolean expected) {
        assertThat(INTEGER_OPTION.isValid(value))
                .isEqualTo(expected);
    }

    @Test
    void testIsValid_null() {
        assertThat(BOOLEAN_OPTION.isValid(null))
                .isFalse();
    }

    @Test
    void testBuild_invalidDefault() {
        assertThatThrownBy(() -> QueryOptionDefinition.builder()
                .id("choice")
                .displayName("Choice")
                .choiceType("Z")
                .addChoice("A", "Option A")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choice");

        assertThatThrownBy(() -> QueryOptionDefinition.builder()
                .id("int")
                .displayName("Int")
                .integerType(20, 1, 10)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
