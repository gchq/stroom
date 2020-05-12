package stroom.util.guice;

import stroom.util.HasHealthCheck;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class HasHealthCheckBinder {
    private final Multibinder<HasHealthCheck> multibinder;

    private HasHealthCheckBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, HasHealthCheck.class);
    }

    public static HasHealthCheckBinder create(final Binder binder) {
        return new HasHealthCheckBinder(binder);
    }

    @ProvidesIntoSet
    public <H extends HasHealthCheck> HasHealthCheckBinder bind(final Class<H> healthCheckClass) {
        multibinder.addBinding().to(healthCheckClass);
        return this;
    }
}
