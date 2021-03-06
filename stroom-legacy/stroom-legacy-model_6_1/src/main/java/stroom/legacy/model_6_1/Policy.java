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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

@Deprecated
@Entity
@Table(name = "POLICY", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class Policy extends NamedEntity {
    public static final String TABLE_NAME = SQLNameConstants.POLICY;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String DATA = SQLNameConstants.DATA;
    public static final String ENTITY_TYPE = "Policy";
    public static final String MANAGE_POLICIES_PERMISSION = "Manage Policies";
    private static final long serialVersionUID = 4519634323788508083L;
    private String data;

    public Policy() {
    }

    public static final Policy createStub(final long pk) {
        final Policy policy = new Policy();
        policy.setStub(pk);
        return policy;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Transient
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
