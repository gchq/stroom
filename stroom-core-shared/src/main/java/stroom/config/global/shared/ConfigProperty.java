/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.config.global.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

/**
 * This class records config properties that are derived from the following
 * sources in increasing order of precedence (larger number == higher precedence)
 * 1. Cluster wide compile time default values
 * 2. Cluster wide database values (as displayed/edited in the UI properties screen)
 * 3. Node specific values from the dropwizard YAML file
 * <p>
 * The object holds the values from all available sources from which the effective value
 * can be derived.
 * <p>
 * The effective value of a property on a node will be governed by the above precedence rules.
 * Properties can be changed by means of the UI (database level) or via changes to the YAML
 * which are hot-loaded in. A change at the DB level may not be effective if there is value
 * in the YAML.
 * <p>
 * The source of config properties for the application is the Guice bound AppConfig class and its
 * child objects. Changes to the YAML or database will result in updates to the Guice bound AppConfig
 * object.
 * <p>
 */
@JsonIgnoreProperties(value = {"source"}, allowGetters = true)
@JsonInclude(Include.NON_NULL)
public class ConfigProperty implements HasAuditInfo, HasIntegerId, Comparable<ConfigProperty> {

    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private PropertyPath name;

    // TODO now that properties are typed in AppConfig we should really be dealing with typed
    // values here so the UI can edit/display/validate them appropriately according to their type,
    // e.g. a custom UI control for managing List/Map/boolean types

    // The values are held inside an OverrideValue so we can one of three things:
    // A null reference - indicating no override value has been supplied
    // A OverrideValue holding null - indicating a null value has been supplied, e.g. an empty maintenanceMessage
    // A OverrideValue holding a non-null value - indicating a non-null value has been supplied

    // The cluster wide compile-time default value set in the AppConfig object tree
    @JsonProperty
    private String defaultValue;

    // The cluster wide value held in the database and set by the user in the UI, may be null.
    @JsonProperty
    private OverrideValue<String> databaseOverrideValue;

    // These fields are not saved to the database,
    // they come from the annotations on the java config classes

    // The node specific value as set by the dropwizard YAML file
    @JsonProperty
    private OverrideValue<String> yamlOverrideValue;

    @JsonProperty
    private String description;
    @JsonProperty
    private boolean editable;
    @JsonProperty
    private boolean password;
    @JsonProperty
    private boolean requireRestart;
    @JsonProperty
    private boolean requireUiRestart;
    // TODO this is a stopgap until we have fully typed values
    @JsonProperty
    private String dataTypeName;

    ConfigProperty() {
        databaseOverrideValue = OverrideValue.unSet(String.class);
        yamlOverrideValue = OverrideValue.unSet(String.class);
    }

    @JsonIgnore
    public ConfigProperty(final PropertyPath name) {
        this.name = name;
        databaseOverrideValue = OverrideValue.unSet(String.class);
        yamlOverrideValue = OverrideValue.unSet(String.class);
    }

    @JsonIgnore
    public ConfigProperty(final PropertyPath name,
                          final String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        databaseOverrideValue = OverrideValue.unSet(String.class);
        yamlOverrideValue = OverrideValue.unSet(String.class);
    }

