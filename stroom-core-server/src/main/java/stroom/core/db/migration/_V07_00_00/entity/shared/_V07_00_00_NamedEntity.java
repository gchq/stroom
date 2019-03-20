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

package stroom.core.db.migration._V07_00_00.entity.shared;

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;

public abstract class _V07_00_00_NamedEntity extends _V07_00_00_AuditedEntity implements _V07_00_00_HasName, _V07_00_00_HasDisplayValue {
    public static final String NAME = _V07_00_00_SQLNameConstants.NAME;
    private static final long serialVersionUID = -6752797140242673318L;
    private String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    protected void copyFrom(final _V07_00_00_NamedEntity t) {
        this.name = t.name;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", name=");
        sb.append(name);
    }
}
