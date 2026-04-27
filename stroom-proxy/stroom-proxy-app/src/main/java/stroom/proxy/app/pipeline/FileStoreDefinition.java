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

package stroom.proxy.app.pipeline;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Definition of a named file store used by pipeline stages.
 */
@SuppressWarnings("checkstyle:linelength")
@JsonPropertyOrder(alphabetic = true)
public class FileStoreDefinition extends AbstractConfig implements IsProxyConfig {

    private final String path;

    public FileStoreDefinition() {
        this(null);
    }

    @JsonCreator
    public FileStoreDefinition(@JsonProperty("path") final String path) {
        this.path = normaliseOptional(path);
    }

    @JsonProperty
    @JsonPropertyDescription("Local/shared filesystem path for this file store. If omitted, a default path can be derived from the store name.")
    public String getPath() {
        return path;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
