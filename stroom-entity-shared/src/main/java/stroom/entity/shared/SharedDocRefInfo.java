package stroom.entity.shared;

public class SharedDocRefInfo extends SharedDocRef {

    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    public SharedDocRefInfo() {

    }

    public SharedDocRefInfo(final String type,
                            final String uuid,
                            final String name,
                            final Long createTime,
                            final String createUser,
                            final Long updateTime,
                            final String updateUser) {
        super(type, uuid, name);
        this.createTime = createTime;
        this.createUser = createUser;
        this.updateTime = updateTime;
        this.updateUser = updateUser;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public static class Builder extends SharedDocRef.BaseBuilder<SharedDocRefInfo, Builder> {
        private Long createTime;
        private Long updateTime;
        private String createUser;
        private String updateUser;

        public Builder() {
        }

        public Builder createTime(final Long value) {
            this.createTime = value;
            return self();
        }

        public Builder createUser(final String value) {
            this.createUser = value;
            return self();
        }

        public Builder updateTime(final Long value) {
            this.updateTime = value;
            return self();
        }

        public Builder updateUser(final String value) {
            this.updateUser = value;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SharedDocRefInfo build() {

            return new SharedDocRefInfo(getType(),
                    getUuid(),
                    getName(),
                    createTime,
                    createUser,
                    updateTime,
                    updateUser);
        }
    }
}
