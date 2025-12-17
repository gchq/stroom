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

package stroom.util.validation;

import stroom.test.common.AbstractValidatorTest;
import stroom.util.shared.validation.AllMatchPattern;

import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestAllMatchPatternValidatorImpl extends AbstractValidatorTest {

    @Test
    void test_valid() {
        validateValidValue(new PoJo("big-dog", "small-cat", "tiny-ant"));
    }

    @Test
    void test_invalid() {
        final Set<ConstraintViolation<PoJo>> violations = validateInvalidValue(
                new PoJo("BIG-dog", "small_cat", "tiny-ant"));

        assertThat(violations.iterator().next().getMessage())
                .contains("BIG-dog")
                .contains("small_cat")
                .doesNotContain("tiny-ant");

    }

    @Test
    void test_invalid2() {
        final Set<ConstraintViolation<PoJo>> violations = validateInvalidValue(new PoJo(" big-dog"));
        assertThat(violations)
                .hasSize(1);
    }

    @Test
    void test_invalid3() {
        final Set<ConstraintViolation<PoJo>> violations = validateInvalidValue(new PoJo((String) null));
        assertThat(violations)
                .hasSize(1);
    }


    // --------------------------------------------------------------------------------


    private static class PoJo {

        private final List<String> values;

        public PoJo(final String... values) {
            this.values = Arrays.asList(values);
        }

        @AllMatchPattern(pattern = "^[a-z0-9-]+$")
        public List<String> getValues() {
            return values;
        }
    }
}
