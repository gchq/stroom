package stroom.util.validation;

import stroom.test.common.AbstractValidatorTest;
import stroom.util.shared.validation.IsSupersetOf;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;

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

        public PoJo(String... values) {
            this.values = Arrays.asList(values);
        }

        @IsSupersetOf(requiredValues = {"one", "two", "three"})
        public List<String> getValues() {
            return values;
        }
    }
}
