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
