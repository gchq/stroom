package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

@JsonPropertyOrder({"otherInfo", "createTime", "updateTime", "createUser", "updateUser"})
public class DocRefInfo {
    private DocRef docRef;
    private String otherInfo;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    public DocRefInfo() {
    }

    public DocRefInfo(final DocRef docRef,
                      final String otherInfo,
                      final Long createTime,
                      final String createUser,
                      final Long updateTime,
                      final String updateUser) {
        this.docRef = docRef;
        this.otherInfo = otherInfo;
        this.createTime = createTime;
        this.createUser = createUser;
        this.updateTime = updateTime;
        this.updateUser = updateUser;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    public String getOtherInfo() {
        return otherInfo;
    }

    public void setOtherInfo(final String otherInfo) {
        this.otherInfo = otherInfo;
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
        private DocRef docRef;
        private String otherInfo;
        private Long createTime;
        private Long updateTime;
        private String createUser;
        private String updateUser;

        public Builder() {
        }

        public Builder docRef(final DocRef value) {
            this.docRef = value;
            return this;
        }

        public Builder otherInfo(final String value) {
            this.otherInfo = value;
            return this;
        }

        public Builder createTime(final Long value) {
            this.createTime = value;
            return this;
        }

        public Builder createUser(final String value) {
            this.createUser = value;
            return this;
        }

        public Builder updateTime(final Long value) {
            this.updateTime = value;
            return this;
        }

        public Builder updateUser(final String value) {
            this.updateUser = value;
            return this;
        }

        public DocRefInfo build() {
            return new DocRefInfo(docRef,
                    otherInfo,
                    createTime,
                    createUser,
                    updateTime,
                    updateUser);
        }
    }
}
