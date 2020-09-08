package stroom.util.shared.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;
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
 * Validation to ensure that the annotated value is a valid path to a directory
 * and the directory exists. Symbolic links will be followed.
 * Null values will not fail validation.
 * {@link NotNull} can be used in addition to ensure a path is supplied.
 */
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE })
@Retention(RUNTIME)
@Constraint(validatedBy = {ValidDirectoryPathValidator.class})
@Documented
public @interface ValidDirectoryPath {

    String message() default "path does not exist or is not a directory";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };

    // Allows for multiple annotations on the same element
    @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER })
    @Retention(RUNTIME)
    @Documented
    @interface List {
        NotNull[] value();
    }
}
