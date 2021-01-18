package stroom.util.shared;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

//TODO consider whether this should be @NameBinding
// Currently not to allow all calls to be logged via runtime config, if required and
// to allow only the resource class itself to be annotated rather than every method.
@Inherited
@Target({TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventLogged {
    String ALLOCATE_AUTOMATICALLY = "";

    StroomLoggingOperationType value() default StroomLoggingOperationType.ALLOCATE_AUTOMATICALLY;
    String typeId () default ALLOCATE_AUTOMATICALLY;
    String verb() default ALLOCATE_AUTOMATICALLY;
}
