package stroom.util.lifecycle.jobmanagement;

import java.util.Objects;
import java.util.function.Consumer;

public class ScheduledJob {
    private String description;
    private boolean enabled;
    private boolean advanced;
    private boolean managed;
    private Consumer method;
    private Schedule schedule;
    private String name;

    public ScheduledJob(Schedule schedule, String name, String description, Consumer method, boolean enabled, boolean advanced, boolean managed){
        this.schedule = schedule;
        this.name = name;
        this.description = description;
        this.method = method;
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

    public Consumer getMethod() {
        return method;
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

    public static final class ScheduledJobBuilder {
        // Mandatory
        private String name;
        private Consumer method;
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

        public ScheduledJobBuilder method(Consumer<?> method) {
            this.method = method;
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
            Objects.requireNonNull(method);
            return new ScheduledJob(schedule, name, description, method, enabled, advanced, managed);
        }
    }
}
