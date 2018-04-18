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

package stroom.node.shared;

import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * A rack in the system. Data stored in different racks is considered resilient.
 */
@Entity
@Table(name = "RK", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class Rack extends NamedEntity {
    public static final String TABLE_NAME = SQLNameConstants.RACK;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "Rack";
    private static final long serialVersionUID = 3029139308332000568L;

    public static Rack create(final String name) {
        Rack rack = new Rack();
        rack.setName(name);
        return rack;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    public Rack copy() {
        final Rack rack = new Rack();
        rack.setName(getName());
        return rack;
    }
}
