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
import stroom.util.shared.validation.IsSupersetOf;

import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

class TestIsSupersetOfValidatorImpl extends AbstractValidatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestIsSupersetOfValidatorImpl.class);

    @Test
    void test_valid_all() {
        validateValidValue(new PoJo("one", "two", "three"));
    }

    @Test
    void test_valid_superset() {
        validateValidValue(new PoJo("one", "two", "three", "four"));
    }

    @Test
    void test_valid_empty() {
        validateValidValue(new PoJo());
    }

    @Test
    void test_valid_outOfOrder() {
        validateValidValue(new PoJo("four", "two", "three", "one"));
    }

    @Test
    void test_valid_duplicates() {
        validateValidValue(new PoJo("one", "two", "three", "three"));
    }

    @Test
    void test_invalid() {
        final Set<ConstraintViolation<PoJo>> violations = validateInvalidValue(
                new PoJo("three", "four", "five"));

        violations.forEach(violation ->
                LOGGER.info(violation.getMessage()));
    }

    private static class PoJo {

        private final List<String> values;

        public PoJo(final String... values) {
            this.values = Arrays.asList(values);
        }

        @IsSupersetOf(requiredValues = {"one", "two", "three"})
        public List<String> getValues() {
            return values;
        }
    }
}
