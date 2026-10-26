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

/// What the browser needs to create a Visualisation the agent generated, once the user clicks its
/// `create_visualisation` button: the name and folder to suggest in Stroom's New Visualisation dialog, what to put
/// in the new Visualisation, and where to wire it up (a vis component on a dashboard, or a StroomQL query's `show`
/// clause). Nothing is created until the user creates it in the dialog.
///
/// For a chart that replaces one Stroom AI generated earlier, [#getReplaced()] is the Visualisation, already
/// rewritten and renamed when the user clicked the button: the browser shows it in place of the old chart, in the
/// same vis component ([#getReplaceComponentId()]), with no dialog.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiCreateVisualisation {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final ExplorerNode folder;
    @JsonProperty
    private final String functionName;
    @JsonProperty
    private final DocRef scriptRef;
    @JsonProperty
    private final String settings;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final DocRef target;
    @JsonProperty
    private final String tableId;
    @JsonProperty
    private final String tableName;
    @JsonProperty
    private final String componentName;
    @JsonProperty
    private final String componentSettings;
    @JsonProperty
    private final String placement;
    @JsonProperty
    private final String snapshotHash;
    @JsonProperty
    private final String showArguments;
    @JsonProperty
    private final String baseQuery;
    @JsonProperty
    private final DocRef replaced;
    @JsonProperty
    private final String replaceComponentId;
    @JsonProperty
    private final String tableChanges;
    @JsonProperty
    private final String filterChanges;

    /// @param name              The name to suggest for the new Visualisation.
    /// @param folder            The folder to suggest for it: the dashboard's or query's folder.
    /// @param functionName      Its vis class.
    /// @param scriptRef         Its script.
    /// @param settings          Its settings JSON.
    /// @param description       Its description.
    /// @param target            The dashboard or query it is for.
    /// @param tableId           For a dashboard, the component id of the table it draws, else null.
    /// @param tableName         For a dashboard, the name of the table it draws, else null.
    /// @param componentName     For a dashboard, the name of the new vis component, else null.
    /// @param componentSettings For a dashboard, the new vis component's settings JSON, else null.
    /// @param placement         For a dashboard, `below` the table or a `tab` beside it, else null.
    /// @param snapshotHash      For a dashboard, the hash of its description when the chart was planned; else null.
    /// @param showArguments     For a StroomQL query, the arguments of its `show` clause, e.g.
    ///                          `source = "Sender", target = "Recipient"`, else null.
    /// @param baseQuery         For a StroomQL query, its text when the chart was planned, so the `show` clause
    ///                          is only put in a query that hasn't changed since; else null.
    /// @param replaced          For a chart replacing one Stroom AI generated, the Visualisation rewritten for it,
    ///                          with its new name; else null.
    /// @param replaceComponentId For a chart replacing another, the id of the vis component showing it; else null.
    /// @param tableChanges      For a chart of a table it adds, the changes that add the table (beside the one with
    ///                          tableId), as a JSON array of the dashboard changes the browser applies; else null.
    /// @param filterChanges     For a chart whose clicks filter the dashboard, the selection filter's change, as a
    ///                          JSON array of the dashboard changes the browser applies, naming the chart by
    ///                          componentName; else null.
    @JsonCreator
    public AiCreateVisualisation(@JsonProperty("name") final String name,
                                 @JsonProperty("folder") final ExplorerNode folder,
                                 @JsonProperty("functionName") final String functionName,
                                 @JsonProperty("scriptRef") final DocRef scriptRef,
                                 @JsonProperty("settings") final String settings,
                                 @JsonProperty("description") final String description,
                                 @JsonProperty("target") final DocRef target,
                                 @JsonProperty("tableId") final String tableId,
                                 @JsonProperty("tableName") final String tableName,
                                 @JsonProperty("componentName") final String componentName,
                                 @JsonProperty("componentSettings") final String componentSettings,
                                 @JsonProperty("placement") final String placement,
                                 @JsonProperty("snapshotHash") final String snapshotHash,
                                 @JsonProperty("showArguments") final String showArguments,
                                 @JsonProperty("baseQuery") final String baseQuery,
                                 @JsonProperty("replaced") final DocRef replaced,
                                 @JsonProperty("replaceComponentId") final String replaceComponentId,
                                 @JsonProperty("tableChanges") final String tableChanges,
                                 @JsonProperty("filterChanges") final String filterChanges) {
        this.name = name;
        this.folder = folder;
        this.functionName = functionName;
        this.scriptRef = scriptRef;
        this.settings = settings;
        this.description = description;
        this.target = target;
        this.tableId = tableId;
        this.tableName = tableName;
        this.componentName = componentName;
        this.componentSettings = componentSettings;
        this.placement = placement;
        this.snapshotHash = snapshotHash;
        this.showArguments = showArguments;
        this.baseQuery = baseQuery;
        this.replaced = replaced;
        this.replaceComponentId = replaceComponentId;
        this.tableChanges = tableChanges;
        this.filterChanges = filterChanges;
    }

    /// @return The name to suggest for the new Visualisation.
    public String getName() {
        return name;
    }

    /// @return The folder to suggest for it, or null.
    public ExplorerNode getFolder() {
        return folder;
    }

    /// @return Its vis class.
    public String getFunctionName() {
        return functionName;
    }

    /// @return Its script.
    public DocRef getScriptRef() {
        return scriptRef;
    }

    /// @return Its settings JSON.
    public String getSettings() {
        return settings;
    }

    /// @return Its description.
    public String getDescription() {
        return description;
    }

    /// @return The dashboard or query it is for.
    public DocRef getTarget() {
        return target;
    }

    /// @return For a dashboard, the component id of the table it draws, else null.
    public String getTableId() {
        return tableId;
    }

    /// @return For a dashboard, the name of the table it draws, else null.
    public String getTableName() {
        return tableName;
    }

    /// @return For a dashboard, the name of the new vis component, else null.
    public String getComponentName() {
        return componentName;
    }

    /// @return For a dashboard, the new vis component's settings JSON, else null.
    public String getComponentSettings() {
        return componentSettings;
    }

    /// @return For a dashboard, `below` or `tab`, else null.
    public String getPlacement() {
        return placement;
    }

    /// @return For a dashboard, the hash of its description when the chart was planned, else null.
    public String getSnapshotHash() {
        return snapshotHash;
    }

    /// @return For a StroomQL query, the arguments of its `show` clause, else null.
    public String getShowArguments() {
        return showArguments;
    }

    /// @return For a StroomQL query, its text when the chart was planned, else null.
    public String getBaseQuery() {
        return baseQuery;
    }

    /// @return True if it is for a StroomQL query rather than a dashboard.
    public boolean isForQuery() {
        return showArguments != null;
    }

    /// @return For a chart replacing one Stroom AI generated, the Visualisation rewritten for it, with its new
    /// name; else null.
    public DocRef getReplaced() {
        return replaced;
    }

    /// @return For a chart replacing another, the id of the vis component showing it; else null.
    public String getReplaceComponentId() {
        return replaceComponentId;
    }

    /// @return For a chart of a table it adds, the changes that add the table, as a JSON array; else null.
    public String getTableChanges() {
        return tableChanges;
    }

    /// @return For a chart whose clicks filter the dashboard, its selection filter's change, as a JSON array; else
    /// null.
    public String getFilterChanges() {
        return filterChanges;
    }
}
