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

package stroom.pipeline;

import stroom.entity.shared.Copyable;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.HasData;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "XSLT")
public class OldXSLT extends DocumentEntity implements Copyable<OldXSLT>, HasData {
    public static final String TABLE_NAME = SQLNameConstants.XSLT;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String DATA = SQLNameConstants.DATA;
    public static final String ENTITY_TYPE = "XSLT";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private String data;

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile("xsl")
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
    public void copyFrom(final OldXSLT other) {
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
