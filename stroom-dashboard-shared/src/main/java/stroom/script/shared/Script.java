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

package stroom.script.shared;

import stroom.entity.shared.Copyable;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.Res;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "SCRIPT", uniqueConstraints = @UniqueConstraint(columnNames = {"FK_FOLDER_ID", "NAME"}))
public class Script extends DocumentEntity implements Copyable<Script> {
    public static final String TABLE_NAME = SQLNameConstants.SCRIPT;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String RESOURCE = Res.FOREIGN_KEY;
    public static final String DEPENDENCIES = SQLNameConstants.DEPENDENCIES;
    public static final String FETCH_RESOURCE = "resource";
    public static final String ENTITY_TYPE = "Script";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private Res resource;

    private String dependenciesXML;
    private DocRefs dependencies;

    @Override
    public void clearPersistence() {
        super.clearPersistence();
        if (resource != null) {
            final Res newResource = new Res();
            newResource.setData(resource.getData());
            resource = newResource;
        }
    }

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.ALL}, optional = true)
    @JoinColumn(name = RESOURCE)
    @ExternalFile("js")
    public Res getResource() {
        return resource;
    }

    public void setResource(final Res resource) {
        this.resource = resource;
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

    @Override
    public void copyFrom(final Script other) {
        this.description = other.description;

        if (other.resource != null) {
            this.resource = new Res();
            this.resource.copyFrom(other.resource);
        } else {
            this.resource = null;
        }

        this.dependencies = other.dependencies;

        super.copyFrom(other);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
