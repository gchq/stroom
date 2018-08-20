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
import com.google.inject.multibindings.Multibinder;
import stroom.guice.PipelineScoped;
import stroom.io.BasicStreamCloser;
import stroom.io.StreamCloser;
import stroom.node.LocalNodeProvider;
import stroom.node.shared.Node;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamtask.statistic.MetaDataStatistic;
import stroom.task.ExecutorProvider;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskHandler;
import stroom.task.shared.ThreadPool;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CliModule extends AbstractModule {
    @Override
    protected void configure() {
//        install(new stroom.cache.CacheModule());
        install(new stroom.cache.PipelineCacheModule());
//        install(new ClusterModule());
        install(new stroom.dictionary.DictionaryModule());
//        install(new stroom.dictionary.DictionaryHandlerModule());
//        install(new stroom.docstore.fs.FSPersistenceModule());
//        install(new stroom.document.DocumentModule());
//        install(new stroom.entity.EntityModule());
//        install(new stroom.entity.cluster.EntityClusterModule());
//        install(new EntityClusterTaskModule());
        install(new stroom.explorer.MockExplorerModule());
        install(new stroom.feed.FeedModule());
        install(new stroom.guice.PipelineScopeModule());
        install(new stroom.importexport.ImportExportModule());
//        install(new stroom.jobsystem.JobSystemModule());
//        install(new stroom.lifecycle.LifecycleModule());
        install(new stroom.logging.LoggingModule());
//        install(new stroom.node.NodeModule());
//        install(new stroom.node.MockNodeServiceModule());
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
        install(new stroom.docstore.fs.FSPersistenceModule());
//        install(new stroom.streamtask.StreamTaskModule());
//        install(new stroom.task.TaskModule());
//        install(new stroom.task.cluster.ClusterTaskModule());
//        install(new stroom.volume.VolumeModule());
        install(new stroom.xmlschema.XmlSchemaModule());

        bind(InternalStatisticsReceiver.class).to(HeadlessInternalStatisticsReceiver.class);
        bind(StreamCloser.class).to(BasicStreamCloser.class).in(PipelineScoped.class);
        bind(TaskContext.class).to(SimpleTaskContext.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.headless.HeadlessTranslationTaskHandler.class);
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
    public LocalNodeProvider localNodeProvider() {
        return new LocalNodeProvider() {
            @Override
            public Node get() {
                return null;
            }
        };
    }
}