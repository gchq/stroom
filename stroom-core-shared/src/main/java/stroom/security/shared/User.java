package stroom.security.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.HasAuditInfo;

import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
public class User implements HasAuditInfo {
    public static final String ADMIN_USER_NAME = "admin";

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
    private String uuid;

    /**
     * Is this user a user group or a regular user?
     */
    @JsonProperty
    private boolean group;
    @JsonProperty
    private Boolean enabled;

    public User() {
        enabled = true;
    }

    @JsonCreator
    public User(@JsonProperty("id") final Integer id,
                @JsonProperty("version") final Integer version,
                @JsonProperty("createTimeMs") final Long createTimeMs,
                @JsonProperty("createUser") final String createUser,
                @JsonProperty("updateTimeMs") final Long updateTimeMs,
                @JsonProperty("updateUser") final String updateUser,
                @JsonProperty("name") final String name,
                @JsonProperty("uuid") final String uuid,
                @JsonProperty("group") final boolean group,
                @JsonProperty("enabled") final Boolean enabled) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.uuid = uuid;
        this.group = group;
        if (enabled != null) {
            this.enabled = enabled;
        } else {
            this.enabled = true;
        }
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

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean group) {
        this.group = group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", version=" + version +
                ", createTimeMs=" + createTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateTimeMs=" + updateTimeMs +
                ", updateUser='" + updateUser + '\'' +
                ", name='" + name + '\'' +
                ", uuid='" + uuid + '\'' +
                ", group=" + group +
                ", enabled=" + enabled +
                '}';
    }

//    @Override
//    public boolean equals(final Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        final User user = (User) o;
//        return Objects.equals(id, user.id);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(id);
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final User user = (User) o;
        return uuid.equals(user.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    public static class Builder {
        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String name;
        private String uuid;
        private boolean group;
        private boolean enabled = true;

        public Builder id(final int value) {
            id = value;
            return this;
        }

        public Builder name(final String value) {
            name = value;
            return this;
        }

        public Builder uuid(final String value) {
            uuid = value;
            return this;
        }

        public Builder group(final boolean value) {
            group = value;
            return this;
        }

        public Builder enabled(final boolean value) {
            enabled = value;
            return this;
        }

        public User build() {
            return new User(id, version, createTimeMs, createUser, updateTimeMs, updateUser, name, uuid, group, enabled);
        }
    }
}