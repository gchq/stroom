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

import stroom.dashboard.shared.DashboardConfig;
import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// What happened when the user clicked a button an MCP tool's result offered (see `UserActions`).
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiUserActionResult {

    @JsonProperty
    private final boolean done;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final DocRef openDocument;
    @JsonProperty
    private final String createQueryName;
    @JsonProperty
    private final String createQueryText;
    @JsonProperty
    private final String createDashboardName;
    @JsonProperty
    private final DashboardConfig createDashboardConfig;
    @JsonProperty
    private final AiCreateVisualisation createVisualisation;

    /// @param done         True if the action was done.
    /// @param message      What happened, or why it was not done.
    /// @param openDocument A document for the browser to open, or null.
    public AiUserActionResult(final Boolean done,
                              final String message,
                              final DocRef openDocument) {
        this(done, message, openDocument, null, null, null, null, null);
    }

    /// @param done            True if the action was done.
    /// @param message         What happened, or why it was not done.
    /// @param openDocument    A document for the browser to open, or null.
    /// @param createQueryName For a create_query action, the name to suggest in Stroom's New Query dialog, or null.
    /// @param createQueryText For a create_query action, the StroomQL to put in the new query once created, or
    ///                        null.
    public AiUserActionResult(final Boolean done,
                              final String message,
                              final DocRef openDocument,
                              final String createQueryName,
                              final String createQueryText) {
        this(done, message, openDocument, createQueryName, createQueryText, null, null, null);
    }

    /// @param done            True if the action was done.
    /// @param message         What happened, or why it was not done.
    /// @param openDocument    A document for the browser to open, or null.
    /// @param createQueryName For a create_query action, the name to suggest in Stroom's New Query dialog, or null.
    /// @param createQueryText For a create_query action, the StroomQL to put in the new query once created, or
    ///                        null.
    /// @param createDashboardName   For a create_dashboard action, the name to suggest in Stroom's New Dashboard
    ///                              dialog, or null.
    /// @param createDashboardConfig For a create_dashboard action, the settings to give the new dashboard once
    ///                              created, or null.
    /// @param createVisualisation   For a create_visualisation action, what to create and where to wire it up,
    ///                              or null.
    @JsonCreator
    public AiUserActionResult(@JsonProperty("done") final Boolean done,
                              @JsonProperty("message") final String message,
                              @JsonProperty("openDocument") final DocRef openDocument,
                              @JsonProperty("createQueryName") final String createQueryName,
                              @JsonProperty("createQueryText") final String createQueryText,
                              @JsonProperty("createDashboardName") final String createDashboardName,
                              @JsonProperty("createDashboardConfig") final DashboardConfig createDashboardConfig,
                              @JsonProperty("createVisualisation") final AiCreateVisualisation createVisualisation) {
        this.done = Objects.requireNonNullElse(done, false);
        this.message = message;
        this.openDocument = openDocument;
        this.createQueryName = createQueryName;
        this.createQueryText = createQueryText;
        this.createDashboardName = createDashboardName;
        this.createDashboardConfig = createDashboardConfig;
        this.createVisualisation = createVisualisation;
    }

    /// @param name   The name to suggest in Stroom's New Dashboard dialog.
    /// @param config The settings to give the new dashboard once created.
    /// @return The result of a create_dashboard action that can be done.
    public static AiUserActionResult createDashboard(final String name, final DashboardConfig config) {
        return new AiUserActionResult(true, null, null, null, null, name, config, null);
    }

    /// @param createVisualisation What to create and where to wire it up.
    /// @return The result of a create_visualisation action that can be done.
    public static AiUserActionResult createVisualisation(final AiCreateVisualisation createVisualisation) {
        return new AiUserActionResult(true, null, null, null, null, null, null, createVisualisation);
    }

    /// @return True if the action was done.
    public boolean isDone() {
        return done;
    }

    /// @return What happened, or why it was not done.
    public String getMessage() {
        return message;
    }

    /// @return A document for the browser to open, or null.
    public DocRef getOpenDocument() {
        return openDocument;
    }

    /// @return For a create_query action, the name to suggest in Stroom's New Query dialog, or null.
    public String getCreateQueryName() {
        return createQueryName;
    }

    /// @return For a create_query action, the StroomQL to put in the new query once created, or null.
    public String getCreateQueryText() {
        return createQueryText;
    }

    /// @return For a create_dashboard action, the name to suggest in Stroom's New Dashboard dialog, or null.
    public String getCreateDashboardName() {
        return createDashboardName;
    }

    /// @return For a create_dashboard action, the settings to give the new dashboard once created, or null.
    public DashboardConfig getCreateDashboardConfig() {
        return createDashboardConfig;
    }

    /// @return For a create_visualisation action, what to create and where to wire it up, or null.
    public AiCreateVisualisation getCreateVisualisation() {
        return createVisualisation;
    }
}
