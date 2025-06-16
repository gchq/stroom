package stroom.docs.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used for generating documentation in stroom-docs.
 * Provides a description of the associated field/type which can
 * be in markdown and include short-codes.
 * Should be one sentence per line, so it meets the style guide of
 * stroom-docs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Description {

    String value();
}
