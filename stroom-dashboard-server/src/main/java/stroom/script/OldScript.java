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

package stroom.script;

import stroom.entity.shared.DocRefs;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "SCRIPT")
public class OldScript extends DocumentEntity {
    public static final String TABLE_NAME = SQLNameConstants.SCRIPT;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String DEPENDENCIES = SQLNameConstants.DEPENDENCIES;
    public static final String ENTITY_TYPE = "Script";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private String data;
    private String dependenciesXML;
    private DocRefs dependencies;

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = SQLNameConstants.DATA, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile("js")
    public String getResource() {
        return data;
    }

    public void setResource(final String data) {
        this.data = data;
    }

    @Column(name = DEPENDENCIES, length = Integer.MAX_VALUE)
    @Lob
    public String getDependenciesXML() {
        return dependenciesXML;
    }

    public void setDependenciesXML(final String dependenciesXML) {
        this.dependenciesXML = dependenciesXML;
    }

    @Transient
    @XmlTransient
    public DocRefs getDependencies() {
        return dependencies;
    }

    public void setDependencies(final DocRefs dependencies) {
        this.dependencies = dependencies;
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
