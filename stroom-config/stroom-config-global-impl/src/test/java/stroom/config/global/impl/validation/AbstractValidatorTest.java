package stroom.config.global.impl.validation;

import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.assertj.core.api.Assertions;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

@IncludeModule(ValidationModule.class)
abstract class AbstractValidatorTest {

    @Inject
    private Validator validator;

    Validator getValidator() {
        return validator;
    }

    <T> Set<ConstraintViolation<T>> validate(T object) {
        return validator.validate(object);
    }

    <T> Set<ConstraintViolation<T>> validateValidValue(T object) {
        final Set<ConstraintViolation<T>> violations = validator.validate(object);
        Assertions.assertThat(violations)
                .isEmpty();
        return violations;
    }

    <T> Set<ConstraintViolation<T>> validateInvalidValue(T object) {
        final Set<ConstraintViolation<T>> violations = validator.validate(object);
        Assertions.assertThat(violations)
                .hasSize(1);
        return violations;
    }
}