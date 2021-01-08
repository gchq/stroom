package stroom.util.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

@Inherited
@Target({TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StroomLog {
    String ALLOCATE_AUTOMATICALLY = "";

    StroomLoggingOperationType value() default StroomLoggingOperationType.ALLOCATE_AUTOMATICALLY;
    String typeId () default ALLOCATE_AUTOMATICALLY;
    String description () default ALLOCATE_AUTOMATICALLY;
}
