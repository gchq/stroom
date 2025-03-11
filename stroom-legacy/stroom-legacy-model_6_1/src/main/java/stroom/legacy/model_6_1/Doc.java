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

package stroom.legacy.model_6_1;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
@XmlRootElement(name = "doc")
@XmlType(name = "Doc", propOrder = {
        "type",
        "uuid",
        "name",
        "version",
        "createTime",
        "updateTime",
        "createUser",
        "updateUser"})
@Deprecated
public class Doc implements SharedObject {

    private static final long serialVersionUID = -7268601402378907741L;

    @XmlElement(name = "type")
    private String type;
    @XmlElement(name = "uuid")
    private String uuid;
    @XmlElement(name = "name")
    private String name;
    @XmlElement(name = "version")
    private String version;
    @XmlElement(name = "createTime")
    private Long createTime;
    @XmlElement(name = "updateTime")
    private Long updateTime;
    @XmlElement(name = "createUser")
    private String createUser;
    @XmlElement(name = "updateUser")
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof final Doc document)) {
            return false;
        }

        if (!Objects.equals(type, document.type)) {
            return false;
        }
        if (!Objects.equals(uuid, document.uuid)) {
            return false;
        }
        if (!Objects.equals(name, document.name)) {
            return false;
        }
        if (!Objects.equals(version, document.version)) {
            return false;
        }
        if (!Objects.equals(createTime, document.createTime)) {
            return false;
        }
        if (!Objects.equals(updateTime, document.updateTime)) {
            return false;
        }
        if (!Objects.equals(createUser, document.createUser)) {
            return false;
        }
        return !Objects.equals(updateUser, document.updateUser);
    }

    @Override
    public int hashCode() {
        int result = type != null
                ? type.hashCode()
                : 0;
        result = 31 * result + (uuid != null
                ? uuid.hashCode()
                : 0);
        result = 31 * result + (name != null
                ? name.hashCode()
                : 0);
        result = 31 * result + (version != null
                ? version.hashCode()
                : 0);
        result = 31 * result + (createTime != null
                ? createTime.hashCode()
                : 0);
        result = 31 * result + (updateTime != null
                ? updateTime.hashCode()
                : 0);
        result = 31 * result + (createUser != null
                ? createUser.hashCode()
                : 0);
        result = 31 * result + (updateUser != null
                ? updateUser.hashCode()
                : 0);
        return result;
    }
}
