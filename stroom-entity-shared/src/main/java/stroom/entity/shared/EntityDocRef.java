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

import stroom.entity.shared.SharedDocRef;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasDisplayValue;
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
@XmlRootElement(name = "doc")
@XmlType(name = "doc", propOrder = {"type", "uuid", "name"})
public class EntityDocRef extends SharedDocRef {
    private static final long serialVersionUID = -2121399789820829359L;

    protected Long id;

    public EntityDocRef() {
        // Default constructor necessary for GWT serialisation.
    }

    public EntityDocRef(final String type, String uuid) {
        super(type, uuid);
    }

    public EntityDocRef(final String type, String uuid, final String name) {
        super(type, uuid, name);
    }

    public EntityDocRef(final String type, final Long id, final String uuid, final String name) {
        super(type, uuid, name);
        this.id = id;
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
}
