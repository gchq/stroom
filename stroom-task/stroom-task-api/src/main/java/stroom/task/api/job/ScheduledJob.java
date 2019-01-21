package stroom.task.api.job;

public class ScheduledJob {
    private String description;
    private boolean enabled;
    private boolean advanced;
    private boolean managed;
    private Schedule schedule;
    private String name;

    ScheduledJob(Schedule schedule, String name, String description, boolean enabled, boolean advanced, boolean managed) {
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
}
