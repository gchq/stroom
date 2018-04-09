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

import stroom.entity.shared.Copyable;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;
import stroom.query.api.v2.DocRef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "VIS")
public class OldVisualisation extends DocumentEntity implements Copyable<OldVisualisation> {
    public static final String TABLE_NAME = SQLNameConstants.VISUALISATION;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String FUNCTION_NAME = SQLNameConstants.FUNCTION + SEP + SQLNameConstants.NAME;
    public static final String SETTINGS = SQLNameConstants.SETTINGS;
    public static final String ENTITY_TYPE = "Visualisation";
    public static final String SCRIPT = SQLNameConstants.SCRIPT;

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private String functionName;
    private String settings;

    private String scriptRefXML;
    private DocRef scriptRef;

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = FUNCTION_NAME)
    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(final String functionName) {
        this.functionName = functionName;
    }

    @Column(name = SETTINGS, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile("json")
    public String getSettings() {
        return settings;
    }

    public void setSettings(final String settings) {
        this.settings = settings;
    }

    @Column(name = SCRIPT)
    @Lob
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
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Override
    public void copyFrom(final OldVisualisation other) {
        this.description = other.description;
        this.functionName = other.functionName;
        this.settings = other.settings;
        this.scriptRef = other.scriptRef;

        super.copyFrom(other);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
