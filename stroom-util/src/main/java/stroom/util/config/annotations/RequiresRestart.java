package stroom.util.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a change to this configuration item requires the system
 * to be restarted for the change to take effect.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRestart {

    enum RestartScope {
        SYSTEM,
        UI
    }

    RestartScope value();
}
