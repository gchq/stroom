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
 * This class records config properties that are derived from the following
 * sources in increasing order of precedence (larger number == higher precedence)
 * 1. Cluster wide compile time default values
 * 2. Cluster wide database values (as displayed/edited in the UI properties screen)
 * 3. Node specific values from the dropwizard YAML file
 *
 * The object holds the values from all available sources from which the effective value
 * can be derived.
 *
 * The effective value of a property on a node will be governed by the above precedence rules.
 * Properties can be changed by means of the UI (database level) or via changes to the YAML
 * which are hot-loaded in. A change at the DB level may not be effective if there is value
 * in the YAML.
 *
 * The source of config properties for the application is the Guice bound AppConfig class and its
 * child objects. Changes to the YAML or database will result in updates to the Guice bound AppConfig
 * object.
 *
 * TODO At present the UI is unable to show the value from the YAML so may give a misleading picture
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

    // The cluster wide value held in the database
    private String databaseValue;

    // These fields are not saved to the database,
    // they come from the annotations on the java config classes

    // The cluster wide compile-time default value set in the AppConfig object tree
    private String defaultValue;

    // The node specific value as set by the dropwizard YAML file
    private String yamlValue;

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

    /**
     * @return The effective value of the property taking into account the precedence order
     */
    public String getEffectiveValue() {
        if (yamlValue != null) {
            return yamlValue;
        } else if (databaseValue != null) {
            return databaseValue;
        } else {
            return defaultValue;
        }
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public void setDatabaseValue(final String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    String getYamlValue() {
        return yamlValue;
    }

    void setYamlValue(final String yamlValue) {
        this.yamlValue = yamlValue;
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

    /**
     * The source of the property value
     */
    public static enum SourceType {
        /**
         * A compile-time default value
         */
        DEFAULT("Default"),
        /**
         * A value from the node specific dropwizard yaml file
         */
        YAML("YAML"),
        /**
         * A cluster-wide value from the database
         */
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
