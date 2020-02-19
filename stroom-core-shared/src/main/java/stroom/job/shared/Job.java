package stroom.job.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.HasAuditInfo;

import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
public final class Job implements HasAuditInfo {
    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String name;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private String description;
    @JsonProperty
    private boolean advanced;

    public Job() {
    }

    public Job(final Integer id, final boolean enabled, final String description, final boolean advanced) {
        this.id = id;
        this.enabled = enabled;
        this.description = description;
        this.advanced = advanced;
    }

    @JsonCreator
    public Job(@JsonProperty("id") final Integer id,
               @JsonProperty("version") final Integer version,
               @JsonProperty("createTimeMs") final Long createTimeMs,
               @JsonProperty("createUser") final String createUser,
               @JsonProperty("updateTimeMs") final Long updateTimeMs,
               @JsonProperty("updateUser") final String updateUser,
               @JsonProperty("name") final String name,
               @JsonProperty("enabled") final boolean enabled,
               @JsonProperty("description") final String description,
               @JsonProperty("advanced") final boolean advanced) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.enabled = enabled;
        this.description = description;
        this.advanced = advanced;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
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

    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", version=" + version +
                ", createTimeMs=" + createTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateTimeMs=" + updateTimeMs +
                ", updateUser='" + updateUser + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", description='" + description + '\'' +
                ", advanced=" + advanced +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Job job = (Job) o;
        return id.equals(job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
