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

package stroom.entity.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.HasId;
import stroom.util.shared.HasType;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * Java class for DocRef complex type.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "doc")
@XmlType(name = "doc", propOrder = {"type", "uuid", "name"})
public class DocRef implements Comparable<DocRef>, SharedObject, HasDisplayValue {
    private static final long serialVersionUID = -2121399789820829359L;

    @XmlElement(required = true)
    protected String type;
    protected String uuid;
    protected String name;

    @Deprecated
    @XmlTransient
    protected Long id;

    public DocRef() {
        // Default constructor necessary for GWT serialisation.
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

    protected DocRef(final String type, final Long id, final String uuid, final String name) {
        this.type = type;
        this.id = id;
        this.uuid = uuid;
        this.name = name;
    }

    public static DocRef create(final Entity entity) {
        if (entity == null) {
            return null;
        }

        String type = null;
        Long id = null;
        String uuid = null;
        String name = null;

        if (entity instanceof HasType) {
            type = entity.getType();
        }

        if (entity instanceof HasId) {
            id = ((HasId) entity).getId();

            // All equality is done on uuid so ensure uuid is set to id even if the entity doesn't have a uuid field.
            uuid = String.valueOf(id);
        }

        try {
            if (entity instanceof HasUuid) {
                uuid = ((HasUuid) entity).getUuid();
            }
        } catch (final RuntimeException e) {
            // Ignore, we might get an exception getting some fields on lazy hibernate objects.
        }

        try {
            if (entity instanceof HasName) {
                name = ((HasName) entity).getName();
            }
        } catch (final RuntimeException e) {
            // Ignore, we might get an exception getting some fields on lazy hibernate objects.
        }

        return new DocRef(type, id, uuid, name);
    }

    public String getType() {
        return type;
    }

    public void setType(final String value) {
        this.type = value;
    }

    @Deprecated
    @XmlTransient
    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        this.id = value;

        // All equality is done on uuid so ensure uuid is set to id even if the entity doesn't have a uuid field.
        if (uuid == null && value != null) {
            uuid = String.valueOf(value);
        }
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

    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(type);
        builder.append(uuid);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof DocRef)) {
            return false;
        }

        final DocRef docRef = (DocRef) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(type, docRef.type);
        builder.append(uuid, docRef.uuid);
        return builder.isEquals();
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("type=");
        sb.append(type);
        sb.append(", uuid=");
        sb.append(uuid);
        if (name != null) {
            sb.append(", name=");
            sb.append(name);
        }
        return sb.toString();
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
}
