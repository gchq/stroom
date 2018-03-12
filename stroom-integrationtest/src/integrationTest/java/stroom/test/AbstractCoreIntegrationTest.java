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
import org.junit.Before;
import stroom.statistics.sql.SQLStatisticModule;
import stroom.statistics.sql.internal.InternalModule;
import stroom.statistics.sql.rollup.SQLStatisticRollupModule;
import stroom.statistics.stroomstats.entity.StroomStatsEntityModule;

public abstract class AbstractCoreIntegrationTest extends StroomIntegrationTest {
    @Before
    public void before() {


////        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
////        dataSource.setPassword();
////
////        final C3P0Config config = new C3P0Config("stroom.db.connectionPool.", stroomPropertyService);
////        dataSource.setMaxStatements(config.getMaxStatements());
////        dataSource.setMaxStatementsPerConnection(config.getMaxStatementsPerConnection());
////        dataSource.setInitialPoolSize(config.getInitialPoolSize());
////        dataSource.setMinPoolSize(config.getMinPoolSize());
////        dataSource.setMaxPoolSize(config.getMaxPoolSize());
////        dataSource.setIdleConnectionTestPeriod(config.getIdleConnectionTestPeriod());
////        dataSource.setMaxIdleTime(config.getMaxIdleTime());
////        dataSource.setAcquireIncrement(config.getAcquireIncrement());
////        dataSource.setAcquireRetryAttempts(config.getAcquireRetryAttempts());
////        dataSource.setAcquireRetryDelay(config.getAcquireRetryDelay());
////        dataSource.setCheckoutTimeout(config.getCheckoutTimeout());
////        dataSource.setMaxAdministrativeTaskTime(config.getMaxAdministrativeTaskTime());
////        dataSource.setMaxIdleTimeExcessConnections(config.getMaxIdleTimeExcessConnections());
////        dataSource.setMaxConnectionAge(config.getMaxConnectionAge());
////        dataSource.setUnreturnedConnectionTimeout(config.getUnreturnedConnectionTimeout());
////        dataSource.setStatementCacheNumDeferredCloseThreads(config.getStatementCacheNumDeferredCloseThreads());
////        dataSource.setNumHelperThreads(config.getNumHelperThreads());
////
////        dataSource.setPreferredTestQuery("select 1");
////        dataSource.setConnectionTesterClassName(StroomProperties.getProperty("stroom.connectionTesterClassName"));
//
//
//        final Map<String, String> properties = new HashMap<>();
//        properties.put("hibernate.connection.driver_class", StroomProperties.getProperty("stroom.jdbcDriverClassName"));
//        properties.put("hibernate.connection.url", StroomProperties.getProperty("stroom.jdbcDriverUrl|trace"));
//        properties.put("hibernate.connection.username", StroomProperties.getProperty("stroom.jdbcDriverUsername"));
//        properties.put("hibernate.connection.password", StroomProperties.getProperty("stroom.jdbcDriverPassword"));
//
//        properties.put("hibernate.hbm2ddl.auto", StroomProperties.getProperty("stroom.jpaHbm2DdlAuto"));
//        properties.put("hibernate.show_sql", StroomProperties.getProperty("stroom.showSql"));
//        properties.put("hibernate.dialect", StroomProperties.getProperty("stroom.jpaDialect"));
//
//
////        properties.put("hibernate.dialect" ,"org.hibernate.dialect.MySQLDialect" );
//////properties.put("hibernate.hbm2ddl.auto" ,"create-drop" );
//////
//////properties.put("hibernate.connection.provider_class"
//////        ,"org.hibernate.connection.C3P0ConnectionProvider" );
//////
//////properties.put("hibernate.c3p0.max_size" ,"100" );
//////properties.put("hibernate.c3p0.min_size" ,"0" );
//////properties.put("hibernate.c3p0.acquire_increment" ,"1" );
//////properties.put("hibernate.c3p0.idle_test_period" ,"300" );
//////properties.put("hibernate.c3p0.max_statements" ,"0" );
//////properties.put("hibernate.c3p0.timeout" ,"100" );
//
//        // C3P0
//        final C3P0Config config = new C3P0Config("stroom.db.connectionPool.", new StroomPropertyServiceImpl());
//
//        //        dataSource.setMaxStatements(config.getMaxStatements());
//        properties.put("hibernate.c3p0.max_statements", String.valueOf(config.getMaxStatements()));
//
////        dataSource.setMaxStatementsPerConnection(config.getMaxStatementsPerConnection());
//        properties.put("hibernate.c3p0.max_statements_per_connection", String.valueOf(config.getMaxStatementsPerConnection()));
////        dataSource.setInitialPoolSize(config.getInitialPoolSize());
//        properties.put("hibernate.c3p0.initial_pool_size", String.valueOf(config.getInitialPoolSize()));
////        dataSource.setMinPoolSize(config.getMinPoolSize());
//        properties.put("hibernate.c3p0.min_pool_size", String.valueOf(config.getMinPoolSize()));
////        dataSource.setMaxPoolSize(config.getMaxPoolSize());
//        properties.put("hibernate.c3p0.max_pool_size", String.valueOf(config.getMaxPoolSize()));
////        dataSource.setIdleConnectionTestPeriod(config.getIdleConnectionTestPeriod());
//        properties.put("hibernate.c3p0.idle_connection_test_period", String.valueOf(config.getIdleConnectionTestPeriod()));
////        dataSource.setMaxIdleTime(config.getMaxIdleTime());
//        properties.put("hibernate.c3p0.max_idle_time", String.valueOf(config.getMaxIdleTime()));
////        dataSource.setAcquireIncrement(config.getAcquireIncrement());
//        properties.put("hibernate.c3p0.acquire_increment", String.valueOf(config.getAcquireIncrement()));
////        dataSource.setAcquireRetryAttempts(config.getAcquireRetryAttempts());
//        properties.put("hibernate.c3p0.acquire_retry_attempts", String.valueOf(config.getAcquireRetryAttempts()));
////        dataSource.setAcquireRetryDelay(config.getAcquireRetryDelay());
//        properties.put("hibernate.c3p0.acquire_retry_delay", String.valueOf(config.getAcquireRetryDelay()));
////        dataSource.setCheckoutTimeout(config.getCheckoutTimeout());
//        properties.put("hibernate.c3p0.checkout_timeout", String.valueOf(config.getCheckoutTimeout()));
////        dataSource.setMaxAdministrativeTaskTime(config.getMaxAdministrativeTaskTime());
//        properties.put("hibernate.c3p0.max_administrative_task_time", String.valueOf(config.getMaxAdministrativeTaskTime()));
////        dataSource.setMaxIdleTimeExcessConnections(config.getMaxIdleTimeExcessConnections());
//        properties.put("hibernate.c3p0.max_idle_time_excess_connections", String.valueOf(config.getMaxIdleTimeExcessConnections()));
////        dataSource.setMaxConnectionAge(config.getMaxConnectionAge());
//        properties.put("hibernate.c3p0.max_connection_age", String.valueOf(config.getMaxConnectionAge()));
////        dataSource.setUnreturnedConnectionTimeout(config.getUnreturnedConnectionTimeout());
//        properties.put("hibernate.c3p0.unreturned_connection_timeout", String.valueOf(config.getUnreturnedConnectionTimeout()));
////        dataSource.setStatementCacheNumDeferredCloseThreads(config.getStatementCacheNumDeferredCloseThreads());
//        properties.put("hibernate.c3p0.statement_cache_num_deferred_close_threads", String.valueOf(config.getStatementCacheNumDeferredCloseThreads()));
////        dataSource.setNumHelperThreads(config.getNumHelperThreads());
//        properties.put("hibernate.c3p0.num_helper_threads", String.valueOf(config.getNumHelperThreads()));
////
////        dataSource.setPreferredTestQuery("select 1");
//        properties.put("hibernate.c3p0.preferred_test_query", "select 1");
////        dataSource.setConnectionTesterClassName(StroomProperties.getProperty("stroom.connectionTesterClassName"));
//        properties.put("hibernate.c3p0.connection_tester_class_name", StroomProperties.getProperty("stroom.connectionTesterClassName"));


        final Injector injector = Guice.createInjector(
                new stroom.entity.EntityModule(),
                new stroom.datafeed.DataFeedModule(),
                new stroom.security.MockSecurityContextModule(),
                new stroom.ruleset.RulesetModule(),
                new stroom.dictionary.DictionaryModule(),
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
                new stroom.volume.VolumeModule(),
                new stroom.statistics.internal.InternalStatisticsModule(),
                new stroom.streamstore.StreamStoreModule(),
                new stroom.streamstore.fs.FSModule(),
                new stroom.streamtask.StreamTaskModule(),
                new stroom.task.TaskModule(),
                new stroom.task.cluster.ClusterTaskModule(),
                new stroom.jobsystem.JobSystemModule(),
                new stroom.pipeline.PipelineModule(),
                new stroom.cache.PipelineCacheModule(),
                new stroom.pipeline.stepping.PipelineSteppingModule(),
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
                new stroom.util.guice.PipelineScopeModule(),
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
        injector.injectMembers(this);

        super.before();
    }
}
