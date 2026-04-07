/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.visualisation.shared;

import stroom.docs.shared.Description;
import stroom.util.shared.ResourceKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Wraps data together for VisualisationAssetResource.updateNewUploadedFile()
 * and VisualisationAssetResource.updateNewFile().
 */
@Description(
        "Parameter for VisualisationAssetResource.updateNewUploadedFile and updateNewFile()"
)
@JsonPropertyOrder({
        "path",
        "resourceKey"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssetUpdateNewFile {

    @JsonProperty
    private final String path;

    @JsonProperty
    private final ResourceKey resourceKey;

    @JsonCreator
    public VisualisationAssetUpdateNewFile(
            @JsonProperty("path") final String path,
            @JsonProperty("resourceKey") final ResourceKey resourceKey) {
        this.path = path;
        this.resourceKey = resourceKey;
    }

    public String getPath() {
        return path;
    }

    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAssetUpdateNewFile that = (VisualisationAssetUpdateNewFile) o;
        return Objects.equals(path, that.path)
               && Objects.equals(resourceKey, that.resourceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, resourceKey);
    }

    @Override
    public String toString() {
        return "VisualisationAssetUpdateNewFile{" +
               "path=" + path +
               ", resourceKey=" + resourceKey +
               '}';
    }
}
