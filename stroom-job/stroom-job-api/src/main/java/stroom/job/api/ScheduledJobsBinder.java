package stroom.job.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;
import java.util.function.Consumer;

public class ScheduledJobsBinder {

    private final MapBinder<ScheduledJob, Runnable> mapBinder;

    private ScheduledJobsBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ScheduledJob.class, Runnable.class);
    }

    public static ScheduledJobsBinder create(final Binder binder) {
        return new ScheduledJobsBinder(binder);
    }

    public ScheduledJobsBinder bindJobTo(final Class<? extends Runnable> jobRunnableClass,
                                         final Consumer<ScheduledJob.Builder> jobScheduleBuilder) {
        Objects.requireNonNull(jobRunnableClass);
        Objects.requireNonNull(jobScheduleBuilder);

        final ScheduledJob.Builder builder = ScheduledJob.builder();
        jobScheduleBuilder.accept(builder);
        final ScheduledJob scheduledJob = builder.build();

        mapBinder.addBinding(scheduledJob)
                .to(jobRunnableClass);
        return this;
    }

    public MapBinder<ScheduledJob, Runnable> build() {
        return mapBinder;
    }
}
