/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.benchmark;

import stroom.activity.mock.MockActivityModule;
import stroom.cache.impl.CacheModule;
import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.mock.MockFsVolumeGroupService;
import stroom.data.store.mock.MockStreamStoreModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.impl.DocFinderModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.dao.MockDocDependencyService;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.explorer.impl.MockExplorerModule;
import stroom.feed.impl.FeedModule;
import stroom.importexport.impl.ImportExportModule;
import stroom.meta.mock.MockMetaModule;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.node.api.NodeInfo;
import stroom.pipeline.cache.PipelineCacheModule;
import stroom.pipeline.factory.CommonPipelineElementModule;
import stroom.pipeline.factory.DataStorePipelineElementModule;
import stroom.pipeline.factory.PipelineFactoryModule;
import stroom.pipeline.xsltfunctions.CommonXsltFunctionModule;
import stroom.processor.impl.MockProcessorModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.impl.TaskContextModule;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.guice.GuiceUtil;
import stroom.util.http.BasicHttpClientFactory;
import stroom.util.http.HttpClientFactory;
import stroom.util.io.BasicStreamCloser;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.FileUtil;
import stroom.util.io.PathConfig;
import stroom.util.io.StreamCloser;
import stroom.util.jersey.MockJerseyModule;
import stroom.util.metrics.Metrics;
import stroom.util.metrics.MetricsImpl;
import stroom.util.pipeline.scope.PipelineScopeModule;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.servlet.MockServletModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A Guice module that provides just enough of Stroom to build and run a {@link
 * stroom.pipeline.factory.Pipeline}, with no database, no content packs and no JUnit extension.
 * <p>
 * It is closely modelled on {@link stroom.headless.CliModule}, which is the existing proof that the
 * pipeline machinery runs standalone. The one deliberate difference is that document storage uses
 * {@link MemoryPersistenceModule} rather than the file system, so a benchmark JVM leaves nothing
 * behind and repeated forks cannot see each other's documents.
 * <p>
 * Being free of JUnit and of a shared database is what lets the JMH benchmarks fork properly.
 */
public class PipelineBenchmarkModule extends AbstractModule {

    private final Path homeDir;
    private final Path tempDir;

    public PipelineBenchmarkModule(final Path homeDir, final Path tempDir) {
        this.homeDir = Objects.requireNonNull(homeDir);
        this.tempDir = Objects.requireNonNull(tempDir);
    }

    @Override
    protected void configure() {
        install(new MockActivityModule());
        install(new CacheModule());
        install(new PipelineCacheModule());
        install(new DictionaryModule());
        install(new MockExplorerModule());
        install(new FeedModule());
        install(new PipelineScopeModule());
        install(new ImportExportModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
        install(new stroom.pipeline.PipelineModule());
        install(new PipelineFactoryModule());
        install(new CommonPipelineElementModule());
        install(new DataStorePipelineElementModule());
        install(new CommonXsltFunctionModule());
        install(new MockMetaModule());
        install(new MockSecurityContextModule());
        install(new DocStoreModule());
        install(new DocFinderModule());
        install(new MemoryPersistenceModule());
        install(new MockStreamStoreModule());
        install(new MockServletModule());
        install(new MockProcessorModule());
        install(new TaskContextModule());
        install(new MockJerseyModule());
        install(new DirProvidersModule());

        bind(DocDependencyService.class).to(MockDocDependencyService.class);
        bind(StreamCloser.class).to(BasicStreamCloser.class).in(PipelineScoped.class);
        bind(PathConfig.class).toInstance(createPathConfig());
        bind(HttpClientFactory.class).to(BasicHttpClientFactory.class);
        bind(FsVolumeGroupService.class).to(MockFsVolumeGroupService.class);
        bind(Metrics.class).toInstance(new MetricsImpl(new MetricRegistry()));

        // Nothing here publishes explorer data sources, but the set must exist to be injectable.
        GuiceUtil.buildMultiBinder(binder(), IsSpecialExplorerDataSource.class);
    }

    private PathConfig createPathConfig() {
        return new PathConfig() {
            @Override
            public String getHome() {
                return FileUtil.getCanonicalPath(homeDir);
            }

            @Override
            public String getTemp() {
                return FileUtil.getCanonicalPath(tempDir);
            }
        };
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

    @Provides
    EntityEventBus entityEventBus() {
        return EntityEventBus.NO_OP_EVENT_BUS;
    }

    @Provides
    InternalStatisticsReceiver internalStatisticsReceiver() {
        return new InternalStatisticsReceiver() {
            @Override
            public void putEvent(final InternalStatisticEvent event) {
                // Benchmarks do not collect statistics.
            }

            @Override
            public void putEvents(final List<InternalStatisticEvent> events) {
                // Benchmarks do not collect statistics.
            }
        };
    }
}
