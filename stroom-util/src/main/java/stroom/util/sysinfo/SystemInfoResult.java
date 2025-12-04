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

package stroom.util.sysinfo;

import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SystemInfoResult {

    @NotNull
    @JsonProperty("name")
    private final String name;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("details")
    private final Map<String, Object> details;

    @JsonCreator
    public SystemInfoResult(@JsonProperty("name") final String name,
                            @JsonProperty("description") final String description,
                            @JsonProperty("details") final Map<String, Object> details) {
        this.name = name;
        this.description = description;
        this.details = Objects.requireNonNull(details);
    }

    @SerialisationTestConstructor
    private SystemInfoResult() {
        this("test", "test", Map.of("test", "test"));
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "SystemInfoResult{" +
               "name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", details=" + details +
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
        final SystemInfoResult that = (SystemInfoResult) o;
        return name.equals(that.name) &&
               Objects.equals(description, that.description) &&
               details.equals(that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, details);
    }

    public static Builder builder(final HasSystemInfo systemInfoProvider) {
        return new Builder(systemInfoProvider.getSystemInfoName());
    }

    /**
     * Only for testing
     */
    static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String name;
        private String description = null;
        private Map<String, Object> details = new HashMap<>();

        public Builder(final String name) {
            this.name = name;
        }

        private Builder() {
        }

        private Builder(final SystemInfoResult systemInfoResult) {
            name = systemInfoResult.name;
            description = systemInfoResult.description;
            details = systemInfoResult.details;
        }

        Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder addDetail(final String key, final Object value) {
            Objects.requireNonNull(key);
            details.put(key, value);
            return this;
        }

        public Builder addDetail(final SystemInfoResult systemInfoResult) {
            Objects.requireNonNull(systemInfoResult);
            details.putAll(NullSafe.map(systemInfoResult.getDetails()));
            return this;
        }

        public Builder addDetail(final String key, final SystemInfoResult systemInfoResult) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(systemInfoResult);
            details.put(key, NullSafe.map(systemInfoResult.getDetails()));
            return this;
        }

        public Builder addError(final Throwable error) {
            Objects.requireNonNull(error);
            details.put("error", error.getMessage());

            return this;
        }

        public Builder addError(final String error) {
            Objects.requireNonNull(error);
            details.put("error", error);

            return this;
        }

        public SystemInfoResult build() {
            if (details.isEmpty()) {
                return new SystemInfoResult(name, description, Collections.emptyMap());
            } else {
                return new SystemInfoResult(name, description, details);
            }
        }
    }
}
