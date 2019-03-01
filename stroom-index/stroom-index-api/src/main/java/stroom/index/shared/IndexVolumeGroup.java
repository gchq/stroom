package stroom.index.shared;

import stroom.util.shared.HasAuditInfo;

public class IndexVolumeGroup implements HasAuditInfo {

    private String name;

    // There is nothing to update, so just use the create fields
    private String createUser;
    private Long createTimeMs;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public void setCreateTimeMs(Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return createTimeMs;
    }

    @Override
    public void setUpdateTimeMs(final Long updateTimeMs) {
        // do nothing
    }

    @Override
    public String getUpdateUser() {
        return createUser;
    }

    @Override
    public void setUpdateUser(String updateUser) {
        // do nothing
    }

    public static class Builder {
        private final IndexVolumeGroup instance;

        public Builder(final IndexVolumeGroup instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new IndexVolumeGroup());
        }

        public Builder name(final String value) {
            instance.setName(value);
            return this;
        }

        public Builder createTimeMs(final Long value) {
            instance.setCreateTimeMs(value);
            return this;
        }

        public Builder createUser(final String value) {
            instance.setCreateUser(value);
            return this;
        }

        public IndexVolumeGroup build() {
            return instance;
        }
    }
}
