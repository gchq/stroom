package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

@JsonPropertyOrder({"otherInfo", "createTime", "updateTime", "createUser", "updateUser"})
@JsonInclude(Include.NON_DEFAULT)
public class DocRefInfo {
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String otherInfo;
    @JsonProperty
    private final Long createTime;
    @JsonProperty
    private final Long updateTime;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final String updateUser;

    @JsonCreator
    public DocRefInfo(@JsonProperty("docRef") final DocRef docRef,
                      @JsonProperty("otherInfo") final String otherInfo,
                      @JsonProperty("createTime") final Long createTime,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateTime") final Long updateTime,
                      @JsonProperty("updateUser") final String updateUser) {
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

    public String getOtherInfo() {
        return otherInfo;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public String getUpdateUser() {
        return updateUser;
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
