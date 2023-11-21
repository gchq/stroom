package stroom.util.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validation to ensure that all items of the annotated value
 * (a {@link java.util.Collection<String>}) are non-null and match
 * the supplied pattern
 */
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = {AllMatchPatternValidator.class})
@Documented
public @interface AllMatchPattern {

    String pattern();

    String message() default "collection contains items that do not match the required pattern";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    // Allows for multiple annotations on the same element
    @Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
    @Retention(RUNTIME)
    @Documented
    @interface List {

        NotNull[] value();
    }
}
