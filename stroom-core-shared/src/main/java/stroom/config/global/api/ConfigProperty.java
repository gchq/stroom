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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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


    // The values are held inside a NullWrapper so we can one of three things:
    // A null reference - indicating no value has been supplied
    // A NullWrapper holding null - indicating a null value has been supplied, e.g. an empty maintenanceMessage
    // A NullWrapper holding a non-null value - indicating a non-null value has been supplied, e.g.

    // The cluster wide value held in the database and set by the user in the UI, may be null.
    private NullWrapper<String> databaseValue = null;

    // These fields are not saved to the database,
    // they come from the annotations on the java config classes

    // The cluster wide compile-time default value set in the AppConfig object tree
    private String defaultValue = null;
//    private NullWrapper<String> defaultValue = null;

    // The node specific value as set by the dropwizard YAML file
    private NullWrapper<String> yamlValue = null;

    private String description;
    private boolean isEditable;
    private boolean isPassword;
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

    /**
     * @return The fully qualified name of the property, e.g. "stroom.temp.path"
     */
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    /**
     * @return The effective value of the property on this node taking into account the precedence order
     * of default, database and yaml values
     */
    public Optional<String> getEffectiveValue() {
        if (yamlValue != null) {
            return yamlValue.getValue();
        } else if (databaseValue != null) {
            return databaseValue.getValue();
        } else {
            return Optional.ofNullable(defaultValue);
        }
    }

    /**
     * @return The effective value of the property on this node taking into account the precedence order
     * of default, database and yaml values. If the value is a password then the value will be masked.
     */
    public Optional<String> getEffectiveValueMasked() {
       if (isPassword) {
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
    public Optional<String> getDatabaseOverrideValue() {
        if (databaseValue == null) {
            return null;
//            throw new RuntimeException(String.format("Property %s has no database override set", name));
        } else {
            return databaseValue.getValue();
        }
    }

    public void setDatabaseValue(final String databaseValue) {
        // If somebody overrides the default with a value identical to the default then we need to save it
        this.databaseValue = NullWrapper.of(databaseValue);
    }

    /**
     * @return True if a value has been supplied to override the defaultValue, even it is null
     */
    public boolean hasDatabaseOverride() {
        return this.databaseValue != null;
    }

    /**
     * Remove any override value at the database level, whether null or non-null
     */
    public void removeDatabaseOverride() {
        this.databaseValue = null;
    }

    /**
     * @return The cluster wide compile time read only default value for the property
     */
    public Optional<String> getDefaultValue() {
        return Optional
                .ofNullable(defaultValue);
    }

    public void setDefaultValue(final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * @return The node specific value from the dropwizard YAML file on this node, if present.
     * If no yaml override has been set an exception will be thrown
     * Test with hasYamlOverride() first.
     */
    public Optional<String> getYamlOverrideValue() {
        if (yamlValue == null) {
            return null;
        } else {
            return yamlValue.getValue();
        }
    }

    /**
     * @return True if a value has been supplied to override the defaultValue, even it is null
     */
    public boolean hasYamlOverride() {
        return yamlValue !=  null;
    }

    /**
     * Remove any override value at the database level, whether null or non-null
     */
    public void removeYamlOverride() {
        this.yamlValue = null;
    }

    public void setYamlValue(final String yamlValue) {

        // We cannot distinguish between a value that has been set in the yaml as say 10
        // and a default value of 10, so if the default matches the yaml then we treat the
        // yaml as unset.
        final NullWrapper<String> wrappedYamlValue = NullWrapper.of(yamlValue);
        if (Objects.equals(defaultValue, wrappedYamlValue)) {
            // matches default so remove the yaml value
            this.yamlValue = null;
        } else {
            this.yamlValue = wrappedYamlValue;
        }
    }

    public void unsetYamlValue() {
        this.yamlValue = null;
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
        return isEditable;
    }

    public void setEditable(final boolean editable) {
        this.isEditable = editable;
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
        return isPassword;
    }

    public void setPassword(final boolean password) {
        this.isPassword = password;
    }

    public SourceType getSource() {
        if (yamlValue != null) {
            return SourceType.YAML;
        } else if (databaseValue != null) {
            return SourceType.DATABASE;
        } else {
            return SourceType.DEFAULT;
        }
    }

//    public void setSource(final SourceType source) {
//        this.source = source;
//    }

    private static boolean hasValue(final NullWrapper<?> wrapper) {
        return wrapper != null && wrapper.getValue().isPresent();
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
                ", databaseValue='" + databaseValue + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                ", yamlValue='" + yamlValue + '\'' +
                ", effectiveValue='" + getEffectiveValue() + '\'' +
                ", source='" + getSource() + '\'' +
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

    public static class OverrideValue<T> implements SharedObject {

        private static final OverrideValue UNSET =  new OverrideValue<>(false, null);
        private static final OverrideValue NULL_VALUE =  new OverrideValue<>(true, null);

        private final boolean hasOverride;
        private final T value;

        @SuppressWarnings("unchecked")
        public static <T> OverrideValue<T> unSet() {
            return (OverrideValue<T>) UNSET;
        }

        @SuppressWarnings("unchecked")
        public static <T> OverrideValue<T> withNullValue() {
            return (OverrideValue<T>) NULL_VALUE;
        }

        public static <T> OverrideValue<T> with(final T value) {
            return new OverrideValue<>(true, value);
        }

        private OverrideValue(final boolean hasOverride, final T value) {
            this.hasOverride = hasOverride;
            this.value = value;
        }

        public boolean hasOverride() {
            return hasOverride;
        }

        public Optional<T> getOverrideValue() {
            return Optional.ofNullable(value);
        }

        public void ifHasOverrideValue(final Consumer<T> consumer) {
            if (hasOverride) {
                consumer.accept(value);
            }
        }

        @Override
        public String toString() {
            return "Override{" +
                    "hasOverride=" + hasOverride +
                    ", value=" + value +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final OverrideValue<?> overrideValue = (OverrideValue<?>) o;
            return hasOverride == overrideValue.hasOverride &&
                    Objects.equals(value, overrideValue.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hasOverride, value);
        }
    }

    /**
     * A wrapper to allow us to distinguish between a reference to a thing whose value is null and not
     * having a reference to a thing.
     * @param <T>
     */
    private static class NullWrapper<T> implements SharedObject {
        private T value;

        NullWrapper(final T value) {
            this.value = value;
        }

        static <T> NullWrapper<T> of(final T value) {
            return new NullWrapper<>(value);
        }

        Optional<T> getValue() {
            return Optional.ofNullable(value);
        }

        void setValue(final T value) {
            this.value = value;
        }

        boolean hasNonNullValue() {
            return this.value != null;
        }

        boolean hasNullValue() {
            return this.value == null;
        }

        @Override
        public String toString() {
            return "NullWrapper{" +
                    "value=" + value +
                    '}';
        }

//        static boolean areEqual(final NullWrapper<?> wrapper1, final NullWrapper<?> wrapper2) {
//            if (wrapper1 == null && wrapper2 == null) {
//                return true;
//            } else if ((wrapper1 != null && wrapper1.value != null) && (wrapper2 != null && wrapper2.value == null)) {
//                return true;
//            } else if (wrapper1 != null && wrapper1.value != null && wrapper1.value.equals())
//        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final NullWrapper<?> that = (NullWrapper<?>) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

    }

//    public static class Builder {
//        private final ConfigProperty instance = new ConfigProperty();
//
//        public Builder name(final String name) {
//            instance.setName(name);
//            return this;
//        }
//
//        public Builder value(final String value) {
//            instance.value = value;
//            return this;
//        }
//
//        public Builder description(final String description) {
//            instance.description = description;
//            return this;
//        }
//
//        public Builder editable(final boolean editable) {
//            instance.editable = editable;
//            return this;
//        }
//
//        public Builder requireRestart(final boolean requireRestart) {
//            instance.requireRestart = requireRestart;
//            return this;
//        }
//
//        public Builder requireUiRestart(final boolean requireUiRestart) {
//            instance.requireUiRestart = requireUiRestart;
//            return this;
//        }
//
//        public Builder password(final boolean password) {
//            instance.password = password;
//            return this;
//        }
//
//        public ConfigProperty build() {
//            return instance;
//        }
//    }
}
