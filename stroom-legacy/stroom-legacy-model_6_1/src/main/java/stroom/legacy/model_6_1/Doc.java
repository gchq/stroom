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
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
@XmlRootElement(name = "doc")
@XmlType(name = "Doc", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser"})
@XmlAccessorType(XmlAccessType.FIELD)
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
        if (this == o) return true;
        if (!(o instanceof Doc)) return false;

        final Doc document = (Doc) o;

        if (type != null ? !type.equals(document.type) : document.type != null) return false;
        if (uuid != null ? !uuid.equals(document.uuid) : document.uuid != null) return false;
        if (name != null ? !name.equals(document.name) : document.name != null) return false;
        if (version != null ? !version.equals(document.version) : document.version != null) return false;
        if (createTime != null ? !createTime.equals(document.createTime) : document.createTime != null) return false;
        if (updateTime != null ? !updateTime.equals(document.updateTime) : document.updateTime != null) return false;
        if (createUser != null ? !createUser.equals(document.createUser) : document.createUser != null) return false;
        return updateUser != null ? !updateUser.equals(document.updateUser) : document.updateUser != null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (createTime != null ? createTime.hashCode() : 0);
        result = 31 * result + (updateTime != null ? updateTime.hashCode() : 0);
        result = 31 * result + (createUser != null ? createUser.hashCode() : 0);
        result = 31 * result + (updateUser != null ? updateUser.hashCode() : 0);
        return result;
    }
}
