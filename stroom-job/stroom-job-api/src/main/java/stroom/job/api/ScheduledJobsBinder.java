package stroom.job.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ScheduledJobsBinder {

    private MapBinder<ScheduledJob, Runnable> mapBinder;

    private ScheduledJobsBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ScheduledJob.class, Runnable.class);
    }

    public static ScheduledJobsBinder create(final Binder binder) {
        return new ScheduledJobsBinder(binder);
    }

    public ScheduledJobsBinder bindJobTo(final Class<? extends Runnable> jobRunnableClass,
                                         final Consumer<ScheduledJob.Builder> jobScheduleBuilder) {
        final ScheduledJob.Builder builder = new ScheduledJob.Builder();
        jobScheduleBuilder.accept(builder);
        final ScheduledJob scheduledJob = builder.build();

        mapBinder.addBinding(scheduledJob).to(jobRunnableClass);
        return bindJobTo(jobRunnableClass, () -> scheduledJob);
    }

    public ScheduledJobsBinder bindJobTo(final Class<? extends Runnable> jobRunnableClass,
                                         final Supplier<ScheduledJob> scheduledJobSupplier) {
        final ScheduledJob scheduledJob = Objects.requireNonNull(scheduledJobSupplier.get());

        mapBinder.addBinding(scheduledJob).to(jobRunnableClass);
        return this;
    }

    public MapBinder<ScheduledJob, Runnable> build() {
        return mapBinder;
    }

}
