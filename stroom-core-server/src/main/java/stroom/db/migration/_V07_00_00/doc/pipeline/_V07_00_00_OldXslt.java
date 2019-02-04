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

package stroom.db.migration._V07_00_00.doc.pipeline;

import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_Copyable;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_DocumentEntity;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_ExternalFile;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_HasData;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "XSLT")
public class _V07_00_00_OldXslt
        extends _V07_00_00_DocumentEntity
        implements _V07_00_00_Copyable<_V07_00_00_OldXslt>, _V07_00_00_HasData {
    public static final String TABLE_NAME = _V07_00_00_SQLNameConstants.XSLT;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String DATA = _V07_00_00_SQLNameConstants.DATA;
    public static final String ENTITY_TYPE = "XSLT";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private String data;

    @Column(name = _V07_00_00_SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @_V07_00_00_ExternalFile("xsl")
    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
    }

    /**
     * @return generic UI drop down value
     */
    @Transient
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Override
    public void copyFrom(final _V07_00_00_OldXslt other) {
        this.description = other.description;
        this.data = other.data;

        super.copyFrom(other);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
