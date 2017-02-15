/*
 * Copyright 2016 Crown Copyright
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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"type", "id", "uuid", "name"})
@XmlType(name = "DocRef", propOrder = {"type", "id", "uuid", "name"})
@XmlRootElement(name = "doc")
public class DocRef implements Comparable<DocRef>, HasDisplayValue, Serializable {
    private static final long serialVersionUID = -2121399789820829359L;

    protected String type;
    protected String uuid;
    protected String name;

    @Deprecated
    protected Long id;

    public DocRef() {
    }

    public DocRef(final String type, String uuid) {
        this.type = type;
        this.uuid = uuid;
    }

    public DocRef(final String type, String uuid, final String name) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(final String value) {
        this.type = value;
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

    @Deprecated
    public Long getId() {
        return id;
    }

    @Deprecated
    public void setId(final Long value) {
        this.id = value;

        // All equality is done on uuid so ensure uuid is set to id even if the entity doesn't have a uuid field.
        if (uuid == null && value != null) {
            uuid = String.valueOf(value);
        }
    }

    @XmlTransient
    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public int compareTo(final DocRef o) {
        int diff = type.compareTo(o.type);

        if (diff == 0) {
            if (name != null && o.name != null) {
                diff = name.compareTo(o.name);
            }
        }
        if (diff == 0) {
            diff = uuid.compareTo(o.uuid);
        }
        return diff;
    }

    public String toInfoString() {
        final StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name);
        }
        if (uuid != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("{");
            sb.append(uuid);
            sb.append("}");
        }

        if (sb.length() > 0) {
            return sb.toString();
        }

        return toString();
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DocRef)) return false;

        final DocRef docRef = (DocRef) o;

        if (type != null ? !type.equals(docRef.type) : docRef.type != null) return false;
        if (uuid != null ? !uuid.equals(docRef.uuid) : docRef.uuid != null) return false;
        return name != null ? name.equals(docRef.name) : docRef.name == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
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
