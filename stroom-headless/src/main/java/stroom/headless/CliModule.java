/*
 * Copyright 2018 Crown Copyright
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

package stroom.headless;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.cache.impl.CacheModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.io.BasicStreamCloser;
import stroom.io.StreamCloser;
import stroom.node.api.NodeInfo;
import stroom.node.shared.Node;
import stroom.pipeline.cache.PipelineCacheModule;
import stroom.pipeline.scope.PipelineScopeModule;
import stroom.pipeline.scope.PipelineScoped;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamtask.statistic.MetaDataStatistic;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskHandlerBinder;
import stroom.task.shared.ThreadPool;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CliModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.activity.impl.mock.MockActivityModule());
        install(new CacheModule());
        install(new PipelineCacheModule());
//        install(new ClusterModule());
        install(new stroom.dictionary.DictionaryModule());
//        install(new stroom.dictionary.DictionaryHandlerModule());
//        install(new stroom.docstore.impl.fs.FSPersistenceModule());
//        install(new stroom.document.DocumentModule());
//        install(new stroom.entity.EntityModule());
//        install(new stroom.entity.cluster.EntityClusterModule());
//        install(new EntityClusterTaskModule());
        install(new stroom.explorer.MockExplorerModule());
        install(new stroom.feed.FeedModule());
        install(new PipelineScopeModule());
        install(new stroom.importexport.ImportExportModule());
//        install(new stroom.jobsystem.JobSystemModule());
//        install(new stroom.lifecycle.LifecycleModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
//        install(new stroom.node.impl.NodeModule());
//        install(new stroom.node.impl.MockNodeServiceModule());
//        install(new EntityManagerModule());
        install(new stroom.pipeline.PipelineModule());
        install(new stroom.pipeline.factory.PipelineFactoryModule());
        install(new stroom.pipeline.factory.CommonPipelineElementModule());
        install(new stroom.pipeline.xsltfunctions.CommonXsltFunctionModule());
//        install(new stroom.pipeline.stepping.PipelineSteppingModule());
//        install(new stroom.pipeline.task.PipelineStreamTaskModule());
//        install(new stroom.policy.PolicyModule());
//        install(new stroom.properties.impl.PropertyModule());
//        install(new stroom.refdata.ReferenceDataModule());
//        install(new stroom.resource.ResourceModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
//        install(new DataStoreHandlerModule());
        install(new DocStoreModule());
        install(new stroom.docstore.impl.fs.FSPersistenceModule());
//        install(new stroom.streamtask.StreamTaskModule());
//        install(new stroom.task.TaskModule());
//        install(new stroom.task.cluster.ClusterTaskModule());
//        install(new stroom.volume.VolumeModule());
        install(new stroom.xmlschema.XmlSchemaModule());

        bind(InternalStatisticsReceiver.class).to(HeadlessInternalStatisticsReceiver.class);
        bind(StreamCloser.class).to(BasicStreamCloser.class).in(PipelineScoped.class);
        bind(TaskContext.class).to(SimpleTaskContext.class);

        TaskHandlerBinder.create(binder())
                .bind(HeadlessTranslationTask.class, HeadlessTranslationTaskHandler.class);
    }

    @Provides
    public MetaDataStatistic metaDataStatistic() {
        return metaData -> {
        };
    }

    @Provides
    public ExecutorProvider executorProvider() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        return new ExecutorProvider() {
            @Override
            public Executor getExecutor() {
                return executorService;
            }

            @Override
            public Executor getExecutor(final ThreadPool threadPool) {
                return executorService;
            }
        };
    }

    @Provides
    public NodeInfo nodeInfo() {
        return new NodeInfo() {
            @Override
            public Node getThisNode() {
                return null;
            }

            @Override
            public String getThisNodeName() {
                return null;
            }
        };
    }
}