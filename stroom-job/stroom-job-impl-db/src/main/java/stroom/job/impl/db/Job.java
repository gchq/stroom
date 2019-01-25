package stroom.job.impl.db;

import java.util.Objects;

public final class Job {

    private Integer id;
    private boolean enabled = false;
    private String description;
    private boolean advanced;
    private int version;

    public Job(){}

    public Job(final Integer id, final boolean enabled, final String description, final boolean advanced){
        this.id = id;
        this.enabled = enabled;
        this.description = description;
        this.advanced = advanced;
    }

    public Integer getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    public void setAdvanced(boolean advanced) {
        this.advanced = advanced;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", enabled=" + enabled +
                ", description='" + description + '\'' +
                ", advanced=" + advanced +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return id == job.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
