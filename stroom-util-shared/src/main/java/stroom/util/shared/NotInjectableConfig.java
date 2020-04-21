package stroom.util.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate objects of the associated class
 * should not be injected with Guice. Objects of the class should instead be obtained
 * via its 'parent' {@link stroom.util.shared.AbstractConfig} class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NotInjectableConfig {
}
