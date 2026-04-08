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
 * Returns content from VisualisationAssetResource.getDraftContent().
 */
@Description(
        "Returns content from VisualisationAssetResource.getDraftContent()"
)
@JsonPropertyOrder({
        "content",
        "editorMode"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssetContent {

    @JsonProperty
    private final String content;

    @JsonProperty
    private final String editorMode;

    @JsonCreator
    public VisualisationAssetContent(@JsonProperty("content") final String content,
                                     @JsonProperty("editorMode") final String editorMode) {
        this.content = content;
        this.editorMode = editorMode;
    }

    public String getContent() {
        return content;
    }

    public String getEditorMode() {
        return editorMode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAssetContent that = (VisualisationAssetContent) o;
        return Objects.equals(content, that.content) && Objects.equals(editorMode, that.editorMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, editorMode);
    }

    @Override
    public String toString() {
        return "VisualisationAssetContent{" +
               "content='" + content + '\'' +
               ", editorMode='" + editorMode + '\'' +
               '}';
    }
}
