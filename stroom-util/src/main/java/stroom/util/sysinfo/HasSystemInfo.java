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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public interface HasSystemInfo {

    /**
     * @return The name for the system information being provided. Should be limited
     * to [A-Za-z_-] to avoid URL encoding issues. By default the qualified class
     * name will be used.
     */
    default String getSystemInfoName() {
        return this.getClass().getName();
    }

    /**
     * @return A {@link SystemInfoResult} for part of the system. e.g. for dumping debug information,
     * the sizes of in memory collections/queues, etc.
     * Implementations do not need to perform permission checks unless additional permissions beyond
     * VIEW_SYSTEM_INFO_PERMISSION are required.
     */
    SystemInfoResult getSystemInfo();

    default SystemInfoResult getSystemInfo(final Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return getSystemInfo();
        } else {
            throw new UnsupportedOperationException("This system info provider does not support parameters. " +
                                                    "Has getSystemInfo(params) been implemented for it.");
        }
    }

    default List<ParamInfo> getParamInfo() {
        return Collections.emptyList();
    }

    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    class ParamInfo {

        @JsonProperty
        private final String name;
        @JsonProperty
        private final String description;
        @JsonProperty
        private final ParamType paramType;

        @JsonCreator
        public ParamInfo(@JsonProperty("name") final String name,
                         @JsonProperty("description") final String description,
                         @JsonProperty("paramType") final ParamType paramType) {
            this.name = Objects.requireNonNull(name);
            this.description = Objects.requireNonNull(description);
            this.paramType = Objects.requireNonNull(paramType);
        }

        @SerialisationTestConstructor
        private ParamInfo() {
            this("test", "test", ParamType.MANDATORY);
        }

        public static ParamInfo optionalParam(final String name,
                                              final String description) {
            return new ParamInfo(name, description, ParamType.OPTIONAL);
        }

        public static ParamInfo mandatoryParam(final String name,
                                               final String description) {
            return new ParamInfo(name, description, ParamType.MANDATORY);
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public ParamType getParamType() {
            return paramType;
        }

        @JsonIgnore
        public boolean isMandatory() {
            return ParamType.MANDATORY.equals(paramType);
        }
    }

    enum ParamType {
        OPTIONAL,
        MANDATORY
    }

    static OptionalLong getLongParam(final Map<String, String> params,
                                     final String paramKey) {
        if (NullSafe.isEmptyMap(params) || NullSafe.isBlankString(paramKey)) {
            return OptionalLong.empty();
        } else {
            final String valStr = params.get(paramKey);
            if (NullSafe.isBlankString(valStr)) {
                return OptionalLong.empty();
            } else {
                return OptionalLong.of(Long.parseLong(valStr));
            }
        }
    }

    static OptionalInt getIntParam(final Map<String, String> params,
                                   final String paramKey) {
        if (NullSafe.isEmptyMap(params) || NullSafe.isBlankString(paramKey)) {
            return OptionalInt.empty();
        } else {
            final String valStr = params.get(paramKey);
            if (NullSafe.isBlankString(valStr)) {
                return OptionalInt.empty();
            } else {
                return OptionalInt.of(Integer.parseInt(valStr));
            }
        }
    }

    static Optional<String> getParam(final Map<String, String> params,
                                     final String paramKey) {
        if (NullSafe.isEmptyMap(params) || NullSafe.isBlankString(paramKey)) {
            return Optional.empty();
        } else {
            final String valStr = params.get(paramKey);
            if (NullSafe.isBlankString(valStr)) {
                return Optional.empty();
            } else {
                return Optional.of(valStr);
            }
        }
    }
}
