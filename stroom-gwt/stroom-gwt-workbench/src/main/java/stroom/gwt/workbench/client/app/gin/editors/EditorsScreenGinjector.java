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

import stroom.ai.client.AskStroomAiPresenter;
import stroom.aws.s3.client.presenter.S3ConfigPresenter;
import stroom.datagen.client.presenter.DataGenPresenter;
import stroom.documentation.client.presenter.DocumentationPresenter;
import stroom.feed.client.presenter.FeedPresenter;
import stroom.folder.client.FolderPresenter;
import stroom.folder.client.FolderRootPresenter;
import stroom.gitrepo.client.presenter.GitRepoPresenter;
import stroom.gwt.workbench.client.app.gin.CredentialsScreenModule;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.MonitoringScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.index.client.presenter.IndexPresenter;
import stroom.kafka.client.presenter.KafkaConfigPresenter;
import stroom.openai.client.presenter.OpenAIModelPresenter;
import stroom.pathways.client.presenter.PathwaysPresenter;
import stroom.pipeline.client.presenter.TextConverterPresenter;
import stroom.pipeline.client.presenter.XsltPresenter;
import stroom.script.client.presenter.ScriptPresenter;
import stroom.search.elastic.client.presenter.ElasticClusterPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexPresenter;
import stroom.search.solr.client.presenter.SolrIndexPresenter;
import stroom.visualisation.client.presenter.VisualisationAssetsPresenter;
import stroom.visualisation.client.presenter.VisualisationPresenter;
import stroom.xmlschema.client.presenter.XMLSchemaPresenter;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the `editors` batch of screen stories (the document editors of
/// `App/Editors/*`, `App/Index/*` and `App/Feed/*`, and the `App/AI/*` dialogs), creating Stroom's
/// presenters and views as Stroom's GIN modules do, as `AppScreenGinjector` does for the pilot:
/// ```
/// final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final GitRepoPresenter presenter = injector.getGitRepoPresenter();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
///
/// The shared modules are the pilot's (`client/app/gin`); the others mirror Stroom's modules for
/// the editors (`XxxScreenModule` for Stroom's `XxxModule`), without plugins and app services.
@GinModules({
        ScreenViewsModule.class,
        CredentialsScreenModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        MonitoringScreenModule.class,
        SecurityScreenModule.class,
        AlertScreenModule.class,
        AskStroomAIScreenModule.class,
        DataGenScreenModule.class,
        DocumentationScreenModule.class,
        ElasticClusterScreenModule.class,
        ElasticIndexScreenModule.class,
        FeedScreenModule.class,
        FolderScreenModule.class,
        GitRepoScreenModule.class,
        HttpScreenModule.class,
        IndexScreenModule.class,
        KafkaConfigScreenModule.class,
        OpenAIModelScreenModule.class,
        PathwaysScreenModule.class,
        PipelineScreenModule.class,
        S3ConfigScreenModule.class,
        ScheduleScreenModule.class,
        ScriptScreenModule.class,
        SolrIndexScreenModule.class,
        StreamStoreScreenModule.class,
        VisualisationScreenModule.class,
        XMLSchemaScreenModule.class,
})
public interface EditorsScreenGinjector extends ScreenGinjector {

    /// @return A Data Generator's editor tab, as `DataGenPlugin` creates it.
    DataGenPresenter getDataGenPresenter();

    /// @return A Documentation document's editor tab, as `DocumentationPlugin` creates it.
    DocumentationPresenter getDocumentationPresenter();

    /// @return An Elastic Cluster's editor tab, as `ElasticClusterPlugin` creates it.
    ElasticClusterPresenter getElasticClusterPresenter();

    /// @return A Git Repository's editor tab, as `GitRepoPlugin` creates it.
    GitRepoPresenter getGitRepoPresenter();

    /// @return An OpenAI Model's editor tab, as `OpenAIModelPlugin` creates it.
    OpenAIModelPresenter getOpenAIModelPresenter();

    /// @return A Pathways document's editor tab, as `PathwaysPlugin` creates it.
    PathwaysPresenter getPathwaysPresenter();

    /// @return A Visualisation's editor tab, as `VisualisationPlugin` creates it.
    VisualisationPresenter getVisualisationPresenter();

    /// @return A Visualisation's 'Assets' tab on its own (the asset tree, its editor and its
    /// toolbar's 'Add file' menu with 'Upload File').
    VisualisationAssetsPresenter getVisualisationAssetsPresenter();

    // Code documents

    /// @return An XSLT's editor tab, as `XsltPlugin` creates it.
    XsltPresenter getXsltPresenter();

    /// @return A Text Converter's editor tab, as `TextConverterPlugin` creates it.
    TextConverterPresenter getTextConverterPresenter();

    /// @return A Script's editor tab, as `ScriptPlugin` creates it.
    ScriptPresenter getScriptPresenter();

    /// @return A Kafka Config's editor tab, as `KafkaConfigPlugin` creates it.
    KafkaConfigPresenter getKafkaConfigPresenter();

    /// @return An S3 Config's editor tab, as `S3ConfigPlugin` creates it.
    S3ConfigPresenter getS3ConfigPresenter();

    /// @return An XML Schema's editor tab, as `XMLSchemaPlugin` creates it.
    XMLSchemaPresenter getXMLSchemaPresenter();

    // Indexes

    /// @return A Lucene index's editor tab, as `IndexPlugin` creates it.
    IndexPresenter getIndexPresenter();

    /// @return A Solr index's editor tab, as `SolrIndexPlugin` creates it.
    SolrIndexPresenter getSolrIndexPresenter();

    /// @return An Elastic index's editor tab, as `ElasticIndexPlugin` creates it.
    ElasticIndexPresenter getElasticIndexPresenter();

    // Folders and feeds

    /// @return A folder's tab, as `FolderPlugin` creates it.
    FolderPresenter getFolderPresenter();

    /// @return The System root folder's tab, as `FolderRootPlugin` creates it.
    FolderRootPresenter getFolderRootPresenter();

    /// @return A Feed's editor tab, as `FeedPlugin` creates it.
    FeedPresenter getFeedPresenter();

    // AI

    /// @return The 'Ask Stroom AI' chat, shown by firing `ShowAskStroomAiEvent`.
    AskStroomAiPresenter getAskStroomAiPresenter();
}
