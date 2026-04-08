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

import java.util.Arrays;
import java.util.Objects;

/**
 * Wraps the byte[] of content for VisualisationAssetResource.updateContent().
 */
@Description(
        "Parameter for VisualisationAssetResource.updateContent()"
)
@JsonPropertyOrder({
        "path",
        "content"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssetUpdateContent {

    @JsonProperty
    private final String path;

    @JsonProperty
    private final byte[] content;

    @JsonCreator
    public VisualisationAssetUpdateContent(
            @JsonProperty("path") final String path,
            @JsonProperty("content") final byte[] content) {
        this.path = path;
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAssetUpdateContent that = (VisualisationAssetUpdateContent) o;
        return Objects.equals(path, that.path)
               && Objects.deepEquals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path) + Arrays.hashCode(content);
    }

}
