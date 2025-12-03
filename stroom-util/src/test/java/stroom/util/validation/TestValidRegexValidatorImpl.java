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
import stroom.util.shared.validation.ValidRegex;

import jakarta.validation.ConstraintViolation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

class TestValidRegexValidatorImpl extends AbstractValidatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValidRegexValidatorImpl.class);

    @Test
    void test_null() {
        doValidationTest(null, true);
    }

    @Test
    void test_good() {
        doValidationTest("^.*$", true);
    }

    @Test
    void test_bad() {
        doValidationTest("((", false);
    }

    void doValidationTest(final String value, final boolean expectedResult) {
        final var myPojo = new Pojo();
        myPojo.regex = value;

        final Set<ConstraintViolation<Pojo>> results = validate(myPojo);
        results.forEach(violation -> {
            LOGGER.info(violation.getMessage());
        });

        Assertions.assertThat(results.isEmpty()).isEqualTo(expectedResult);
    }

    private static class Pojo {

        @ValidRegex
        private String regex;
    }

}
