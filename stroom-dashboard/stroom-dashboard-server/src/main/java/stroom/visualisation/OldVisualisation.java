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
 */

package stroom.visualisation;

import stroom.docref.DocRef;
import stroom.importexport.api.ExternalFile;
import stroom.importexport.migration.DocumentEntity;

import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldVisualisation extends DocumentEntity {
    private static final String ENTITY_TYPE = "Visualisation";

    private String description;
    private String functionName;
    private String settings;

    private String scriptRefXML;
    private DocRef scriptRef;

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(final String functionName) {
        this.functionName = functionName;
    }

    @ExternalFile("json")
    public String getSettings() {
        return settings;
    }

    public void setSettings(final String settings) {
        this.settings = settings;
    }

    public String getScriptRefXML() {
        return scriptRefXML;
    }

    public void setScriptRefXML(final String scriptRefXML) {
        this.scriptRefXML = scriptRefXML;
    }

    @Transient
    @XmlTransient
    public DocRef getScriptRef() {
        return scriptRef;
    }

    public void setScriptRef(final DocRef scriptRef) {
        this.scriptRef = scriptRef;
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
