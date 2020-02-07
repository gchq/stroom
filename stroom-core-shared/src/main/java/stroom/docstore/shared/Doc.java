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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
@JsonInclude(Include.NON_DEFAULT)
public class Doc {
    private static final long serialVersionUID = -7268601402378907741L;

    private String type;
    private String uuid;
    private String name;
    private String version;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;

    public Doc() {
    }

    public Doc(final String type, final String uuid, final String name) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

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
