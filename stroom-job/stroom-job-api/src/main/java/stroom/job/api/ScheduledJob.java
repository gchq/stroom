package stroom.job.api;

import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import java.util.Objects;

public class ScheduledJob {

    private final String description;
    private final boolean enabled;
    private final boolean enabledOnBootstrap;
    private final boolean advanced;
    private final boolean managed;
    private final Schedule schedule;
    private final String name;

    private ScheduledJob(final Schedule schedule,
                         final String name,
                         final String description,
                         final boolean enabled,
                         final boolean enabledOnBootstrap,
                         final boolean advanced,
                         final boolean managed) {
        this.schedule = schedule;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.enabledOnBootstrap = enabledOnBootstrap;
        this.advanced = advanced;
        this.managed = managed;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnabledOnBootstrap() {
        return enabledOnBootstrap;
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

    @Override
    public String toString() {
        return "ScheduledJob{" +
               "description='" + description + '\'' +
               ", enabled=" + enabled +
               ", enabledOnBootstrap=" + enabledOnBootstrap +
               ", advanced=" + advanced +
               ", managed=" + managed +
               ", schedule=" + schedule +
               ", name='" + name + '\'' +
               '}';
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static final class Builder {

        private String description;
        private boolean enabled = true;
        private boolean advanced = true;
        private boolean managed = true;
        private Schedule schedule;
        private String name;
        private boolean enabledOnBootstrap = true;

        private Builder() {
        }

        private Builder(final ScheduledJob scheduledJob) {
            description = scheduledJob.description;
            enabled = scheduledJob.enabled;
            enabledOnBootstrap = scheduledJob.enabledOnBootstrap;
            advanced = scheduledJob.advanced;
            managed = scheduledJob.managed;
            schedule = scheduledJob.schedule;
            name = scheduledJob.name;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        /**
         * The initial state of the job and jobNode when they are created on a fresh system.
         * It has no effect on existing jobs as their enabled state is then managed in
         * the UI.
         * <p>
         * Default value is true.
         */
        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * If the prop stroom.job.enableJobsOnBootstrap is true, then when the jobs and jobNodes
         * are created they will have an enabled state matching the value of enabledOnBootstrap.
         * <p>
         * This allows for certain jobs to remain disabled in a test environment where you otherwise
         * want all jobs enabled on boot.
         * <p>
         * Default value is true.
         */
        public Builder enabledOnBootstrap(final boolean enabledOnBootstrap) {
            this.enabledOnBootstrap = enabledOnBootstrap;
            return this;
        }

        public Builder advanced(final boolean advanced) {
            this.advanced = advanced;
            return this;
        }

        /**
         * If true, the job will be visible in the UI so that the user can change the enabled state
         * or alter the schedule. Un-managed jobs are typically internal jobs that must always run
         * on with a non-configurable schedule.
         */
        public Builder managed(final boolean managed) {
            this.managed = managed;
            return this;
        }

        public Builder cronSchedule(final String schedule) {
            this.schedule = new Schedule(ScheduleType.CRON, schedule);
            return this;
        }

        public Builder frequencySchedule(final String schedule) {
            this.schedule = new Schedule(ScheduleType.FREQUENCY, schedule);
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public ScheduledJob build() {
            Objects.requireNonNull(schedule);
            Objects.requireNonNull(name);
            return new ScheduledJob(schedule, name, description, enabled, enabledOnBootstrap, advanced, managed);
        }
    }
}
