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
 * Wraps data together for VisualisationAssetResource.updateRename().
 */
@Description(
        "Parameter for VisualisationAssetResource.updateRename"
)
@JsonPropertyOrder({
        "oldPath",
        "newPath",
        "folder"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssetUpdateRename {
    @JsonProperty
    private final String oldPath;

    @JsonProperty
    private final String newPath;

    @JsonProperty
    private final Boolean folder;

    @JsonCreator
    public VisualisationAssetUpdateRename(
            @JsonProperty("oldPath") final String oldPath,
            @JsonProperty("newPath") final String newPath,
            @JsonProperty("folder") final Boolean folder) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.folder = folder;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public Boolean isFolder() {
        return folder;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAssetUpdateRename that = (VisualisationAssetUpdateRename) o;
        return Objects.equals(folder, that.folder)
               && Objects.equals(oldPath, that.oldPath)
               && Objects.equals(newPath, that.newPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldPath, newPath, folder);
    }

    @Override
    public String toString() {
        return "VisualisationAssetUpdateRename{" +
               "oldPath='" + oldPath +
               "', newPath='" + newPath +
               "', isFolder=" + folder +
               '}';
    }
}
