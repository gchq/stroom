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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Wraps the byte[] of content for VisualisationAssetResource.updateDelete().
 */
@Description(
        "Parameter for VisualisationAssetResource.updateDelete()"
)
@JsonPropertyOrder({
        "path",
        "folder"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssetUpdateDelete {
    @JsonProperty
    private final String path;

    @JsonProperty
    private final Boolean folder;

    @JsonCreator
    public VisualisationAssetUpdateDelete(
            @JsonProperty("path") final String path,
            @JsonProperty("folder") final Boolean folder) {
        this.path = path;
        this.folder = folder;
    }

    public String getPath() {
        return path;
    }

    public Boolean isFolder() {
        return folder;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAssetUpdateDelete that = (VisualisationAssetUpdateDelete) o;
        return Objects.equals(path, that.path) && Objects.equals(folder, that.folder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, folder);
    }

    @Override
    public String toString() {
        return "VisualisationAssetUpdateDelete{" +
               "path='" + path + '\'' +
               ", folder=" + folder +
               '}';
    }
}
