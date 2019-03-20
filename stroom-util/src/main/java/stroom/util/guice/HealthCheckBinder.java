package stroom.util.guice;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;
import stroom.util.HasHealthCheck;

public class HealthCheckBinder {
    private final Multibinder<HasHealthCheck> multibinder;

    private HealthCheckBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, HasHealthCheck.class);
    }

    public static HealthCheckBinder create(final Binder binder) {
        return new HealthCheckBinder(binder);
    }

    public <H extends HasHealthCheck> HealthCheckBinder bind(final Class<H> healthCheckClass) {
        multibinder.addBinding().to(healthCheckClass);
        return this;
    }
}
