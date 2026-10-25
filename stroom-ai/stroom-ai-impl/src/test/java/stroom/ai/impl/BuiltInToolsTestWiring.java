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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.api.CodeChangeStepper;
import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.config.global.api.GlobalConfigReader;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataService;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.impl.QueryService;
import stroom.resource.api.ResourceStore;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.TaskResource;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.mockito.Mockito;

import java.util.Set;

/// Every built-in tool, as Guice binds them, with everything they need mocked, as `TestAgentModeTools` builds
/// them. Tools are only asked for their specification and flags, so the mocks are never used.
final class BuiltInToolsTestWiring {

    private final Injector injector;

    /// @param aiService The AI service the tools are given.
    BuiltInToolsTestWiring(final AiService aiService) {
        injector = Guice.createInjector(new BuiltInToolsModule(), new AbstractModule() {
            @Override
            protected void configure() {
                bind(ChatWorkspace.class).toInstance(Mockito.mock(ChatWorkspace.class));
                bind(AiService.class).toInstance(aiService);
                bind(AiMemoryService.class).toInstance(Mockito.mock(AiMemoryService.class));
                bind(QueryService.class).toInstance(Mockito.mock(QueryService.class));
                bind(DashboardBuilder.class).toInstance(Mockito.mock(DashboardBuilder.class));
                bind(ResourceStore.class).toInstance(Mockito.mock(ResourceStore.class));
                bind(Store.class).toInstance(Mockito.mock(Store.class));
                bind(SecurityContext.class).toInstance(Mockito.mock(SecurityContext.class));
                bind(TableSummariser.class).toInstance(Mockito.mock(TableSummariser.class));
                bind(AskStroomAiConfig.class).toInstance(new AskStroomAiConfig());
                bind(McpElicitations.class).toInstance(Mockito.mock(McpElicitations.class));
                bind(AiChatRunManager.class).toInstance(Mockito.mock(AiChatRunManager.class));
                bind(ExplorerNodeService.class).toInstance(Mockito.mock(ExplorerNodeService.class));
                bind(ExplorerService.class).toInstance(Mockito.mock(ExplorerService.class));
                bind(DocDependencyService.class).toInstance(Mockito.mock(DocDependencyService.class));
                bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                bind(AiAuditLog.class).toInstance(Mockito.mock(AiAuditLog.class));
                bind(TabSnapshots.class).toInstance(Mockito.mock(TabSnapshots.class));
                bind(TableDownloader.class).toInstance(Mockito.mock(TableDownloader.class));
                bind(DataSourceProviderRegistry.class).toInstance(Mockito.mock(DataSourceProviderRegistry.class));
                bind(DashboardService.class).toInstance(Mockito.mock(DashboardService.class));
                bind(TableAnalysisConfig.class).toInstance(new TableAnalysisConfig());
                MapBinder.newMapBinder(binder(), DocumentTypeName.class, DocumentActionHandler.class);
                bind(MetaService.class).toInstance(Mockito.mock(MetaService.class));
                bind(ProcessorFilterService.class).toInstance(Mockito.mock(ProcessorFilterService.class));
                bind(ProcessorTaskService.class).toInstance(Mockito.mock(ProcessorTaskService.class));
                bind(TaskResource.class).toInstance(Mockito.mock(TaskResource.class));
                bind(TaskContextFactory.class).toInstance(new SimpleTaskContextFactory());
                bind(NodeService.class).toInstance(Mockito.mock(NodeService.class));
                bind(ResultStoreManager.class).toInstance(Mockito.mock(ResultStoreManager.class));
                bind(CodeChangeStepper.class).toInstance(Mockito.mock(CodeChangeStepper.class));
                bind(ElasticIndexTemplates.class).toInstance(Mockito.mock(ElasticIndexTemplates.class));
                bind(AttributeMapFactory.class).toInstance(Mockito.mock(AttributeMapFactory.class));
                bind(DataService.class).toInstance(Mockito.mock(DataService.class));
                bind(FeedProperties.class).toInstance(Mockito.mock(FeedProperties.class));
                bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                bind(XmlSchemaStore.class).toInstance(Mockito.mock(XmlSchemaStore.class));
                bind(ScriptStore.class).toInstance(Mockito.mock(ScriptStore.class));
                bind(VisualisationStore.class).toInstance(Mockito.mock(VisualisationStore.class));
                bind(DocumentPermissionService.class)
                        .toInstance(Mockito.mock(DocumentPermissionService.class));
                bind(SchemaPool.class).toInstance(Mockito.mock(SchemaPool.class));
                bind(ContentIndex.class).toInstance(Mockito.mock(ContentIndex.class));
                bind(StroomEventLoggingService.class).toInstance(Mockito.mock(StroomEventLoggingService.class));
                bind(GlobalConfigReader.class).toInstance(Mockito.mock(GlobalConfigReader.class));
                bind(NodeInfo.class).toInstance(Mockito.mock(NodeInfo.class));
                bind(ExecutorProvider.class).toInstance(Mockito.mock(ExecutorProvider.class));
                Multibinder.newSetBinder(binder(), ContentIndexable.class);
            }
        });
    }

    /// @return The registry of the built-in tools.
    BuiltInTools builtInTools() {
        return injector.getInstance(BuiltInTools.class);
    }

    /// @return The built-in tools themselves, as bound.
    Set<StroomTool> tools() {
        return injector.getInstance(Key.get(new TypeLiteral<Set<StroomTool>>() {
        }));
    }
}
