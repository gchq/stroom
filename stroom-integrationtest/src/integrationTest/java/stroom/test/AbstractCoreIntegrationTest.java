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

package stroom.test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import stroom.guice.PipelineScopeModule;
import stroom.spring.PersistService;
import stroom.task.TaskManager;

public abstract class AbstractCoreIntegrationTest extends StroomIntegrationTest {
    private static final Injector injector;

    static {
        injector = Guice.createInjector(
                new stroom.entity.EntityModule(),
                new stroom.datafeed.DataFeedModule(),
                new stroom.security.MockSecurityContextModule(),
                new stroom.ruleset.RulesetModule(),
                new stroom.dictionary.DictionaryStoreModule(),
                new stroom.dictionary.DictionaryHandlerModule(),
                new stroom.docstore.db.DBPersistenceModule(),
                new stroom.spring.PersistenceModule(),
                new stroom.internalstatistics.MockMetaDataStatisticModule(),
                new stroom.properties.PropertyModule(),
                new stroom.importexport.ImportExportModule(),
                new stroom.explorer.ExplorerModule(),
                new stroom.servlet.ServletModule(),
                new stroom.test.DatabaseTestControlModule(),
                new stroom.cache.CacheModule(),
                new stroom.index.IndexModule(),
                new stroom.node.NodeServiceModule(),
                new stroom.node.NodeTestConfigModule(),
                new stroom.node.NodeModule(),
                new stroom.node.NodeHandlerModule(),
                new stroom.volume.VolumeModule(),
                new stroom.statistics.internal.MockInternalStatisticsModule(),
                new stroom.streamstore.StreamStoreModule(),
                new stroom.streamstore.fs.FSModule(),
                new stroom.streamtask.StreamTaskModule(),
                new stroom.task.TaskModule(),
                new stroom.task.cluster.ClusterTaskModule(),
                new stroom.jobsystem.JobSystemModule(),
                new stroom.pipeline.PipelineModule(),
                new stroom.cache.PipelineCacheModule(),
                new stroom.pipeline.stepping.PipelineSteppingModule(),
                new stroom.pipeline.task.PipelineStreamTaskModule(),
                new stroom.dashboard.DashboardModule(),
                new stroom.document.DocumentModule(),
                new stroom.entity.cluster.EntityClusterModule(),
                new stroom.entity.event.EntityEventModule(),
                new stroom.feed.FeedModule(),
                new stroom.lifecycle.LifecycleModule(),
                new stroom.policy.PolicyModule(),
                new stroom.query.QueryModule(),
                new stroom.refdata.ReferenceDataModule(),
                new stroom.script.ScriptModule(),
                new stroom.search.SearchModule(),
                new stroom.security.SecurityModule(),
                new stroom.cluster.MockClusterModule(),
                new stroom.dashboard.logging.LoggingModule(),
                new stroom.datasource.DatasourceModule(),
                new stroom.logging.LoggingModule(),
                new stroom.pipeline.factory.FactoryModule(),
                new PipelineScopeModule(),
                new stroom.resource.ResourceModule(),
                new stroom.search.shard.ShardModule(),
                new stroom.visualisation.VisualisationModule(),
                new stroom.xmlschema.XmlSchemaModule(),
                new stroom.elastic.ElasticModule(),
                new stroom.kafka.KafkaModule(),
                new stroom.externaldoc.ExternalDocRefModule(),
                new stroom.statistics.spring.PersistenceModule(),
                new stroom.statistics.sql.SQLStatisticModule(),
                new stroom.statistics.sql.rollup.SQLStatisticRollupModule(),
                new stroom.statistics.sql.internal.InternalModule(),
                new stroom.statistics.sql.datasource.DataSourceModule(),
                new stroom.statistics.stroomstats.entity.StroomStatsEntityModule(),
                new stroom.statistics.stroomstats.rollup.StroomStatsRollupModule(),
                new stroom.statistics.stroomstats.internal.InternalModule()
        );

        // Start persistance
        injector.getInstance(PersistService.class).start();

        // Start task manager
        injector.getInstance(TaskManager.class).startup();
    }


    @Before
    public void before() {
//        final Injector childInjector = injector.createChildInjector();
//        childInjector.injectMembers(this);

        injector.injectMembers(this);
        super.before();
    }
//
//    @After
//    public void after() {
//        // Stop task manager
//        injector.getInstance(TaskManager.class).shutdown();
//
//        // Stop persistance
//        injector.getInstance(PersistService.class).stop();
//    }
}
