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

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryOptionRule {

    @Test
    void testDisabledWhen() {
        final QueryOptionRule rule = QueryOptionRule.disabledWhen("mode", "STRICT");

        assertThat(rule.getOptionId())
                .isEqualTo("mode");
        assertThat(rule.appliesTo("STRICT"))
                .isTrue();
        assertThat(rule.appliesTo("STANDARD"))
                .isFalse();
    }

    @Test
    void testDisabledUnless() {
        final QueryOptionRule rule = QueryOptionRule.disabledUnless("mode", "STRICT");

        assertThat(rule.appliesTo("STRICT"))
                .isFalse();
        assertThat(rule.appliesTo("STANDARD"))
                .isTrue();
    }
}
