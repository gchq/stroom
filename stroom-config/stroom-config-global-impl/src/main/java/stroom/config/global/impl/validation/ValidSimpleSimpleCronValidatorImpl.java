package stroom.config.global.impl.validation;

import stroom.util.scheduler.MalformedCronException;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.validation.ValidSimpleCron;
import stroom.util.shared.validation.ValidSimpleCronValidator;

import javax.validation.ConstraintValidatorContext;

public class ValidSimpleSimpleCronValidatorImpl implements ValidSimpleCronValidator {

    @Override
    public void initialize(final ValidSimpleCron constraintAnnotation) {

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

        if (value != null) {
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
        }
        return result;
    }
}
