package stroom.security.shared;

import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;

import java.util.Objects;

public class User implements HasAuditInfo, SharedObject {
    public static final String ADMIN_USER_NAME = "admin";

    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private String createUser;
    private Long updateTimeMs;
    private String updateUser;
    private String name;
    private String uuid;

    /**
     * Is this user a user group or a regular user?
     */
    private boolean group;

    private boolean enabled = true;

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static class Builder {
        private final User instance;

        public Builder(final User instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new User());
        }

        public Builder id(final int value) {
            instance.setId(value);
            return this;
        }

        public Builder name(final String value) {
            instance.setName(value);
            return this;
        }

        public Builder uuid(final String value) {
            instance.setUuid(value);
            return this;
        }

        public Builder group(final boolean value) {
            instance.setGroup(value);
            return this;
        }

        public Builder enabled(final boolean enabled) {
            instance.setEnabled(enabled);
            return this;
        }

        public User build() {
            return this.instance;
        }
    }
}