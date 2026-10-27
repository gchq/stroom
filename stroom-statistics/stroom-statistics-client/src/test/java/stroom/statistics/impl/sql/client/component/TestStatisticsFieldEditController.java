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

package stroom.statistics.impl.sql.client.component;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsFieldEditController {

    private static final String PATTERN = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    @Test
    void aNewNameIsValid() {
        assertThat(StatisticsFieldEditController.validate("user", Set.of("host"), PATTERN)).isNull();
    }

    @Test
    void aNameIsNeeded() {
        assertThat(StatisticsFieldEditController.validate("", Set.of(), PATTERN))
                .isEqualTo("A statistic field must have a name");
    }

    @Test
    void anotherFieldsNameIsTaken() {
        assertThat(StatisticsFieldEditController.validate("host", Set.of("host"), PATTERN))
                .isEqualTo("Another field with this name already exists");
    }

    @Test
    void theNameMustMatchThePattern() {
        assertThat(StatisticsFieldEditController.validate("a/b", Set.of(), PATTERN))
                .isEqualTo("Invalid name \"a/b\" (valid name pattern: " + PATTERN + ")");
    }

    @Test
    void withNoPatternAnyNameIsValid() {
        assertThat(StatisticsFieldEditController.validate("a/b", Set.of(), null)).isNull();
    }
}
