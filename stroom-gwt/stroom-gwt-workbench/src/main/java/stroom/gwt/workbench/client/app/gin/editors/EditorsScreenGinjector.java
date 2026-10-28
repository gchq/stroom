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

package stroom.gwt.workbench.client.app.gin.editors;

import stroom.ai.client.component.AskStroomAiController;
import stroom.aws.s3.client.component.S3ConfigEditor;
import stroom.datagen.client.component.DataGenEditor;
import stroom.document.asset.client.component.DocumentAssetSelectionController;
import stroom.document.asset.client.component.DocumentAssetsTabController;
import stroom.documentation.client.component.DocumentationEditor;
import stroom.feed.client.component.FeedEditor;
import stroom.floormap.client.component.FloorMapEditorController;
import stroom.floormap.client.component.FloorMapInitController;
import stroom.floormap.client.component.FloorMapMapController;
import stroom.floormap.client.component.FloorMapSettingsController;
import stroom.folder.client.FolderTabs;
import stroom.gitrepo.client.component.GitRepoEditor;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.index.client.component.LuceneIndexEditor;
import stroom.kafka.client.component.KafkaConfigEditor;
import stroom.openai.client.component.OpenAIModelEditor;
import stroom.pathways.client.component.PathwaysEditor;
import stroom.pipeline.client.component.TextConverterEditor;
import stroom.pipeline.client.component.XsltEditor;
import stroom.script.client.component.ScriptEditor;
import stroom.search.elastic.client.component.ElasticClusterEditor;
import stroom.search.elastic.client.component.ElasticIndexEditor;
import stroom.search.solr.client.component.SolrIndexEditor;
import stroom.sqlstore.client.component.SqlTemporalStoreSettingsController;
import stroom.visualisation.client.component.VisualisationEditor;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.xmlschema.client.component.XMLSchemaEditor;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the `editors` batch of screen stories (the document editors of
/// `App/Editors/*`, `App/Index/*` and `App/Feed/*`, and the `App/AI/*` dialogs), creating Stroom's
/// presenters and views as Stroom's GIN modules do, as `AppScreenGinjector` does for the pilot:
/// ```
/// final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final GitRepoEditor editor = injector.getGitRepoEditor();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
///
/// The shared modules are the pilot's (`client/app/gin`); the others mirror Stroom's modules for
/// the editors (`XxxScreenModule` for Stroom's `XxxModule`), without plugins and app services.
@GinModules({
        ScreenViewsModule.class,
        EntityScreenModule.class,
        AlertScreenModule.class,
        ScheduleScreenModule.class,
        SqlTemporalStoreScreenModule.class,
})
public interface EditorsScreenGinjector extends ScreenGinjector {

    /// @return A Data Generator's editor, as `DataGenPlugin` opens it.
    DataGenEditor getDataGenEditor();

    /// @return A Documentation document's editor, as `DocumentationPlugin` opens it.
    DocumentationEditor getDocumentationEditor();

    /// @return An Elastic Cluster's editor, as `ElasticClusterPlugin` opens it.
    ElasticClusterEditor getElasticClusterEditor();

    /// @return A Floor Map's 'Editor' tab (the map canvas, its timeline and its Layers panel), as
    /// `FloorMapDocEditor` creates it.
    FloorMapEditorController getFloorMapEditor();

    /// @return The dialog that asks for a new Floor Map's facts and events stores, made for one use
    /// as `FloorMapPlugin`'s `DocInitialisationHandler` makes it.
    FloorMapInitController getFloorMapInitController();

    /// @return A Floor Map's 'Map' tab (the read-only map canvas, its timeline and its Tracking,
    /// Layers and Groups panels), as `FloorMapDocEditor` creates it.
    FloorMapMapController getFloorMapMap();

    /// @return A Floor Map's 'Settings' tab, as `FloorMapDocEditor` creates it.
    FloorMapSettingsController getFloorMapSettingsController();

    /// @return A Git Repository's editor, as `GitRepoPlugin` opens it.
    GitRepoEditor getGitRepoEditor();

    /// @return An OpenAI Model's editor, as `OpenAIModelPlugin` opens it.
    OpenAIModelEditor getOpenAIModelEditor();

    /// @return A Pathways document's editor, as `PathwaysPlugin` opens it.
    PathwaysEditor getPathwaysEditor();

    /// @return A Visualisation's editor, as `VisualisationPlugin` opens it.
    VisualisationEditor getVisualisationEditor();

    /// @return A Visualisation's 'Assets' tab on its own (the asset tree, its editor and its
    /// toolbar's 'Add file' menu with 'Upload File').
    DocumentAssetsTabController<VisualisationDoc> getVisualisationAssetsTab();

    /// @return A document's asset picker, as a floor map's settings have it, which opens the
    /// 'Choose Asset' dialog.
    DocumentAssetSelectionController getDocumentAssetSelectionController();

    /// @return A SQL temporal store's 'Settings' tab (its entry count and Reset Store).
    SqlTemporalStoreSettingsController getSqlTemporalStoreSettingsController();

    // Code documents

    /// @return An XSLT's editor, as `XsltPlugin` opens it.
    XsltEditor getXsltEditor();

    /// @return A Text Converter's editor, as `TextConverterPlugin` opens it.
    TextConverterEditor getTextConverterEditor();

    /// @return A Script's editor, as `ScriptPlugin` opens it.
    ScriptEditor getScriptEditor();

    /// @return A Kafka Config's editor, as `KafkaConfigPlugin` opens it.
    KafkaConfigEditor getKafkaConfigEditor();

    /// @return An S3 Config's editor, as `S3ConfigPlugin` opens it.
    S3ConfigEditor getS3ConfigEditor();

    /// @return An XML Schema's editor, as `XMLSchemaPlugin` opens it.
    XMLSchemaEditor getXMLSchemaEditor();

    // Indexes

    /// @return A Lucene index's editor, as `IndexPlugin` opens it.
    LuceneIndexEditor getLuceneIndexEditor();

    /// @return A Solr index's editor, as `SolrIndexPlugin` opens it.
    SolrIndexEditor getSolrIndexEditor();

    /// @return An Elastic index's editor, as `ElasticIndexPlugin` opens it.
    ElasticIndexEditor getElasticIndexEditor();

    // Folders and feeds

    /// @return A folder's tabs, or the System root's, which `FolderPlugin` (and
    /// `FolderRootPlugin`) open in a `DocumentEditorHost`.
    FolderTabs getFolderTabs();

    /// @return A Feed's editor, as `FeedPlugin` opens it.
    FeedEditor getFeedEditor();

    // AI

    /// @return The 'Ask Stroom AI' chat, shown by firing `ShowAskStroomAiEvent`.
    AskStroomAiController getAskStroomAiController();
}
