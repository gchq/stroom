/*
 * Copyright 2016 Crown Copyright
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

package stroom.app.client.gin;

import stroom.about.client.gin.AboutModule;
import stroom.ai.client.gin.AskStroomAIModule;
import stroom.alert.client.gin.AlertGinjector;
import stroom.alert.client.gin.AlertModule;
import stroom.analytics.client.gin.AnalyticsModule;
import stroom.analytics.client.gin.ReportModule;
import stroom.annotation.client.gin.AnnotationModule;
import stroom.aws.s3.client.gin.S3ConfigModule;
import stroom.cache.client.gin.CacheModule;
import stroom.contentstore.client.gin.ContentStoreModule;
import stroom.core.client.component.CoreController;
import stroom.core.client.component.FullScreenController;
import stroom.credentials.client.gin.CredentialsModule;
import stroom.dashboard.client.embeddedquery.gin.EmbeddedQueryModule;
import stroom.dashboard.client.gin.DashboardGinjector;
import stroom.dashboard.client.gin.DashboardModule;
import stroom.dashboard.client.vis.gin.VisModule;
import stroom.data.client.gin.StreamStoreModule;
import stroom.datagen.client.gin.DataGenModule;
import stroom.dictionary.client.gin.DictionaryModule;
import stroom.dispatch.client.RestModule;
import stroom.documentation.client.gin.DocumentationModule;
import stroom.entity.client.gin.EntityGinjector;
import stroom.entity.client.gin.EntityModule;
import stroom.feed.client.gin.FeedModule;
import stroom.floormap.client.gin.FloorMapModule;
import stroom.folder.client.gin.FolderModule;
import stroom.gitrepo.client.gin.GitRepoModule;
import stroom.importexport.client.gin.ImportExportConfigModule;
import stroom.index.client.gin.IndexModule;
import stroom.kafka.client.gin.KafkaConfigModule;
import stroom.monitoring.client.gin.MonitoringModule;
import stroom.openai.client.gin.OpenAIModelModule;
import stroom.pathways.client.gin.PathwaysModule;
import stroom.pipeline.client.gin.PipelineModule;
import stroom.planb.client.gin.PlanBModule;
import stroom.preferences.client.gin.UserPreferencesGinjector;
import stroom.preferences.client.gin.UserPreferencesModule;
import stroom.query.client.gin.QueryModule;
import stroom.receive.content.client.gin.ContentTemplateModule;
import stroom.receive.rules.client.gin.PolicyModule;
import stroom.script.client.gin.ScriptModule;
import stroom.search.elastic.client.gin.ElasticClusterModule;
import stroom.search.elastic.client.gin.ElasticIndexModule;
import stroom.search.solr.client.gin.SolrIndexModule;
import stroom.security.client.gin.SecurityGinjector;
import stroom.security.client.gin.SecurityModule;
import stroom.security.identity.client.gin.ChangePasswordGinjector;
import stroom.security.identity.client.gin.ChangePasswordModule;
import stroom.sqlstore.client.gin.SqlTemporalStoreGinjector;
import stroom.sqlstore.client.gin.SqlTemporalStoreModule;
import stroom.statistics.impl.sql.client.gin.StatisticsModule;
import stroom.task.client.gin.TaskModule;
import stroom.view.client.gin.ViewModule;
import stroom.visualisation.client.gin.VisualisationModule;
import stroom.welcome.client.gin.WelcomeModule;
import stroom.widget.popup.client.gin.PopupGinjector;
import stroom.widget.popup.client.gin.PopupModule;
import stroom.xmlschema.client.gin.XMLSchemaModule;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

@GinModules({
        AboutModule.class,
        AlertModule.class,
        AnnotationModule.class,
        AppModule.class,
        AskStroomAIModule.class,
        ContentStoreModule.class,
        CacheModule.class,
        ContentTemplateModule.class,
        CredentialsModule.class,
        RestModule.class,
        DashboardModule.class,
        DictionaryModule.class,
        DocumentationModule.class,
        EntityModule.class,
        FeedModule.class,
        FolderModule.class,
        ImportExportConfigModule.class,
        IndexModule.class,
        KafkaConfigModule.class,
        MonitoringModule.class,
        OpenAIModelModule.class,
        PathwaysModule.class,
        PipelineModule.class,
        PluginsModule.class,
        PolicyModule.class,
        PopupModule.class,
        UserPreferencesModule.class,
        QueryModule.class,
        ScriptModule.class,
        SecurityModule.class,
        ChangePasswordModule.class,
        ElasticClusterModule.class,
        ElasticIndexModule.class,
        AnalyticsModule.class,
        DataGenModule.class,
        ReportModule.class,
        FloorMapModule.class,
        SolrIndexModule.class,
        StatisticsModule.class,
        StreamStoreModule.class,
        TaskModule.class,
        VisModule.class,
        EmbeddedQueryModule.class,
        VisualisationModule.class,
        ViewModule.class,
        WelcomeModule.class,
        XMLSchemaModule.class,
        S3ConfigModule.class,
        PlanBModule.class,
        SqlTemporalStoreModule.class,
        GitRepoModule.class
})
public interface AppGinjectorUser extends
        AlertGinjector,
        DashboardGinjector,
        EntityGinjector,
        Ginjector,
        PluginsGinjector,
        PopupGinjector,
        UserPreferencesGinjector,
        SecurityGinjector,
        ChangePasswordGinjector,
        SqlTemporalStoreGinjector {

    // Default implementation of standard resources
    @SuppressWarnings("unused")
    EventBus getEventBus();

    // Presenters
    Provider<CoreController> getCoreController();

    @SuppressWarnings("unused")
    Provider<FullScreenController> getFullScreenController();
}
