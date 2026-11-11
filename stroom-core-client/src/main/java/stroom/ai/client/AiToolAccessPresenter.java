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

package stroom.ai.client;

import stroom.ai.client.AiToolAccessPresenter.AiToolAccessView;
import stroom.ai.shared.AiToolPolicyResource;
import stroom.ai.shared.AiToolSummary;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

/// The effective MCP tool access of a user or group: what every enabled tool policy covering it grants it
/// together, by category and tool, with the policies each permission comes from. Shown on the MCP Tool Policies
/// screen, for a user looked up, and on a user's own page.
public class AiToolAccessPresenter extends MyPresenterWidget<AiToolAccessView> {

    private static final AiToolPolicyResource RESOURCE = GWT.create(AiToolPolicyResource.class);

    private final RestFactory restFactory;
    private final AiToolAccessPanel panel;

    // The user or group shown, or null if none is.
    private UserRef userRef;
    // Stroom's tools, or null until they have been fetched.
    private List<AiToolSummary> tools;

    /// @param eventBus    The event bus.
    /// @param view        Holds the panel.
    /// @param restFactory For calling the tool policy API.
    @Inject
    public AiToolAccessPresenter(final EventBus eventBus,
                                 final AiToolAccessView view,
                                 final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        panel = AiToolAccessPanel.forEffectiveAccess(this);
        panel.setHeading(null);
        view.setPanel(panel);
    }

    /// @param text The panel's heading, or null for none (as at first).
    public void setHeading(final String text) {
        panel.setHeading(text);
    }

    /// @param widget Put in a row of its own below the heading row.
    public void addRow(final Widget widget) {
        panel.addRow(widget);
    }

    /// @return The user or group shown, or null if none is.
    public UserRef getUserRef() {
        return userRef;
    }

    /// Shows a user's or group's effective tool access.
    ///
    /// @param userRef The user or group, or null to show none.
    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        refresh();
    }

    /// Fetches the access again, e.g. as a policy may have changed it.
    public void refresh() {
        if (tools == null) {
            fetchTools();
        }
        if (userRef == null) {
            panel.setEffectiveGrants(null);
            return;
        }
        final UserRef fetching = userRef;
        restFactory
                .create(RESOURCE)
                .method(res -> res.effectiveGrants(fetching))
                .onSuccess(grants -> {
                    // Unless another has been chosen meanwhile.
                    if (fetching.equals(userRef)) {
                        panel.setEffectiveGrants(NullSafe.list(grants));
                    }
                })
                .onFailure(restError -> AlertEvent.fireError(
                        this, "Error fetching the MCP tool access of " + fetching.toDisplayString() + ": "
                              + restError.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    private void fetchTools() {
        restFactory
                .create(RESOURCE)
                .method(AiToolPolicyResource::listTools)
                .onSuccess(fetched -> {
                    tools = NullSafe.list(fetched);
                    panel.setTools(tools);
                })
                .onFailure(restError -> AlertEvent.fireError(
                        this, "Error fetching the tools: " + restError.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }


    // --------------------------------------------------------------------------------


    /// Holds the effective access panel.
    public interface AiToolAccessView extends View {

        /// @param panel The effective access panel.
        void setPanel(IsWidget panel);
    }
}
