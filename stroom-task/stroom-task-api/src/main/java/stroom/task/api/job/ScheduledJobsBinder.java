package stroom.task.api.job;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class ScheduledJobsBinder {
    private final Multibinder<ScheduledJobs> multibinder;

    private ScheduledJobsBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, ScheduledJobs.class);
    }

    public static ScheduledJobsBinder create(final Binder binder) {
        return new ScheduledJobsBinder(binder);
    }

    public <T extends ScheduledJobs> ScheduledJobsBinder bind(final Class<T> scheduledJobsClass) {
        multibinder.addBinding().to(scheduledJobsClass);
        return this;
    }
}
