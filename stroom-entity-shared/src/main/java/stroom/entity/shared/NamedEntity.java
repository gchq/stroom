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

import stroom.util.shared.HasDisplayValue;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

@MappedSuperclass
public abstract class NamedEntity extends AuditedEntity implements HasName, HasDisplayValue {
    public static final String NAME = SQLNameConstants.NAME;
    private static final long serialVersionUID = -6752797140242673318L;
    private String name;

    @Override
    @Column(name = NAME, nullable = false)
    @Size(min = LengthConstants.MIN_NAME_LENGTH)
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Transient
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    protected void copyFrom(final NamedEntity t) {
        this.name = t.name;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", name=");
        sb.append(name);
    }
}
