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
import stroom.importexport.api.ExternalFile;
import stroom.importexport.migration.DocumentEntity;

import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldScript extends DocumentEntity {
    private static final String ENTITY_TYPE = "Script";

    private String description;
    private String data;
    private String dependenciesXML;
    private DocRefs dependencies;

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @ExternalFile("js")
    public String getResource() {
        return data;
    }

    public void setResource(final String data) {
        this.data = data;
    }

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
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Transient
    public final String getType() {
        return ENTITY_TYPE;
    }
}
