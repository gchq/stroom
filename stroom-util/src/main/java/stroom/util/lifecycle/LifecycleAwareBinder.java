package stroom.util.lifecycle;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class LifecycleAwareBinder {
    private final Multibinder<LifecycleAware> multibinder;

    private LifecycleAwareBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, LifecycleAware.class);
    }

    public static LifecycleAwareBinder create(final Binder binder) {
        return new LifecycleAwareBinder(binder);
    }

    public <T extends LifecycleAware> LifecycleAwareBinder bind(final Class<T> scheduledJobsClass) {
        multibinder.addBinding().to(scheduledJobsClass);
        return this;
    }
}
