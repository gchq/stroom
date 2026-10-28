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
import stroom.activity.client.component.ManageActivityController;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.component.AnnotationTabs;
import stroom.annotation.client.component.AnnotationTagsController;
import stroom.annotation.client.component.BrowseAnnotationController;
import stroom.annotation.client.component.FindAnnotationController;
import stroom.cache.client.component.CachesController;
import stroom.config.global.client.component.GlobalPropertyTabController;
import stroom.content.client.component.ContentTabPaneController;
import stroom.contentstore.client.component.ContentStoreTabController;
import stroom.core.client.ContentManager;
import stroom.data.store.impl.fs.client.component.FsVolumeGroupsController;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.component.CreateDocumentController;
import stroom.explorer.client.TabSessionManager;
import stroom.explorer.client.component.FindController;
import stroom.explorer.client.component.FindInContentController;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.importexport.client.component.DependenciesInfoController;
import stroom.importexport.client.component.DependenciesTabController;
import stroom.importexport.client.component.ImportConfigConfirmController;
import stroom.importexport.client.component.ImportConfigController;
import stroom.index.client.component.IndexVolumeGroupsController;
import stroom.monitoring.client.component.DatabaseTablesMonitoringController;
import stroom.pathways.client.component.TracesEditor;
import stroom.query.client.component.SearchResultStoresController;
import stroom.receive.content.client.component.ContentTemplateTabController;
import stroom.xmlschema.client.XMLSchemaPlugin;

import com.google.gwt.inject.client.GinModules;

/// The `content` batch's [ScreenGinjector]: it creates the screens of the `App/Main` content,
/// annotation, activity and monitoring stories (controllers and their components) as Stroom's GIN
/// modules do, as `AppScreenGinjector` does for the pilot's screens. Create one each time the
/// story renders and give it to the harness with `ScreenHarness.Builder.injector(...)`.
///
/// Its modules are the shared [ScreenViewsModule] (and other shared mirrors in `app.gin`) plus
/// this package's mirrors of Stroom's modules.
@GinModules({
        ScreenViewsModule.class,
        EntityScreenModule.class,
        TaskScreenModule.class,
        ContentAppScreenModule.class,
})
public interface ContentScreenGinjector extends ScreenGinjector {

    // Monitoring

    /// @return The 'Caches' tab.
    CachesController getCachesController();

    /// @return The 'Database Tables' tab.
    DatabaseTablesMonitoringController getDatabaseTablesController();

    /// @return The 'Index Volumes' tab.
    IndexVolumeGroupsController getIndexVolumeGroupsController();

    /// @return The 'Data Volumes' tab.
    FsVolumeGroupsController getFsVolumeGroupsController();

    /// @return The 'Properties' tab.
    GlobalPropertyTabController getGlobalPropertyTabController();

    // Content

    /// @return The document tab pane, shown by registering it as the content events' handler.
    ContentTabPaneController getContentTabPaneController();

    /// @return The content manager (a singleton), which opens tabs in the tab pane.
    ContentManager getContentManager();

    /// @return The document plugin event manager (a singleton), which builds the tab and explorer menus.
    DocumentPluginEventManager getDocumentPluginEventManager();

    /// @return The XML Schema document plugin, which registers itself with the document plugin event
    /// manager (it opens and saves XML Schemas).
    XMLSchemaPlugin getXMLSchemaPlugin();

    /// @return The 'Content Store' tab.
    ContentStoreTabController getContentStoreTabController();

    /// @return The 'Content Templates' tab.
    ContentTemplateTabController getContentTemplateTabController();

    // Import, export and dependencies

    /// @return The 'Dependencies' tab.
    DependenciesTabController getDependenciesTabController();

    /// @return The 'Confirm Import' dialog, made for one use when `ImportConfigConfirmEvent` fires.
    ImportConfigConfirmController getImportConfigConfirmController();

    /// @return The 'Import' dialog (choose and upload a file), as `ImportConfigPlugin` shows it for
    /// `ImportConfigEvent`.
    ImportConfigController getImportConfigController();

    /// @return The Dependencies screen's information dialog, shown by firing
    /// `ShowDependenciesInfoDialogEvent`.
    DependenciesInfoController getDependenciesInfoController();

    // Documents and the explorer

    /// @return The 'New ...' / 'Save As' dialog, shown by firing `ShowCreateDocumentDialogEvent`.
    CreateDocumentController getCreateDocumentController();

    /// @return The 'Find' dialog, as `NavigationPlugin` shows it for `ShowFindEvent`.
    FindController getFindController();

    /// @return The 'Find In Content' dialog, as `NavigationPlugin` shows it for
    /// `ShowFindInContentEvent`.
    FindInContentController getFindInContentController();

    /// @return The tab session manager, which shows the tab session chooser for `OpenTabSessionEvent`.
    TabSessionManager getTabSessionManager();

    // Activities

    /// @return The activity chooser ('Choose Activity'), made for one use and shown by its
    ///         `show` method.
    ManageActivityController getManageActivityController();

    /// @return The current activity (a singleton), which opens the activity chooser.
    CurrentActivity getCurrentActivity();

    // Annotations

    /// @return An annotation tags tab (labels, statuses, comments...), as `AnnotationPlugin` creates it.
    AnnotationTagsController getAnnotationTagsController();

    /// @return An annotation's tab (the editor and its sub-tabs), as `AnnotationEditSupport` creates it.
    AnnotationTabs getAnnotationTabs();

    /// @return The 'Choose Annotation' dialog, kept by the app and shown when
    ///         `ShowFindAnnotationEvent` fires.
    FindAnnotationController getFindAnnotationController();

    /// @return The annotation REST client, e.g. to fetch an annotation as `AnnotationEditSupport` does.
    AnnotationResourceClient getAnnotationResourceClient();

    /// @return The 'Annotations' (browse) tab.
    BrowseAnnotationController getBrowseAnnotationController();

    // Queries

    /// @return The 'Search Result Stores' dialog, shown by its `show` method.
    SearchResultStoresController getSearchResultStoresController();

    /// @return The 'Traces' tab, as `TracesPlugin` opens it for `ShowTracesEvent`.
    TracesEditor getTracesEditor();
}
