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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Super class for all stroom config pojos. Can be decorated with property path
 * information, e.g. stroom.path.home
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public abstract class AbstractConfig implements HasPropertyPath {

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    @JsonIgnore
    private PropertyPath basePropertyPath = PropertyPath.blank();

    @Override
    @JsonIgnore
    public PropertyPath getBasePath() {
        return basePropertyPath;
    }

    /**
     * @return The base property path, e.g. "stroom.node" for this config object
     */
    @Override
    @JsonIgnore
    public String getBasePathStr() {
        return getBasePath().toString();
    }

    @Override
    @JsonIgnore
    public PropertyPath getFullPath(final String propertyName) {
        Objects.requireNonNull(basePropertyPath);
        Objects.requireNonNull(propertyName);
        return basePropertyPath.merge(propertyName);
    }

    /**
     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
     * object
     */
    @Override
    @JsonIgnore
    public String getFullPathStr(final String propertyName) {
        return getFullPath(propertyName).toString();
    }

    @Override
    @JsonIgnore
    public void setBasePath(final PropertyPath basePropertyPath) {
        this.basePropertyPath = Objects.requireNonNull(basePropertyPath);
    }

}
