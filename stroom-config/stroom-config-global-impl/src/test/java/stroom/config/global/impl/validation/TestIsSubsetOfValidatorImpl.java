package stroom.config.global.impl.validation;

import stroom.util.shared.validation.IsSubsetOf;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

class TestIsSubsetOfValidatorImpl extends AbstractValidatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestIsSubsetOfValidatorImpl.class);

    @Test
    void test_valid_all() {
        validateValidValue(new PoJo("one", "two", "three"));
    }

    @Test
    void test_valid_subset() {
        validateValidValue(new PoJo("one", "three"));
    }

    @Test
    void test_valid_empty() {
        validateValidValue(new PoJo());
    }

    @Test
    void test_valid_oneValue() {
        validateValidValue(new PoJo("three"));
    }

    @Test
    void test_valid_outOfOrder() {
        validateValidValue(new PoJo("three", "one"));
    }

    @Test
    void test_valid_duplicates() {
        validateValidValue(new PoJo("three", "three"));
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

        public PoJo(String... values) {
            this.values = Arrays.asList(values);
        }

        @IsSubsetOf(allowedValues = {"one", "two", "three"})
        public List<String> getValues() {
            return values;
        }
    }
}