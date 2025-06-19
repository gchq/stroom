package stroom.util.validation;

import stroom.util.scheduler.QuartzCronUtil;
import stroom.util.shared.validation.ValidSimpleCron;
import stroom.util.shared.validation.ValidSimpleCronValidator;

import jakarta.validation.ConstraintValidatorContext;
import org.quartz.CronScheduleBuilder;
import org.quartz.TriggerBuilder;

import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class ValidSimpleSimpleCronValidatorImpl implements ValidSimpleCronValidator {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

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
                final String converted = QuartzCronUtil.convertLegacy(value);
                TriggerBuilder
                        .newTrigger()
                        .withSchedule(CronScheduleBuilder.cronSchedule(converted).inTimeZone(UTC))
                        .startAt(Date.from(Instant.ofEpochMilli(0)))
                        .build();
            } catch (final RuntimeException e) {
                final String msgTemplate =
                        context.getDefaultConstraintMessageTemplate() +
                                ". caused by: " +
                                e.getMessage().replaceAll("\\n", " ");

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
