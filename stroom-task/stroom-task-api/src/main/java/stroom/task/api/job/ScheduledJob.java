package stroom.task.api.job;

import java.util.Objects;

public class ScheduledJob {
    private String description;
    private boolean enabled;
    private boolean advanced;
    private boolean managed;
    private Schedule schedule;
    private String name;

    public ScheduledJob(Schedule schedule, String name, String description, boolean enabled, boolean advanced, boolean managed) {
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ScheduledJob that = (ScheduledJob) o;
        return enabled == that.enabled &&
                advanced == that.advanced &&
                managed == that.managed &&
                Objects.equals(description, that.description) &&
                Objects.equals(schedule, that.schedule) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, enabled, advanced, managed, schedule, name);
    }

    public static final class ScheduledJobBuilder {
        // Mandatory
        private String name;
        private Schedule schedule;

        // Optional
        private boolean enabled = true;
        private boolean advanced = true;
        private boolean managed = true;
        private String description = "";

        private ScheduledJobBuilder() {
        }

        public static ScheduledJobBuilder jobBuilder() {
            return new ScheduledJobBuilder();
        }

        public ScheduledJobBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ScheduledJobBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ScheduledJobBuilder advanced(boolean advanced) {
            this.advanced = advanced;
            return this;
        }

        public ScheduledJobBuilder managed(boolean managed) {
            this.managed = managed;
            return this;
        }

        public ScheduledJobBuilder schedule(Schedule.ScheduleType scheduleType, String schedule) {
            this.schedule = new Schedule(scheduleType, schedule);
            return this;
        }

        public ScheduledJobBuilder name(String name) {
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
