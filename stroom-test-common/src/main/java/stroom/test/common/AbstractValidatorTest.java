package stroom.test.common;

import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;
import stroom.util.validation.ValidationModule;

import com.google.inject.Guice;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

public abstract class AbstractValidatorTest {

    @TempDir
    Path tempDir;

    public Validator getValidator() {

        return Guice.createInjector(
                new TestingHomeAndTempProvidersModule(tempDir),
                new ValidationModule()
        ).getInstance(Validator.class);
    }

    public <T> Set<ConstraintViolation<T>> validate(T object) {
        return getValidator().validate(object);
    }

    public <T> Set<ConstraintViolation<T>> validateValidValue(T object) {
        final Set<ConstraintViolation<T>> violations = getValidator().validate(object);
        Assertions.assertThat(violations)
                .isEmpty();
        return violations;
    }

    public <T> Set<ConstraintViolation<T>> validateInvalidValue(T object) {
        final Set<ConstraintViolation<T>> violations = getValidator().validate(object);
        Assertions.assertThat(violations)
                .hasSize(1);
        return violations;
    }
}
