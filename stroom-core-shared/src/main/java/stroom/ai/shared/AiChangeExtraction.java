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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/// What the browser needs to change a dashboard table's extraction pipeline in place, as the agent proposed, once
/// the user clicks its `change_extraction_pipeline` button: the change to the pipeline's XSLT, and the other
/// documents that use it, which change too, for the user to confirm before anything is written (see
/// `AskStroomAiResource.writeExtraction`). Nothing is written yet.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChangeExtraction {

    @JsonProperty
    private final DocRef dashboard;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final String componentName;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final DocRef xslt;
    @JsonProperty
    private final String currentXslt;
    @JsonProperty
    private final String proposedXslt;
    @JsonProperty
    private final String change;
    @JsonProperty
    private final List<String> usedBy;
    @JsonProperty
    private final List<String> dashboardChanges;
    @JsonProperty
    private final String contentHash;

    /// @param dashboard        The dashboard.
    /// @param componentId      The table's component id.
    /// @param componentName    The table's name.
    /// @param pipeline         The table's extraction pipeline.
    /// @param xslt             The pipeline's XSLT that is changed.
    /// @param currentXslt      The XSLT's code now.
    /// @param proposedXslt     The XSLT's code as it will be written.
    /// @param change           What the change does, in words.
    /// @param usedBy           The other documents that use the XSLT, directly or through a pipeline, which change
    ///                         too, in words.
    /// @param dashboardChanges Each change made to the table as the XSLT is written (unsaved), in words.
    /// @param contentHash      The hash of what will be written, which the write must be given, so nothing else
    ///                         is written.
    @JsonCreator
    public AiChangeExtraction(@JsonProperty("dashboard") final DocRef dashboard,
                              @JsonProperty("componentId") final String componentId,
                              @JsonProperty("componentName") final String componentName,
                              @JsonProperty("pipeline") final DocRef pipeline,
                              @JsonProperty("xslt") final DocRef xslt,
                              @JsonProperty("currentXslt") final String currentXslt,
                              @JsonProperty("proposedXslt") final String proposedXslt,
                              @JsonProperty("change") final String change,
                              @JsonProperty("usedBy") final List<String> usedBy,
                              @JsonProperty("dashboardChanges") final List<String> dashboardChanges,
                              @JsonProperty("contentHash") final String contentHash) {
        this.dashboard = dashboard;
        this.componentId = componentId;
        this.componentName = componentName;
        this.pipeline = pipeline;
        this.xslt = xslt;
        this.currentXslt = currentXslt;
        this.proposedXslt = proposedXslt;
        this.change = change;
        this.usedBy = usedBy;
        this.dashboardChanges = dashboardChanges;
        this.contentHash = contentHash;
    }

    /// @return The dashboard.
    public DocRef getDashboard() {
        return dashboard;
    }

    /// @return The table's component id.
    public String getComponentId() {
        return componentId;
    }

    /// @return The table's name.
    public String getComponentName() {
        return componentName;
    }

    /// @return The table's extraction pipeline.
    public DocRef getPipeline() {
        return pipeline;
    }

    /// @return The pipeline's XSLT that is changed.
    public DocRef getXslt() {
        return xslt;
    }

    /// @return The XSLT's code now.
    public String getCurrentXslt() {
        return currentXslt;
    }

    /// @return The XSLT's code as it will be written.
    public String getProposedXslt() {
        return proposedXslt;
    }

    /// @return What the change does, in words.
    public String getChange() {
        return change;
    }

    /// @return The other documents that use the XSLT, which change too, in words.
    public List<String> getUsedBy() {
        return usedBy;
    }

    /// @return Each change made to the table as the XSLT is written, in words.
    public List<String> getDashboardChanges() {
        return dashboardChanges;
    }

    /// @return The hash of what will be written.
    public String getContentHash() {
        return contentHash;
    }
}
