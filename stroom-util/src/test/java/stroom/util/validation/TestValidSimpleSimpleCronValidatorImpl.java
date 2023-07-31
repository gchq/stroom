package stroom.util.validation;

import stroom.test.common.AbstractValidatorTest;
import stroom.util.shared.validation.ValidSimpleCron;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import javax.validation.ConstraintViolation;

class TestValidSimpleSimpleCronValidatorImpl extends AbstractValidatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValidSimpleSimpleCronValidatorImpl.class);

    @Test
    void test_null() {
        doValidationTest(null, true);
    }

    @Test
    void test_good() {
        doValidationTest("* * *", true);
    }

    @Test
    void test_bad() {
        doValidationTest("xxxxx", false);
    }

    void doValidationTest(final String value, boolean expectedResult) {
        var myPojo = new Pojo();
        myPojo.simpleCron = value;

        final Set<ConstraintViolation<Pojo>> results = validate(myPojo);

        results.forEach(violation -> {
            LOGGER.info(violation.getMessage());
        });

        Assertions.assertThat(results.isEmpty()).isEqualTo(expectedResult);
    }

    private static class Pojo {

        @ValidSimpleCron
        private String simpleCron;
    }
}
