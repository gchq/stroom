/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// User-defined settings for one field of an [ElasticIndexDoc], keyed by the field's full name.
///
/// Only settings that differ from the defaults are stored. A field with no settings entry is enabled and has no
/// type-specific options. An entry with [#getVirtualGroups()] set defines a virtual field rather than settings for a
/// field in the index mapping.
@JsonPropertyOrder({"fieldName", "enabled", "denseVector", "virtualGroups"})
@JsonInclude(Include.NON_NULL)
public final class ElasticIndexFieldSettings {

    @JsonProperty
    private final String fieldName;
    @JsonProperty
    private final Boolean enabled;
    @JsonProperty
    private final ElasticDenseVectorSettings denseVector;
    @JsonProperty
    private final List<ElasticVirtualFieldGroup> virtualGroups;

    /// @param fieldName     the full name of the field these settings apply to
    /// @param enabled       whether the field is visible to users and usable in queries; `null` means enabled
    /// @param denseVector   settings for a `dense_vector` field, or `null`
    /// @param virtualGroups the groups of a virtual field, or `null` if this is not a virtual field
    @JsonCreator
    public ElasticIndexFieldSettings(@JsonProperty("fieldName") final String fieldName,
                                     @JsonProperty("enabled") final Boolean enabled,
                                     @JsonProperty("denseVector") final ElasticDenseVectorSettings denseVector,
                                     @JsonProperty("virtualGroups")
                                     final List<ElasticVirtualFieldGroup> virtualGroups) {
        this.fieldName = fieldName;
        this.enabled = enabled;
        this.denseVector = denseVector;
        this.virtualGroups = virtualGroups;
    }

    /// @return the full name of the field these settings apply to
    public String getFieldName() {
        return fieldName;
    }

    /// @return the stored enabled flag, which may be `null` (meaning enabled)
    public Boolean getEnabled() {
        return enabled;
    }

    /// @return `true` unless the field has been explicitly disabled
    @JsonIgnore
    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }

    /// @return settings for a `dense_vector` field, or `null`
    public ElasticDenseVectorSettings getDenseVector() {
        return denseVector;
    }

    /// @return the groups of a virtual field, or `null` if this is not a virtual field
    public List<ElasticVirtualFieldGroup> getVirtualGroups() {
        return virtualGroups;
    }

    /// @return `true` if these settings define a virtual field
    @JsonIgnore
    public boolean isVirtual() {
        return virtualGroups != null;
    }

    /// @return `true` if these settings hold nothing that differs from the defaults, so can be dropped
    @JsonIgnore
    public boolean isDefault() {
        return isEnabled() && denseVector == null && virtualGroups == null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElasticIndexFieldSettings that = (ElasticIndexFieldSettings) o;
        return Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(enabled, that.enabled) &&
               Objects.equals(denseVector, that.denseVector) &&
               Objects.equals(virtualGroups, that.virtualGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, enabled, denseVector, virtualGroups);
    }

    @Override
    public String toString() {
        return "ElasticIndexFieldSettings{" +
               "fieldName='" + fieldName + '\'' +
               ", enabled=" + enabled +
               ", denseVector=" + denseVector +
               ", virtualGroups=" + virtualGroups +
               '}';
    }

    /// @param fieldName the full name of the field
    /// @return a builder for settings of the named field
    public static Builder builder(final String fieldName) {
        return new Builder(fieldName);
    }

    /// @return a builder initialised with these settings
    public Builder copy() {
        return new Builder(this);
    }

    /// Builder for [ElasticIndexFieldSettings].
    public static final class Builder {

        private String fieldName;
        private Boolean enabled;
        private ElasticDenseVectorSettings denseVector;
        private List<ElasticVirtualFieldGroup> virtualGroups;

        private Builder(final String fieldName) {
            this.fieldName = fieldName;
        }

        private Builder(final ElasticIndexFieldSettings settings) {
            this.fieldName = settings.fieldName;
            this.enabled = settings.enabled;
            this.denseVector = settings.denseVector;
            this.virtualGroups = settings.virtualGroups == null
                    ? null
                    : new ArrayList<>(settings.virtualGroups);
        }

        /// @param fieldName the full name of the field
        /// @return this builder
        public Builder fieldName(final String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        /// @param enabled whether the field is enabled; `true` is stored as `null`, the default
        /// @return this builder
        public Builder enabled(final boolean enabled) {
            this.enabled = enabled
                    ? null
                    : Boolean.FALSE;
            return this;
        }

        /// @param denseVector settings for a `dense_vector` field, or `null`
        /// @return this builder
        public Builder denseVector(final ElasticDenseVectorSettings denseVector) {
            this.denseVector = denseVector;
            return this;
        }

        /// @param virtualGroups the groups of a virtual field, or `null` if this is not a virtual field
        /// @return this builder
        public Builder virtualGroups(final List<ElasticVirtualFieldGroup> virtualGroups) {
            this.virtualGroups = virtualGroups;
            return this;
        }

        /// @return the built settings
        public ElasticIndexFieldSettings build() {
            return new ElasticIndexFieldSettings(fieldName, enabled, denseVector, virtualGroups);
        }
    }
}
