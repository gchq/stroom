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


package stroom.gwt.workbench.client.app.gin.dashboard;

import stroom.ai.client.component.AskStroomAiController;
import stroom.dashboard.client.component.DashboardEditor;
import stroom.dashboard.client.main.DashboardPaneTypes;
import stroom.data.client.DataDisplaySupport;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.hyperlink.client.HyperlinkOpener;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the dashboard batch's stories (`App/Editors/DashboardEditor`, the
/// dashboard table's expression editor and `App/Dashboard/TableFilterSettings`), creating Stroom's
/// controllers and components as Stroom's GIN modules do, as `AppScreenGinjector` does for the pilot.
/// Create one each time the story renders: its singletons (e.g. the dashboard's
/// `PaneRegistry`) belong to that rendering's harness.
///
/// It reuses the query batch's mirrors of the modules a dashboard's components share with
/// StroomQL queries (the dashboard Table, Query and Vis modules, pipelines, annotations, streams)
/// and the editors batch's mirror of the Ask Stroom AI module, and adds a mirror of Stroom's
/// `DashboardModule`.
///
/// A dashboard creates its panes through the `PaneRegistry`, which Stroom's `DashboardPaneTypes`
/// (an eager singleton in Stroom) fills: get it before reading a dashboard
/// (`DashboardSupport.registerComponentTypes`).
@GinModules({
        DashboardScreenModule.class,
})
public interface DashboardScreenGinjector extends ScreenGinjector {

    /// @return A dashboard's editor tab (Dashboard, Documentation and Permissions), as
    /// `DashboardPlugin` creates it.
    DashboardEditor getDashboardEditor();

    /// @return The 'Ask Stroom AI' chat, shown by firing `AskStroomAiEvent`.
    AskStroomAiController getAskStroomAiController();

    /// @return Stroom's handler of `HyperlinkEvent`s (a table's links), which Stroom creates eagerly.
    HyperlinkOpener getHyperlinkOpener();

    /// @return Stroom's handler of `ShowDataEvent`s (a data link's dialog), which Stroom creates
    /// eagerly.
    DataDisplaySupport getDataDisplaySupport();

    /// @return The registrar of the dashboard's pane types, which registers them in the
    /// `PaneRegistry` as it is created.
    DashboardPaneTypes getDashboardPaneTypes();
}
