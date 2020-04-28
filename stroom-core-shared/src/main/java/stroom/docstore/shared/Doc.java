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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasUuid;

import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
@JsonInclude(Include.NON_NULL)
public abstract class Doc implements HasAuditInfo, HasUuid {
    @JsonProperty
    private String type;
    @JsonProperty
    private String uuid;
    @JsonProperty
    private String name;
    @JsonProperty
    private String version;
    @JsonProperty
    private Long createTime;
    @JsonProperty
    private Long updateTime;
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
               @JsonProperty("createTime") final Long createTime,
               @JsonProperty("updateTime") final Long updateTime,
               @JsonProperty("createUser") final String createUser,
               @JsonProperty("updateUser") final String updateUser) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.createUser = createUser;
        this.updateUser = updateUser;
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
        return createTime;
    }

    @Override
    public void setCreateTimeMs(final Long createTime) {
        this.createTime = createTime;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTime;
    }

    @Override
    public void setUpdateTimeMs(final Long updateTime) {
        this.updateTime = updateTime;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Doc)) return false;
        final Doc doc = (Doc) o;
        return Objects.equals(type, doc.type) &&
                Objects.equals(uuid, doc.uuid) &&
                Objects.equals(name, doc.name) &&
                Objects.equals(version, doc.version) &&
                Objects.equals(createTime, doc.createTime) &&
                Objects.equals(updateTime, doc.updateTime) &&
                Objects.equals(createUser, doc.createUser) &&
                Objects.equals(updateUser, doc.updateUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
    }

    @Override
    public String toString() {
        return "DocRef{" +
                "type='" + type + '\'' +
                ", uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
