/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.docstore.shared;

import stroom.docref.DocRef;
import stroom.util.shared.Document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser"})
@JsonInclude(Include.NON_NULL)
// TODO Ought to be renamed AbstractDoc, job for master branch
public abstract class Doc implements Document {

    @JsonProperty
    private String type;
    @JsonProperty
    private String uuid;
    @JsonProperty
    private String name;
    @JsonProperty
    private String version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private String updateUser;

    public Doc() {
    }

    public Doc(final String type, final String uuid, final String name) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
    }

    @JsonCreator
    public Doc(@JsonProperty("type") final String type,
               @JsonProperty("uuid") final String uuid,
               @JsonProperty("name") final String name,
               @JsonProperty("version") final String version,
               @JsonProperty("createTimeMs") final Long createTimeMs,
               @JsonProperty("updateTimeMs") final Long updateTimeMs,
               @JsonProperty("createUser") final String createUser,
               @JsonProperty("updateUser") final String updateUser) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
    }

    public DocRef asDocRef() {
        return DocRef.builder()
                .type(type)
                .name(name)
                .uuid(uuid)
                .build();
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public void setCreateTimeMs(final Long createTime) {
        this.createTimeMs = createTime;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public void setUpdateTimeMs(final Long updateTime) {
        this.updateTimeMs = updateTime;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    @Override
    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    @JsonIgnore
    public boolean isSingleton() {
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Doc)) {
            return false;
        }
        final Doc doc = (Doc) o;
        return Objects.equals(type, doc.type) &&
               Objects.equals(uuid, doc.uuid) &&
               Objects.equals(name, doc.name) &&
               Objects.equals(version, doc.version) &&
               Objects.equals(createTimeMs, doc.createTimeMs) &&
               Objects.equals(updateTimeMs, doc.updateTimeMs) &&
               Objects.equals(createUser, doc.createUser) &&
               Objects.equals(updateUser, doc.updateUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
    }

    @Override
    public String toString() {
        return "DocRef{" +
               "type='" + type + '\'' +
               ", uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               '}';
    }


    // --------------------------------------------------------------------------------


    public abstract static class AbstractBuilder<T extends Doc, B extends AbstractBuilder<T, ?>> {

        protected String type;
        protected String uuid;
        protected String name;
        protected String version;
        protected Long createTimeMs;
        protected Long updateTimeMs;
        protected String createUser;
        protected String updateUser;

        public AbstractBuilder() {
        }

        public AbstractBuilder(final Doc doc) {
            this.type = doc.type;
            this.uuid = doc.uuid;
            this.name = doc.name;
            this.version = doc.version;
            this.createTimeMs = doc.createTimeMs;
            this.updateTimeMs = doc.updateTimeMs;
            this.createUser = doc.createUser;
            this.updateUser = doc.updateUser;
        }

        public B type(final String type) {
            this.type = type;
            return self();
        }

        public B uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public B name(final String name) {
            this.name = name;
            return self();
        }

        public B version(final String version) {
            this.version = version;
            return self();
        }

        public B createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        public B updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        public B createUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        public B updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
