package stroom.job.api;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;

public class ScheduledJobsModule extends AbstractModule {
    private MapBinder<ScheduledJob, TaskRunnable> mapBinder;

    @Override
    protected void configure() {
        super.configure();
        mapBinder = MapBinder.newMapBinder(binder(), ScheduledJob.class, TaskRunnable.class);
    }

    public Builder bindJob() {
        return new Builder(mapBinder);
    }

    public static final class Builder {
        private final MapBinder<ScheduledJob, TaskRunnable> mapBinder;

        // Mandatory
        private String name;
        private Schedule schedule;

        // Optional
        private boolean enabled = true;
        private boolean advanced = true;
        private boolean managed = true;
        private String description = "";

        Builder(final MapBinder<ScheduledJob, TaskRunnable> mapBinder) {
            this.mapBinder = mapBinder;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder advanced(boolean advanced) {
            this.advanced = advanced;
            return this;
        }

        public Builder managed(boolean managed) {
            this.managed = managed;
            return this;
        }

        public Builder schedule(Schedule.ScheduleType scheduleType, String schedule) {
            this.schedule = new Schedule(scheduleType, schedule);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public <T extends TaskRunnable> void to(final Class<T> consumerClass) {
            Objects.requireNonNull(schedule);
            Objects.requireNonNull(name);
            final ScheduledJob scheduledJob = new ScheduledJob(schedule, name, description, enabled, advanced, managed);
            mapBinder.addBinding(scheduledJob).to(consumerClass);
        }
    }
}
