package stroom.job.api;

import java.util.Objects;

public class ScheduledJob {

    private final String description;
    private final boolean enabled;
    private final boolean advanced;
    private final boolean managed;
    private final Schedule schedule;
    private final String name;

    private ScheduledJob(final Schedule schedule,
                         final String name,
                         final String description,
                         final boolean enabled,
                         final boolean advanced,
                         final boolean managed) {
        this.schedule = schedule;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.advanced = advanced;
        this.managed = managed;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    public String getName() {
        return name;
    }

    public boolean isManaged() {
        return managed;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static final class Builder {

        private String description;
        private boolean enabled = true;
        private boolean advanced = true;
        private boolean managed = true;
        private Schedule schedule;
        private String name;

        private Builder() {
        }

        private Builder(final ScheduledJob scheduledJob) {
            description = scheduledJob.description;
            enabled = scheduledJob.enabled;
            advanced = scheduledJob.advanced;
            managed = scheduledJob.managed;
            schedule = scheduledJob.schedule;
            name = scheduledJob.name;
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

        /**
         * See {@link stroom.util.scheduler.SimpleCronScheduler} or
         * {@link stroom.util.scheduler.FrequencyScheduler} for schedule string format.
         */
        public Builder schedule(Schedule.ScheduleType scheduleType, String schedule) {
            this.schedule = new Schedule(scheduleType, schedule);
            return this;
        }

        public Builder schedule(final Schedule schedule) {
            Objects.requireNonNull(schedule);
            this.schedule = schedule;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public ScheduledJob build() {
            Objects.requireNonNull(schedule);
            Objects.requireNonNull(name);
            return new ScheduledJob(schedule, name, description, enabled, advanced, managed);
        }
    }
}
