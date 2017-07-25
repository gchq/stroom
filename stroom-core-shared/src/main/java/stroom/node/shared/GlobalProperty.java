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

package stroom.node.shared;

import stroom.entity.shared.HasPassword;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * This class records global properties that are accessible across the whole
 * cluster.
 */
@Entity
@Table(name = "GLOB_PROP", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class GlobalProperty extends NamedEntity implements HasPassword {
    public static final String TABLE_NAME = SQLNameConstants.GLOBAL + SEP + SQLNameConstants.PROPERTY;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "GlobalProperty";
    public static final String MANAGE_PROPERTIES_PERMISSION = "Manage Properties";
    public static final String SOURCE_FILE = "File";
    public static final String SOURCE_DB = "DB";
    public static final String SOURCE_DB_DEPRECATED = "DB (Deprecated)";
    public static final String SOURCE_DEFAULT = "Default";
    private static final long serialVersionUID = 8440384191352234225L;
    private String value;
    // These fields are not saved to the database ... just
    private String defaultValue;
    private String source;
    private String description;
    private boolean editable;
    private boolean password;
    private boolean requireRestart;
    private boolean requireUiRestart;

    public void copyTransients(final GlobalProperty globalProperty) {
        source = globalProperty.getSource();
        defaultValue = globalProperty.getDefaultValue();
        description = globalProperty.getDescription();
        editable = globalProperty.isEditable();
        password = globalProperty.isPassword();
        requireRestart = globalProperty.isRequireRestart();
        requireUiRestart = globalProperty.isRequireUiRestart();
    }

    @Override
    @Column(name = SQLNameConstants.VALUE, nullable = false, length = Integer.MAX_VALUE)
    @Lob
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(final String value) {
        this.value = value;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Transient
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Transient
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Transient
    public boolean isEditable() {
        return editable;
    }

    public void setEditable(final boolean editable) {
        this.editable = editable;
    }

    @Transient
    public boolean isRequireRestart() {
        return requireRestart;
    }

    public void setRequireRestart(final boolean requireRestart) {
        this.requireRestart = requireRestart;
    }

    @Transient
    public boolean isRequireUiRestart() {
        return requireUiRestart;
    }

    public void setRequireUiRestart(final boolean requireUiRestart) {
        this.requireUiRestart = requireUiRestart;
    }

    @Override
    @Transient
    public boolean isPassword() {
        return password;
    }

    public void setPassword(final boolean password) {
        this.password = password;
    }

    @Transient
    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

}
