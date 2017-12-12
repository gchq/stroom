package stroom.util.shared;

import stroom.query.api.v2.DocRef;

public class DocRefInfo {
    private DocRef docRef;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    public DocRefInfo() {

    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(DocRef docRef) {
        this.docRef = docRef;
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

    public static class Builder {
        private final DocRefInfo instance;

        public Builder() {
            this.instance = new DocRefInfo();
        }

        public Builder docRef(final DocRef value) {
            this.instance.docRef = value;
            return this;
        }

        public Builder createTime(final Long value) {
            this.instance.setCreateTime(value);
            return this;
        }

        public Builder createUser(final String value) {
            this.instance.setCreateUser(value);
            return this;
        }

        public Builder updateTime(final Long value) {
            this.instance.setUpdateTime(value);
            return this;
        }

        public Builder updateUser(final String value) {
            this.instance.setUpdateUser(value);
            return this;
        }

        public DocRefInfo build() {
            return instance;
        }
    }
}
