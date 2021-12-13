package stroom.util.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate objects of the associated class
 * contain configuration that is require to bootstrap the application.
 * These config objects are derived from YAML and default values only and are
 * bound differently to other config classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BootStrapConfig {

}
