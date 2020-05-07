package stroom.job.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class ScheduledJob {
    private String description;
    private boolean enabled;
    private boolean advanced;
    private boolean managed;
    private Schedule schedule;
    private String name;

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

    @JsonIgnore
    public Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        // Mandatory
        private String name;
        private Schedule schedule;

        // Optional
        private boolean isEnabled = true;
        private boolean isAdvanced = true;
        private boolean isManaged = true;
        private String description = "";

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withEnabledState(boolean isEnabled) {
            this.isEnabled = isEnabled;
            return this;
        }

        public Builder withAdvancedState(boolean isAdvanced) {
            this.isAdvanced = isAdvanced;
            return this;
        }

        public Builder withManagedState(boolean isManaged) {
            this.isManaged = isManaged;
            return this;
        }

        /**
         * See {@link stroom.util.scheduler.SimpleCronScheduler} or
         * {@link stroom.util.scheduler.FrequencyScheduler} for schedule string format.
         */
        public Builder withSchedule(Schedule.ScheduleType scheduleType, String schedule) {
            this.schedule = new Schedule(scheduleType, schedule);
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public ScheduledJob build() {
            Objects.requireNonNull(schedule);
            Objects.requireNonNull(name);
            return new ScheduledJob(schedule, name, description, isEnabled, isAdvanced, isManaged);
        }
    }
}
