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


package stroom.gwt.workbench.client.app.gin.content;

import stroom.activity.client.CurrentActivity;
import stroom.activity.client.ManageActivityPresenter;
import stroom.annotation.client.AnnotationPresenter;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.AnnotationTagPresenter;
import stroom.annotation.client.BrowseAnnotationPresenter;
import stroom.annotation.client.FindAnnotationPresenter;
import stroom.cache.client.presenter.CachePresenter;
import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.core.client.ContentManager;
import stroom.data.store.impl.fs.client.presenter.FsVolumeGroupPresenter;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.explorer.client.presenter.FindPresenter;
import stroom.explorer.client.presenter.TabSessionManager;
import stroom.gwt.workbench.client.app.gin.CredentialsScreenModule;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.MonitoringScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.importexport.client.presenter.DependenciesInfoPresenter;
import stroom.importexport.client.presenter.DependenciesTabPresenter;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter;
import stroom.index.client.presenter.IndexVolumeGroupPresenter;
import stroom.monitoring.client.presenter.DatabaseTablesMonitoringPresenter;
import stroom.pathways.client.presenter.TracesPresenter;
import stroom.query.client.presenter.ResultStorePresenter;
import stroom.receive.content.client.presenter.ContentTemplateTabPresenter;
import stroom.xmlschema.client.XMLSchemaPlugin;

import com.google.gwt.inject.client.GinModules;

/// The `content` batch's [ScreenGinjector]: it creates the screens of the `App/Main` content,
/// annotation, activity and monitoring stories (presenters and their views) as Stroom's GIN
/// modules do, as `AppScreenGinjector` does for the pilot's screens. Create one each time the
/// story renders and give it to the harness with `ScreenHarness.Builder.injector(...)`.
///
/// Its modules are the shared [ScreenViewsModule] (and other shared mirrors in `app.gin`) plus
/// this package's mirrors of Stroom's modules.
@GinModules({
        ScreenViewsModule.class,
        CredentialsScreenModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        MonitoringScreenModule.class,
        SecurityScreenModule.class,
        TaskScreenModule.class,
        PipelineScreenModule.class,
        StreamStoreScreenModule.class,
        CacheScreenModule.class,
        ImportExportScreenModule.class,
        AnnotationScreenModule.class,
        ContentAppScreenModule.class,
        ActivityScreenModule.class,
        ContentExtrasScreenModule.class,
        ResultStoreScreenModule.class,
        ContentStoreScreenModule.class,
        IndexVolumeScreenModule.class,
        FsVolumeScreenModule.class,
        PathwaysScreenModule.class,
        PlanBScreenModule.class,
        ContentTemplateScreenModule.class,
        XMLSchemaScreenModule.class,
})
public interface ContentScreenGinjector extends ScreenGinjector {

    // Monitoring

    /// @return The 'Caches' tab.
    CachePresenter getCachePresenter();

    /// @return The 'Database Tables' tab.
    DatabaseTablesMonitoringPresenter getDatabaseTablesMonitoringPresenter();

    /// @return The 'Index Volumes' tab.
    IndexVolumeGroupPresenter getIndexVolumeGroupPresenter();

    /// @return The 'Data Volumes' tab.
    FsVolumeGroupPresenter getFsVolumeGroupPresenter();

    /// @return The 'Properties' tab.
    GlobalPropertyTabPresenter getGlobalPropertyTabPresenter();

    // Content

    /// @return The document tab pane, shown by registering it as the content events' handler.
    ContentTabPanePresenter getContentTabPanePresenter();

    /// @return The content manager (a singleton), which opens tabs in the tab pane.
    ContentManager getContentManager();

    /// @return The document plugin event manager (a singleton), which builds the tab and explorer menus.
    DocumentPluginEventManager getDocumentPluginEventManager();

    /// @return The XML Schema document plugin, which registers itself with the document plugin event
    /// manager (it opens and saves XML Schemas).
    XMLSchemaPlugin getXMLSchemaPlugin();

    /// @return The 'Content Store' tab.
    ContentStorePresenter getContentStorePresenter();

    /// @return The 'Content Templates' tab.
    ContentTemplateTabPresenter getContentTemplateTabPresenter();

    // Import, export and dependencies

    /// @return The 'Dependencies' tab.
    DependenciesTabPresenter getDependenciesTabPresenter();

    /// @return The 'Confirm Import' dialog, shown by firing `ImportConfigConfirmEvent`.
    ImportConfigConfirmPresenter getImportConfigConfirmPresenter();

    /// @return The Dependencies screen's information dialog, shown by firing
    /// `ShowDependenciesInfoDialogEvent`.
    DependenciesInfoPresenter getDependenciesInfoPresenter();

    // Documents and the explorer

    /// @return The 'New ...' / 'Save As' dialog, shown by firing `ShowCreateDocumentDialogEvent`.
    CreateDocumentPresenter getCreateDocumentPresenter();

    /// @return The 'Find' dialog, shown by firing `ShowFindEvent`.
    FindPresenter getFindPresenter();

    /// @return The 'Find In Content' dialog, shown by firing `ShowFindInContentEvent`.
    FindInContentPresenter getFindInContentPresenter();

    /// @return The tab session manager, which shows the tab session chooser for `OpenTabSessionEvent`.
    TabSessionManager getTabSessionManager();

    // Activities

    /// @return The activity chooser ('Choose Activity'), shown by its `show` method.
    ManageActivityPresenter getManageActivityPresenter();

    /// @return The current activity (a singleton), which opens the activity chooser.
    CurrentActivity getCurrentActivity();

    // Annotations

    /// @return An annotation tags tab (labels, statuses, comments...), as `AnnotationPlugin` creates it.
    AnnotationTagPresenter getAnnotationTagPresenter();

    /// @return An annotation's tab (the editor and its sub-tabs), as `AnnotationEditSupport` creates it.
    AnnotationPresenter getAnnotationPresenter();

    /// @return The 'Choose Annotation' dialog, shown by firing `ShowFindAnnotationEvent`.
    FindAnnotationPresenter getFindAnnotationPresenter();

    /// @return The annotation REST client, e.g. to fetch an annotation as `AnnotationEditSupport` does.
    AnnotationResourceClient getAnnotationResourceClient();

    /// @return The 'Annotations' (browse) tab.
    BrowseAnnotationPresenter getBrowseAnnotationPresenter();

    // Queries

    /// @return The 'Search Result Stores' dialog, shown by its `show` method.
    ResultStorePresenter getResultStorePresenter();

    /// @return The 'Traces' tab, as `TracesPlugin` opens it for `ShowTracesEvent`.
    TracesPresenter getTracesPresenter();
}
