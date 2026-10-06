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

import stroom.ai.client.AskStroomAiPresenter;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPlugin;
import stroom.dashboard.client.input.KeyValueInputPlugin;
import stroom.dashboard.client.input.ListInputPlugin;
import stroom.dashboard.client.input.TableFilterPlugin;
import stroom.dashboard.client.input.TextInputPlugin;
import stroom.dashboard.client.main.DashboardSuperPresenter;
import stroom.dashboard.client.query.QueryPlugin;
import stroom.dashboard.client.table.TablePlugin;
import stroom.dashboard.client.text.TextPlugin;
import stroom.dashboard.client.vis.VisPlugin;
import stroom.data.client.presenter.DataDisplaySupport;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.MonitoringScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.gin.editors.AskStroomAIScreenModule;
import stroom.gwt.workbench.client.app.gin.query.ActivityScreenModule;
import stroom.gwt.workbench.client.app.gin.query.AlertScreenModule;
import stroom.gwt.workbench.client.app.gin.query.AnnotationScreenModule;
import stroom.gwt.workbench.client.app.gin.query.DashboardQueryScreenModule;
import stroom.gwt.workbench.client.app.gin.query.PipelineScreenModule;
import stroom.gwt.workbench.client.app.gin.query.QueryExtrasScreenModule;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenModule;
import stroom.gwt.workbench.client.app.gin.query.StreamStoreScreenModule;
import stroom.gwt.workbench.client.app.gin.query.TableScreenModule;
import stroom.gwt.workbench.client.app.gin.query.VisScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the dashboard batch's stories (`App/Editors/DashboardEditor`, the
/// dashboard table's expression editor and `App/Dashboard/TableFilterSettings`), creating Stroom's
/// presenters and views as Stroom's GIN modules do, as `AppScreenGinjector` does for the pilot.
/// Create one each time the story renders: its singletons (e.g. the dashboard's
/// `ComponentRegistry`) belong to that rendering's harness.
///
/// It reuses the query batch's mirrors of the modules a dashboard's components share with
/// StroomQL queries (the dashboard Table, Query and Vis modules, pipelines, annotations, streams)
/// and the editors batch's mirror of the Ask Stroom AI module, and adds mirrors of Stroom's
/// `DashboardModule`, `EmbeddedQueryModule`, `InputModule` and `TextModule`.
///
/// A dashboard creates its components through the `ComponentRegistry`, which Stroom's component
/// plugins (eager singletons in Stroom) fill: get every plugin before reading a dashboard
/// (`DashboardStories.registerComponentTypes`).
@GinModules({
        ScreenViewsModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        SecurityScreenModule.class,
        MonitoringScreenModule.class,
        TaskScreenModule.class,
        PipelineScreenModule.class,
        QueryScreenModule.class,
        TableScreenModule.class,
        AnnotationScreenModule.class,
        StreamStoreScreenModule.class,
        DashboardQueryScreenModule.class,
        ActivityScreenModule.class,
        AlertScreenModule.class,
        QueryExtrasScreenModule.class,
        VisScreenModule.class,
        AskStroomAIScreenModule.class,
        DashboardScreenModule.class,
        EmbeddedQueryScreenModule.class,
        InputScreenModule.class,
        TextScreenModule.class,
})
public interface DashboardScreenGinjector extends ScreenGinjector {

    /// @return A dashboard's editor tab (Dashboard, Documentation and Permissions), as
    /// `DashboardPlugin` creates it.
    DashboardSuperPresenter getDashboardSuperPresenter();

    /// @return The 'Ask Stroom AI' chat, shown by firing `AskStroomAiEvent`.
    AskStroomAiPresenter getAskStroomAiPresenter();

    /// @return Stroom's handler of `HyperlinkEvent`s (a table's links), which Stroom creates eagerly.
    HyperlinkEventHandlerImpl getHyperlinkEventHandler();

    /// @return Stroom's handler of `ShowDataEvent`s (a data link's dialog), which Stroom creates
    /// eagerly.
    DataDisplaySupport getDataDisplaySupport();

    // The component plugins, which register their component types as they are created

    /// @return The plugin of Query components.
    QueryPlugin getQueryPlugin();

    /// @return The plugin of Table components.
    TablePlugin getTablePlugin();

    /// @return The plugin of Visualisation components.
    VisPlugin getVisPlugin();

    /// @return The plugin of Text components.
    TextPlugin getTextPlugin();

    /// @return The plugin of Embedded Query components.
    EmbeddedQueryPlugin getEmbeddedQueryPlugin();

    /// @return The plugin of Key/Value Input components.
    KeyValueInputPlugin getKeyValueInputPlugin();

    /// @return The plugin of List Input components.
    ListInputPlugin getListInputPlugin();

    /// @return The plugin of Text Input components.
    TextInputPlugin getTextInputPlugin();

    /// @return The plugin of Table Filter components.
    TableFilterPlugin getTableFilterPlugin();
}
