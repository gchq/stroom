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

package stroom.properties.global.api;

import stroom.docref.SharedObject;

/**
 * This class records global properties that are accessible across the whole
 * cluster.
 */
public class ConfigProperty implements SharedObject {
    private static final long serialVersionUID = 8440384191352234225L;

    //    public static final String TABLE_NAME = SQLNameConstants.GLOBAL + Entity.SEP + SQLNameConstants.PROPERTY;
//    public static final String FOREIGN_KEY = Entity.FK_PREFIX + TABLE_NAME + Entity.ID_SUFFIX;
//    public static final String ENTITY_TYPE = "GlobalProperty";
//    public static final String SOURCE_FILE = "File";
//    public static final String SOURCE_DB = "DB";
//    public static final String SOURCE_DB_DEPRECATED = "DB (Deprecated)";
//    public static final String SOURCE_DEFAULT = "Default";

    private Integer id;
    private String name;
    private String value;
    // These fields are not saved to the database ... just
    private String defaultValue;
    private String source;
    private String description;
    private boolean editable;
    private boolean password;
    private boolean requireRestart;
    private boolean requireUiRestart;

//    public void copyTransients(final ConfigProperty configProperty) {
//        source = configProperty.getSource();
//        defaultValue = configProperty.getDefaultValue();
//        description = configProperty.getDescription();
//        editable = configProperty.isEditable();
//        password = configProperty.isPassword();
//        requireRestart = configProperty.isRequireRestart();
//        requireUiRestart = configProperty.isRequireUiRestart();
//    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

//    public final String getType() {
//        return ENTITY_TYPE;
//    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(final boolean editable) {
        this.editable = editable;
    }

    public boolean isRequireRestart() {
        return requireRestart;
    }

    public void setRequireRestart(final boolean requireRestart) {
        this.requireRestart = requireRestart;
    }

    public boolean isRequireUiRestart() {
        return requireUiRestart;
    }

    public void setRequireUiRestart(final boolean requireUiRestart) {
        this.requireUiRestart = requireUiRestart;
    }

    public boolean isPassword() {
        return password;
    }

    public void setPassword(final boolean password) {
        this.password = password;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public static class Builder {
        private final ConfigProperty instance = new ConfigProperty();

        public Builder name(final String name) {
            instance.setName(name);
            return this;
        }

        public Builder value(final String value) {
            instance.value = value;
            return this;
        }

        public Builder description(final String description) {
            instance.description = description;
            return this;
        }

        public Builder editable(final boolean editable) {
            instance.editable = editable;
            return this;
        }

        public Builder requireRestart(final boolean requireRestart) {
            instance.requireRestart = requireRestart;
            return this;
        }

        public Builder requireUiRestart(final boolean requireUiRestart) {
            instance.requireUiRestart = requireUiRestart;
            return this;
        }

        public Builder password(final boolean password) {
            instance.password = password;
            return this;
        }

        public ConfigProperty build() {
            return instance;
        }
    }
}
