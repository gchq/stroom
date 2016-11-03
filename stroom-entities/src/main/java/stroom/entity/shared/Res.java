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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = SQLNameConstants.RES)
public class Res extends AuditedEntity implements Copyable<Res>, HasData {
    private static final long serialVersionUID = 4519634323788508083L;

    public static final String TABLE_NAME = SQLNameConstants.RES;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;

    public static final String DATA = SQLNameConstants.DATA;

    public static final String ENTITY_TYPE = "Resource";

    private String data;

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
    }

    @Override
    public void copyFrom(final Res other) {
        this.data = other.data;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
