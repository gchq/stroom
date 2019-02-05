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

package stroom.config.global.api;

import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;

/**
 * This class records global properties that are accessible across the whole
 * cluster.
 */
public class ConfigProperty implements HasAuditInfo, SharedObject, Comparable<ConfigProperty> {
    private static final long serialVersionUID = 8440384191352234225L;

    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private String createUser;
    private Long updateTimeMs;
    private String updateUser;
    private String name;
    // TODO now that properties are typed in AppConfig we should really be dealing with typed
    // values here so the UI can edit/display/validate them appropriately according to their type,
    // e.g. a custom UI control for managing List/Map/boolean types
    private String value;

    // These fields are not saved to the database ... just
    private String defaultValue;
    private SourceType source;
    private String description;
    private boolean editable;
    private boolean password;
    private boolean requireRestart;
    private boolean requireUiRestart;

    public ConfigProperty() {
        // Required for Gwt
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
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

    public SourceType getSource() {
        return source;
    }

    public void setSource(final SourceType source) {
        this.source = source;
    }

    @Override
    public int compareTo(final ConfigProperty o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return "ConfigProperty{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", source='" + source + '\'' +
                '}';
    }

    public static enum SourceType {
        DEFAULT("Default"),
        YAML("YAML"),
        DATABASE("Database");

        private final String name;

        SourceType(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
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
