package stroom.util.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the type or method does not require authentication.
 * Intended for use on servlets at the type level and rest resource impls
 * as the method level (to avoid accidentally adding a new method that becomes
 * unauthenticated by default).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Unauthenticated {

}
