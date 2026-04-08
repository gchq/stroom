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

@Description(
        "Parameter for VisualisationAssetResource.saveAs()"
)
@JsonPropertyOrder({
        "toOwnerDocId",
        "updatedContentPath",
        "updatedContent"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssetSaveAsParameters {

    @JsonProperty
    private final String toOwnerDocId;

    @JsonProperty
    private final String updatedContentPath;

    @JsonProperty
    private final byte[] updatedContent;

    /**
     * @param toOwnerDocId Must not be null.
     * @param updatedContentPath Null if no content is being updated
     * @param updatedContent Null if no content is being updated
     */
    @JsonCreator
    public VisualisationAssetSaveAsParameters(
            @JsonProperty("toOwnerDocId") final String toOwnerDocId,
            @JsonProperty("updatedContentPath") final String updatedContentPath,
            @JsonProperty("updatedContent") final byte[] updatedContent) {
        this.toOwnerDocId = toOwnerDocId;
        this.updatedContentPath = updatedContentPath;
        this.updatedContent = updatedContent;
    }

    public String getToOwnerDocId() {
        return toOwnerDocId;
    }

    public String getUpdatedContentPath() {
        return updatedContentPath;
    }

    public byte[] getUpdatedContent() {
        return updatedContent;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAssetSaveAsParameters that = (VisualisationAssetSaveAsParameters) o;
        return Objects.equals(toOwnerDocId, that.toOwnerDocId) && Objects.equals(updatedContentPath,
                that.updatedContentPath) && Objects.deepEquals(updatedContent, that.updatedContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toOwnerDocId, updatedContentPath, Arrays.hashCode(updatedContent));
    }
}
