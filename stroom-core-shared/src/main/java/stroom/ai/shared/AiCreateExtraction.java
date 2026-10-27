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
import stroom.explorer.shared.ExplorerNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/// What the browser needs to make a dashboard table's search extraction the agent proposed, once the user clicks
/// its `create_extraction_pipeline` button: the name and folder to suggest for the new pipeline and XSLT, and what
/// will be written, for the user to confirm before anything is (see `AskStroomAiResource.writeExtraction`). Nothing
/// is written yet.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiCreateExtraction {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final ExplorerNode folder;
    @JsonProperty
    private final DocRef dashboard;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final String componentName;
    @JsonProperty
    private final String base;
    @JsonProperty
    private final String xslt;
    @JsonProperty
    private final List<String> fields;
    @JsonProperty
    private final List<String> dashboardChanges;
    @JsonProperty
    private final String xsltDescription;
    @JsonProperty
    private final String pipelineDescription;
    @JsonProperty
    private final DocRef tablePipeline;
    @JsonProperty
    private final String contentHash;

    /// @param name             The name to suggest for the new pipeline and its XSLT.
    /// @param folder           The folder to suggest for them: the dashboard's.
    /// @param dashboard        The dashboard.
    /// @param componentId      The table's component id.
    /// @param componentName    The table's name.
    /// @param base             What the pipeline is built from, in words, e.g. a child of a template.
    /// @param xslt             The XSLT that will be written.
    /// @param fields           Each field extracted, in words.
    /// @param dashboardChanges Each change made to the table as the pipeline is made (unsaved), in words.
    /// @param xsltDescription     The description the XSLT is given.
    /// @param pipelineDescription The description the pipeline is given.
    /// @param tablePipeline    The extraction pipeline the table had when this was proposed (which the new one is a
    ///                         child of), or null for none: nothing is written if it has another now.
    /// @param contentHash      The hash of what will be written, which the write must be given, so nothing else
    ///                         is written.
    @JsonCreator
    public AiCreateExtraction(@JsonProperty("name") final String name,
                              @JsonProperty("folder") final ExplorerNode folder,
                              @JsonProperty("dashboard") final DocRef dashboard,
                              @JsonProperty("componentId") final String componentId,
                              @JsonProperty("componentName") final String componentName,
                              @JsonProperty("base") final String base,
                              @JsonProperty("xslt") final String xslt,
                              @JsonProperty("fields") final List<String> fields,
                              @JsonProperty("dashboardChanges") final List<String> dashboardChanges,
                              @JsonProperty("xsltDescription") final String xsltDescription,
                              @JsonProperty("pipelineDescription") final String pipelineDescription,
                              @JsonProperty("tablePipeline") final DocRef tablePipeline,
                              @JsonProperty("contentHash") final String contentHash) {
        this.name = name;
        this.folder = folder;
        this.dashboard = dashboard;
        this.componentId = componentId;
        this.componentName = componentName;
        this.base = base;
        this.xslt = xslt;
        this.fields = fields;
        this.dashboardChanges = dashboardChanges;
        this.xsltDescription = xsltDescription;
        this.pipelineDescription = pipelineDescription;
        this.tablePipeline = tablePipeline;
        this.contentHash = contentHash;
    }

    /// @return The name to suggest for the new pipeline and its XSLT.
    public String getName() {
        return name;
    }

    /// @return The folder to suggest for them.
    public ExplorerNode getFolder() {
        return folder;
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

    /// @return What the pipeline is built from, in words.
    public String getBase() {
        return base;
    }

    /// @return The XSLT that will be written.
    public String getXslt() {
        return xslt;
    }

    /// @return Each field extracted, in words.
    public List<String> getFields() {
        return fields;
    }

    /// @return Each change made to the table as the pipeline is made, in words.
    public List<String> getDashboardChanges() {
        return dashboardChanges;
    }

    /// @return The description the XSLT is given.
    public String getXsltDescription() {
        return xsltDescription;
    }

    /// @return The description the pipeline is given.
    public String getPipelineDescription() {
        return pipelineDescription;
    }

    /// @return The extraction pipeline the table had when this was proposed, or null for none.
    public DocRef getTablePipeline() {
        return tablePipeline;
    }

    /// @return The hash of what will be written.
    public String getContentHash() {
        return contentHash;
    }
}
