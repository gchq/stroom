/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// What a pipeline's stepping screen shows, beyond the text of the selected element's panes (which are sent as
/// the snapshot's parts): where the step is (feed, stream, part and record), the pipeline's elements in order, which
/// one is selected, and the step's errors.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiSteppingContext {

    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final Long metaId;
    @JsonProperty
    private final String streamType;
    @JsonProperty
    private final String childStreamType;
    @JsonProperty
    private final Long partNo;
    @JsonProperty
    private final Long recordNo;
    @JsonProperty
    private final String selectedElementId;
    @JsonProperty
    private final List<AiSteppingElement> elements;
    @JsonProperty
    private final List<String> generalErrors;

    /// @param feedName          The feed of the stream being stepped, or null.
    /// @param metaId            The id of the stream being stepped, or null if none is.
    /// @param streamType        The stream's type, e.g. `Raw Events`, or null.
    /// @param childStreamType   The child stream type being stepped (e.g. `Context`), or null.
    /// @param partNo            The one based part of the stream, or null.
    /// @param recordNo          The one based record within the part, or null.
    /// @param selectedElementId The id of the element selected, or null if the source is.
    /// @param elements          The pipeline's elements, in order from the source.
    /// @param generalErrors     Errors from the step that aren't about one element.
    @JsonCreator
    public AiSteppingContext(@JsonProperty("feedName") final String feedName,
                             @JsonProperty("metaId") final Long metaId,
                             @JsonProperty("streamType") final String streamType,
                             @JsonProperty("childStreamType") final String childStreamType,
                             @JsonProperty("partNo") final Long partNo,
                             @JsonProperty("recordNo") final Long recordNo,
                             @JsonProperty("selectedElementId") final String selectedElementId,
                             @JsonProperty("elements") final List<AiSteppingElement> elements,
                             @JsonProperty("generalErrors") final List<String> generalErrors) {
        this.feedName = feedName;
        this.metaId = metaId;
        this.streamType = streamType;
        this.childStreamType = childStreamType;
        this.partNo = partNo;
        this.recordNo = recordNo;
        this.selectedElementId = selectedElementId;
        this.elements = elements == null
                ? null
                : new ArrayList<>(elements);
        this.generalErrors = generalErrors == null
                ? null
                : new ArrayList<>(generalErrors);
    }

    /// @return The feed of the stream being stepped, or null.
    public String getFeedName() {
        return feedName;
    }

    /// @return The id of the stream being stepped, or null if none is.
    public Long getMetaId() {
        return metaId;
    }

    /// @return The stream's type, or null.
    public String getStreamType() {
        return streamType;
    }

    /// @return The child stream type being stepped, or null.
    public String getChildStreamType() {
        return childStreamType;
    }

    /// @return The one based part of the stream, or null.
    public Long getPartNo() {
        return partNo;
    }

    /// @return The one based record within the part, or null.
    public Long getRecordNo() {
        return recordNo;
    }

    /// @return The id of the element selected, or null if the source is.
    public String getSelectedElementId() {
        return selectedElementId;
    }

    /// @return The pipeline's elements, in order from the source.
    public List<AiSteppingElement> getElements() {
        return elements == null
                ? Collections.emptyList()
                : elements;
    }

    /// @return Errors from the step that aren't about one element.
    public List<String> getGeneralErrors() {
        return generalErrors == null
                ? Collections.emptyList()
                : generalErrors;
    }

    /// @return The selected element, or null if the source (or nothing) is selected.
    @JsonIgnore
    public AiSteppingElement getSelectedElement() {
        return findElement(selectedElementId);
    }

    /// @param id An element's id.
    /// @return The element with the id, or null if there is none.
    public AiSteppingElement findElement(final String id) {
        if (id == null) {
            return null;
        }
        for (final AiSteppingElement element : getElements()) {
            if (id.equals(element.getId())) {
                return element;
            }
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiSteppingContext that = (AiSteppingContext) o;
        return Objects.equals(feedName, that.feedName)
               && Objects.equals(metaId, that.metaId)
               && Objects.equals(streamType, that.streamType)
               && Objects.equals(childStreamType, that.childStreamType)
               && Objects.equals(partNo, that.partNo)
               && Objects.equals(recordNo, that.recordNo)
               && Objects.equals(selectedElementId, that.selectedElementId)
               && Objects.equals(getElements(), that.getElements())
               && Objects.equals(getGeneralErrors(), that.getGeneralErrors());
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, metaId, streamType, childStreamType, partNo, recordNo, selectedElementId,
                getElements(), getGeneralErrors());
    }

    @Override
    public String toString() {
        return "AiSteppingContext{feedName='" + feedName + "', metaId=" + metaId + ", partNo=" + partNo
               + ", recordNo=" + recordNo + ", selectedElementId='" + selectedElementId + "', elements="
               + elements + "}";
    }
}
