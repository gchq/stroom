package stroom.config.global.impl.validation;

import stroom.util.scheduler.MalformedCronException;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.validation.ValidCron;
import stroom.util.shared.validation.ValidCronValidator;

import javax.validation.ConstraintValidatorContext;

public class ValidCronValidatorImpl implements ValidCronValidator {

    /**
     * Initializes the validator in preparation for
     * {@link #isValid(Object, ConstraintValidatorContext)} calls.
     * The constraint annotation for a given constraint declaration
     * is passed.
     * <p/>
     * This method is guaranteed to be called before any use of this instance for
     * validation.
     *
     * @param constraintAnnotation annotation instance for a given constraint declaration
     */
    @Override
    public void initialize(final ValidCron constraintAnnotation) {

    }

    /**
     * Implements the validation logic.
     * The state of {@code value} must not be altered.
     * <p>
     * This method can be accessed concurrently, thread-safety must be ensured
     * by the implementation.
     *
     * @param value   object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        boolean result = true;

        try {
            SimpleCron.compile(value);
        } catch (MalformedCronException e) {
            final String msgTemplate =
                context.getDefaultConstraintMessageTemplate() +
                    ". caused by: " +
                    e.getMessage().replaceAll("\\n"," ");

            // We want the exception details in the message so bin the default constraint
            // violation and make a new one.
            context.disableDefaultConstraintViolation();
            context
                .buildConstraintViolationWithTemplate(msgTemplate)
                .addConstraintViolation();
            result = false;
        }
        return result;
    }
}
