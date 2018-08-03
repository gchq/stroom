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

package stroom.security;

import stroom.entity.shared.BaseEntitySmall;
import stroom.entity.shared.SQLNameConstants;
import stroom.docref.HasDisplayValue;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "PERM", uniqueConstraints = @UniqueConstraint(columnNames = {
        Permission.NAME}))
public class Permission extends BaseEntitySmall implements HasDisplayValue {
    public static final String TABLE_NAME = SQLNameConstants.PERMISSION;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String NAME = SQLNameConstants.NAME;
    public static final String ENTITY_TYPE = "Permission";
    private static final long serialVersionUID = -4387769015867129844L;
    private String name;

    public static final Permission create(final String name) {
        final Permission permission = new Permission();
        permission.name = name;
        return permission;
    }

    @Column(name = NAME, nullable = false)
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Transient
    @Override
    public String getDisplayValue() {
        return getName();
    }

    @Override
    public String toString() {
        return super.toString() + getDisplayValue();
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
