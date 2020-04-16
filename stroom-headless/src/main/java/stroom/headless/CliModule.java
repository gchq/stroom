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
import stroom.activity.mock.MockActivityModule;
import stroom.cache.impl.CacheModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.explorer.impl.MockExplorerModule;
import stroom.feed.impl.FeedModule;
import stroom.importexport.impl.ImportExportModule;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.node.api.NodeInfo;
import stroom.pipeline.cache.PipelineCacheModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.impl.TaskContextModule;
import stroom.util.io.BasicStreamCloser;
import stroom.util.io.StreamCloser;
import stroom.util.pipeline.scope.PipelineScopeModule;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.servlet.MockServletModule;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CliModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new MockActivityModule());
        install(new CacheModule());
        install(new PipelineCacheModule());
//        install(new ClusterModule());
        install(new DictionaryModule());
//        install(new stroom.dictionary.impl.DictionaryHandlerModule());
//        install(new stroom.docstore.impl.fs.FSPersistenceModule());
//        install(new stroom.document.DocumentModule());
//        install(new stroom.entity.EntityModule());
//        install(new stroom.entity.cluster.EntityClusterModule());
//        install(new EntityClusterTaskModule());
        install(new MockExplorerModule());
        install(new FeedModule());
        install(new PipelineScopeModule());
        install(new ImportExportModule());
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
//        install(new stroom.pipeline.refdata.ReferenceDataModule());
//        install(new stroom.resource.ResourceModule());
        install(new MockSecurityContextModule());
//        install(new DataStoreHandlerModule());
        install(new DocStoreModule());
        install(new stroom.docstore.impl.fs.FSPersistenceModule());
//        install(new stroom.streamtask.StreamTaskModule());
//        install(new stroom.task.impl.TaskModule());
//        install(new stroom.task.cluster.impl.ClusterTaskModule());
//        install(new stroom.index.impl.selection.selection.VolumeModule());
        install(new MockServletModule());
        install(new TaskContextModule());

        bind(InternalStatisticsReceiver.class).to(HeadlessInternalStatisticsReceiver.class);
        bind(StreamCloser.class).to(BasicStreamCloser.class).in(PipelineScoped.class);
    }

    @Provides
    public MetaStatistics metaStatistics() {
        return metaData -> {
        };
    }

    @Provides
    public Executor executorProvider() {
        return Executors.newCachedThreadPool();
    }

    @Provides
    public NodeInfo nodeInfo() {
        return () -> null;
    }
}