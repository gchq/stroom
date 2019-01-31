package stroom.job.api;

import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;

import java.util.Objects;

public final class Job implements HasAuditInfo, SharedObject {
    public static final String ENTITY_TYPE = "Job";

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

    //TODO gh-1072: add these to the database
    @Override
    public Long getCreateTimeMs() {
        return null;
    }

    @Override
    public void setCreateTimeMs(Long createTimeMs) {

    }

    @Override
    public String getCreateUser() {
        return null;
    }

    @Override
    public void setCreateUser(String createUser) {

    }

    @Override
    public Long getUpdateTimeMs() {
        return null;
    }

    @Override
    public void setUpdateTimeMs(Long updateTimeMs) {

    }

    @Override
    public String getUpdateUser() {
        return null;
    }

    @Override
    public void setUpdateUser(String updateUser) {

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
