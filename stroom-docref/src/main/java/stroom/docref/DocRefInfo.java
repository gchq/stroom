/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.docref;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DocRefInfo {

    @JsonProperty
    private DocRef docRef;
    @JsonProperty
    private Long createTime;
    @JsonProperty
    private Long updateTime;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String otherInfo;

    public DocRefInfo() {
    }

    @JsonCreator
    public DocRefInfo(@JsonProperty("docRef") final DocRef docRef,
                      @JsonProperty("createTime") final Long createTime,
                      @JsonProperty("updateTime") final Long updateTime,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("otherInfo") final String otherInfo) {
        this.docRef = docRef;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.otherInfo = otherInfo;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getOtherInfo() {
        return otherInfo;
    }

    public void setOtherInfo(final String otherInfo) {
        this.otherInfo = otherInfo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocRefInfo that = (DocRefInfo) o;
        return Objects.equals(docRef, that.docRef) &&
                Objects.equals(createTime, that.createTime) &&
                Objects.equals(updateTime, that.updateTime) &&
                Objects.equals(createUser, that.createUser) &&
                Objects.equals(updateUser, that.updateUser) &&
                Objects.equals(otherInfo, that.otherInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, createTime, updateTime, createUser, updateUser, otherInfo);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private DocRef docRef;
        private Long createTime;
        private Long updateTime;
        private String createUser;
        private String updateUser;
        private String otherInfo;

        private Builder() {
        }

        private Builder(final DocRefInfo docRefInfo) {
            this.docRef = docRefInfo.docRef;
            this.createTime = docRefInfo.createTime;
            this.updateTime = docRefInfo.updateTime;
            this.createUser = docRefInfo.createUser;
            this.updateUser = docRefInfo.updateUser;
            this.otherInfo = docRefInfo.otherInfo;
        }

        public Builder docRef(final DocRef value) {
            this.docRef = value;
            return this;
        }

        public Builder createTime(final Long createTime) {
            this.createTime = createTime;
            return this;
        }

        public Builder updateTime(final Long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public Builder otherInfo(final String otherInfo) {
            this.otherInfo = otherInfo;
            return this;
        }

        public DocRefInfo build() {
            return new DocRefInfo(docRef, createTime, updateTime, createUser, updateUser, otherInfo);
        }
    }
}