    @JsonCreator
    public ConfigProperty(@JsonProperty("id") final Integer id,
                          @JsonProperty("version") final Integer version,
                          @JsonProperty("createTimeMs") final Long createTimeMs,
                          @JsonProperty("createUser") final String createUser,
                          @JsonProperty("updateTimeMs") final Long updateTimeMs,
                          @JsonProperty("updateUser") final String updateUser,
                          @JsonProperty("name") final PropertyPath name,
                          @JsonProperty("defaultValue") final String defaultValue,
                          @JsonProperty("databaseOverrideValue") final OverrideValue<String> databaseOverrideValue,
                          @JsonProperty("yamlOverrideValue") final OverrideValue<String> yamlOverrideValue,
                          @JsonProperty("description") final String description,
                          @JsonProperty("editable") final boolean editable,
                          @JsonProperty("password") final boolean password,
                          @JsonProperty("requireRestart") final boolean requireRestart,
                          @JsonProperty("requireUiRestart") final boolean requireUiRestart,
                          @JsonProperty("dataTypeName") final String dataTypeName) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.defaultValue = defaultValue;
        this.databaseOverrideValue = databaseOverrideValue;
        this.yamlOverrideValue = yamlOverrideValue;
        this.description = description;
        this.editable = editable;
        this.password = password;
        this.requireRestart = requireRestart;
        this.requireUiRestart = requireUiRestart;
        this.dataTypeName = dataTypeName;
    }

    @Override
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

    /**
     * @return The fully qualified name of the property, e.g. "stroom.temp.path"
     */
    @JsonIgnore
    public String getNameAsString() {
        return name == null
                ? null
                : name.toString();
    }

    public PropertyPath getName() {
        return name;
    }

    public void setName(final PropertyPath name) {
        this.name = name;
    }

    @JsonIgnore
    public void setName(final String propertyPathString) {
        Objects.requireNonNull(propertyPathString);
        this.name = PropertyPath.fromPathString(propertyPathString);
    }

    /**
     * @return The effective value of the property on this node taking into account the precedence order
     * of default, database and yaml values
     */
    @JsonIgnore
    public Optional<String> getEffectiveValue() {
        return getEffectiveValue(defaultValue, databaseOverrideValue, yamlOverrideValue);
    }

    public Optional<String> getEffectiveValue(final OverrideValue<String> yamlOverrideValue) {
        return getEffectiveValue(defaultValue, databaseOverrideValue, yamlOverrideValue);
    }

    public static Optional<String> getEffectiveValue(final String defaultValue,
                                                     final OverrideValue<String> databaseOverrideValue,
                                                     final OverrideValue<String> yamlOverrideValue) {
        if (yamlOverrideValue != null && yamlOverrideValue.isHasOverride()) {
            return yamlOverrideValue.getValueAsOptional();
        } else if (databaseOverrideValue != null && databaseOverrideValue.isHasOverride()) {
            return databaseOverrideValue.getValueAsOptional();
        } else {
            return Optional.ofNullable(defaultValue);
        }
    }

    /**
     * @return The effective value of the property on this node taking into account the precedence order
     * of default, database and yaml values. If the value is a password then the value will be masked.
     */
    @JsonIgnore
    public Optional<String> getEffectiveValueMasked() {
        if (password) {
            return Optional.of("********************");
        } else {
            return getEffectiveValue();
        }
    }

    /**
     * @return The cluster wide value set in the UI and stored in the database, if present.
     * If no database override value has been set an exception will be thrown.
     * Test with hasDatabaseOverride() first.
     */
    public OverrideValue<String> getDatabaseOverrideValue() {
        return databaseOverrideValue;
    }

    @JsonIgnore
    public void setDatabaseOverrideValue(final String databaseOverrideValue) {
        // If somebody overrides the default with a value identical to the default then we need to save it
        this.databaseOverrideValue = OverrideValue.with(databaseOverrideValue);
    }

    public void setDatabaseOverrideValue(final OverrideValue<String> databaseOverride) {
        this.databaseOverrideValue = databaseOverride != null
                ? databaseOverride
                : OverrideValue.unSet(String.class);
    }

    /**
     * @return True if a value has been supplied to override the defaultValue, even it is null
     */
    public boolean hasDatabaseOverride() {
        return this.databaseOverrideValue.isHasOverride();
    }

    /**
     * Remove any override value at the database level, whether null or non-null
     */
    public void removeDatabaseOverride() {
        this.databaseOverrideValue = OverrideValue.unSet(String.class);
    }

    /**
     * @return The cluster wide compile time read only default value for the property
     */
    @JsonIgnore
    public Optional<String> getDefaultValue() {
        return Optional
                .ofNullable(defaultValue);
    }

    public void setDefaultValue(final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * @return The node specific value from the dropwizard YAML file on this node, if present.
     */
    public OverrideValue<String> getYamlOverrideValue() {
        return yamlOverrideValue;
    }

    /**
     * @return True if a value has been supplied to override the defaultValue, even it is null
     */
    public boolean hasYamlOverride() {
        return yamlOverrideValue.isHasOverride();
    }

    /**
     * Remove any override value at the yaml level, whether null or non-null
     */
    public void removeYamlOverride() {
        this.yamlOverrideValue = OverrideValue.unSet(String.class);
    }

    @JsonIgnore
    public void setYamlOverrideValue(final String yamlOverrideValue) {

        // We cannot distinguish between a value that has been set in the yaml as say 10
        // and a default value of 10, so if the default matches the yaml then we treat the
        // yaml as unset.
        if (Objects.equals(defaultValue, yamlOverrideValue)) {
            // matches default so remove the yaml value
            this.yamlOverrideValue = OverrideValue.unSet(String.class);
        } else {
            this.yamlOverrideValue = OverrideValue.with(yamlOverrideValue);
        }
    }

    public void setYamlOverrideValue(final OverrideValue<String> yamlOverride) {
        if (yamlOverride == null) {
            this.yamlOverrideValue = OverrideValue.unSet(String.class);
        } else if (yamlOverride.isHasOverride()) {
            setYamlOverrideValue(yamlOverride.getValueAsOptional().orElse(null));
        } else {
            this.yamlOverrideValue = yamlOverride;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * @return True if the databaseValue for this property can be changed in the UI.
     */
    public boolean isEditable() {
        return editable;
    }

    public void setEditable(final boolean editable) {
        this.editable = editable;
    }

    /**
     * @return True if a change to the value requires a full cluster restart to take affect.
     */
    public boolean isRequireRestart() {
        return requireRestart;
    }

    public void setRequireRestart(final boolean requireRestart) {
        this.requireRestart = requireRestart;
    }

    /**
     * @return True if a change to the value requires a restart of the UI nodes to take affect.
     */
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

    public SourceType getSource(final OverrideValue<String> databaseOverrideValue,
                                final OverrideValue<String> yamlOverrideValue) {
        if (yamlOverrideValue != null && yamlOverrideValue.isHasOverride()) {
            return SourceType.YAML;
        } else if (databaseOverrideValue != null && databaseOverrideValue.isHasOverride()) {
            return SourceType.DATABASE;
        } else {
            return SourceType.DEFAULT;
        }
    }

    //TODO Don't want JsonIgnore here as the class level JsonIgnoreProperties annotation allows
    //  us to keep the getter but make jackson ignore it.  Having source is useful if the rest
    //  endpoint is hit manually or by something that doesn't have the java code.
    @JsonIgnore
    public SourceType getSource() {
        return getSource(databaseOverrideValue, yamlOverrideValue);
    }

    public String getDataTypeName() {
        return dataTypeName;
    }

    public void setDataTypeName(final String dataTypeName) {
        this.dataTypeName = dataTypeName;
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
               ", defaultValue='" + defaultValue + '\'' +
               ", databaseOverrideValue='" + databaseOverrideValue + '\'' +
               ", yamlOverrideValue='" + yamlOverrideValue + '\'' +
               ", effectiveValue='" + getEffectiveValue() + '\'' +
               ", source='" + getSource() + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConfigProperty that = (ConfigProperty) o;
        return editable == that.editable &&
               password == that.password &&
               requireRestart == that.requireRestart &&
               requireUiRestart == that.requireUiRestart &&
               Objects.equals(id, that.id) &&
               Objects.equals(version, that.version) &&
               Objects.equals(createTimeMs, that.createTimeMs) &&
               Objects.equals(createUser, that.createUser) &&
               Objects.equals(updateTimeMs, that.updateTimeMs) &&
               Objects.equals(updateUser, that.updateUser) &&
               Objects.equals(name, that.name) &&
               Objects.equals(defaultValue, that.defaultValue) &&
               Objects.equals(databaseOverrideValue, that.databaseOverrideValue) &&
               Objects.equals(yamlOverrideValue, that.yamlOverrideValue) &&
               Objects.equals(description, that.description) &&
               Objects.equals(dataTypeName, that.dataTypeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                version,
                createTimeMs,
                createUser,
                updateTimeMs,
                updateUser,
                name,
                defaultValue,
                databaseOverrideValue,
                yamlOverrideValue,
                description,
                editable,
                password,
                requireRestart,
                requireUiRestart,
                dataTypeName);
    }

    /**
     * The source of the property value
     */
    public enum SourceType {
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

        SourceType(@JsonProperty("name") final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final ConfigProperty copy) {
        return new Builder(copy);
    }

    public static class Builder extends AbstractBuilder<ConfigProperty, ConfigProperty.Builder> {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private PropertyPath name;
        private String defaultValue;
        private OverrideValue<String> databaseOverrideValue;
        private OverrideValue<String> yamlOverrideValue;
        private String description;
        private boolean editable;
        private boolean password;
        private boolean requireRestart;
        private boolean requireUiRestart;
        private String dataTypeName;

        private Builder() {
            this.databaseOverrideValue = OverrideValue.unSet(String.class);
            this.yamlOverrideValue = OverrideValue.unSet(String.class);
        }

        private Builder(final ConfigProperty copy) {
            this.id = copy.id;
            this.version = copy.version;
            this.createTimeMs = copy.createTimeMs;
            this.createUser = copy.createUser;
            this.updateTimeMs = copy.updateTimeMs;
            this.updateUser = copy.updateUser;
            this.name = copy.name;
            this.defaultValue = copy.defaultValue;
            this.databaseOverrideValue = copy.databaseOverrideValue;
            this.yamlOverrideValue = copy.yamlOverrideValue;
            this.description = copy.description;
            this.editable = copy.editable;
            this.password = copy.password;
            this.requireRestart = copy.requireRestart;
            this.requireUiRestart = copy.requireUiRestart;
            this.dataTypeName = copy.dataTypeName;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        public Builder name(final PropertyPath name) {
            this.name = name;
            return self();
        }

        public Builder name(final String propertyPathString) {
            this.name = PropertyPath.fromPathString(propertyPathString);
            return self();
        }

        public Builder defaultValue(final String defaultValue) {
            this.defaultValue = defaultValue;
            return self();
        }

        public Builder databaseOverrideValue(final OverrideValue<String> databaseOverrideValue) {
            this.databaseOverrideValue = databaseOverrideValue != null
                    ? databaseOverrideValue
                    : OverrideValue.unSet(String.class);
            return self();
        }

        public Builder databaseOverrideValue(final String databaseOverrideValue) {
            this.databaseOverrideValue = OverrideValue.with(databaseOverrideValue);
            return self();
        }

        public Builder yamlOverrideValue(final OverrideValue<String> yamlOverrideValue) {
            this.yamlOverrideValue = yamlOverrideValue != null
                    ? yamlOverrideValue
                    : OverrideValue.unSet(String.class);
            return self();
        }

        public Builder yamlOverrideValue(final String yamlOverrideValue) {
            this.yamlOverrideValue = OverrideValue.with(yamlOverrideValue);
            return self();
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder editable(final boolean editable) {
            this.editable = editable;
            return self();
        }

        public Builder password(final boolean password) {
            this.password = password;
            return self();
        }

        public Builder requireRestart(final boolean requireRestart) {
            this.requireRestart = requireRestart;
            return self();
        }

        public Builder requireUiRestart(final boolean requireUiRestart) {
            this.requireUiRestart = requireUiRestart;
            return self();
        }

        public Builder dataTypeName(final String dataTypeName) {
            this.dataTypeName = dataTypeName;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ConfigProperty build() {
            return new ConfigProperty(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    name,
                    defaultValue,
                    databaseOverrideValue,
                    yamlOverrideValue,
                    description,
                    editable,
                    password,
                    requireRestart,
                    requireUiRestart,
                    dataTypeName);
        }
    }
}
