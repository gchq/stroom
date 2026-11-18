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

package stroom.dashboard.client.vis;

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

/// Fired when a visualisation reports how it rendered (see `stroom.report` in `vis.js`), e.g. what it
/// drew, the rows it dropped or why it failed.
public class VisRenderReportEvent extends GwtEvent<VisRenderReportEvent.Handler> {

    private static Type<Handler> TYPE;

    private final DocRef visualisation;
    private final DocRef dashboard;
    private final String componentId;
    private final String reportJson;

    private VisRenderReportEvent(final DocRef visualisation,
                                 final DocRef dashboard,
                                 final String componentId,
                                 final String reportJson) {
        this.visualisation = visualisation;
        this.dashboard = dashboard;
        this.componentId = componentId;
        this.reportJson = Objects.requireNonNull(reportJson);
    }

    /// @return The type of this event.
    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    /// Fires the event.
    ///
    /// @param handlers      Where to fire it.
    /// @param visualisation The Visualisation doc that rendered, if known.
    /// @param dashboard     The dashboard holding the visualisation, or null if it is not on a dashboard,
    ///                      e.g. a StroomQL query's result.
    /// @param componentId   The dashboard component's id, or null if it is not on a dashboard.
    /// @param reportJson    The report, as JSON.
    public static void fire(final HasHandlers handlers,
                            final DocRef visualisation,
                            final DocRef dashboard,
                            final String componentId,
                            final String reportJson) {
        handlers.fireEvent(new VisRenderReportEvent(visualisation, dashboard, componentId, reportJson));
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onReport(this);
    }

    /// @return The Visualisation doc that rendered, if known.
    public DocRef getVisualisation() {
        return visualisation;
    }

    /// @return The dashboard holding the visualisation, or null.
    public DocRef getDashboard() {
        return dashboard;
    }

    /// @return The dashboard component's id, or null.
    public String getComponentId() {
        return componentId;
    }

    /// @return The report, as JSON.
    public String getReportJson() {
        return reportJson;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onReport(VisRenderReportEvent event);
    }
}
