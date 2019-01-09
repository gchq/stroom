package stroom.config.global.impl.db.migration;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.impl.db.ConfigMapper;
import stroom.config.impl.db.stroom.tables.records.ConfigRecord;
import stroom.util.logging.LambdaLogger;

import java.sql.Connection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static stroom.config.impl.db.stroom.tables.Config.CONFIG;

@SuppressWarnings("unused")
        // used by FlyWay
public class V7_0_0_2__property_rename implements JdbcMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V7_0_0_2__property_rename.class);

    private static final Map<String, String> FROM_TO_MAP = new HashMap<>();

    static {

        // TODO what do we do about mapping c3po pool props to hikari?

        FROM_TO_MAP.put("stroom.aboutHTML", "stroom.ui.aboutHtml");
        FROM_TO_MAP.put("stroom.activity.chooseOnStartup", "stroom.ui.activity.chooseOnStartup");
        FROM_TO_MAP.put("stroom.activity.editorBody", "stroom.ui.activity.editorBody");
        FROM_TO_MAP.put("stroom.activity.editorTitle", "stroom.ui.activity.editorTitle");
        FROM_TO_MAP.put("stroom.activity.enabled", "stroom.ui.activity.enabled");
        FROM_TO_MAP.put("stroom.activity.managerTitle", "stroom.ui.activity.managerTitle");
        FROM_TO_MAP.put("stroom.advertisedUrl", "stroom.ui.url.ui");
        FROM_TO_MAP.put("stroom.auth.authentication.service.url", "stroom.security.authentication.authenticationServiceUrl");
        FROM_TO_MAP.put("stroom.auth.jwt.enabletokenrevocationcheck", "stroom.security.authentication.jwt.enableTokenRevocationCheck");
        FROM_TO_MAP.put("stroom.auth.jwt.issuer", "stroom.security.authentication.jwt.jwtIssuer");
        FROM_TO_MAP.put("stroom.auth.services.url", "stroom.security.authentication.authServicesBaseUrl");
        FROM_TO_MAP.put("stroom.authentication.required", "stroom.security.authentication.authenticationRequired");
        // same names in 6 & master
        // stroom.benchmark.concurrentWriters
        // stroom.benchmark.recordCount
        // stroom.benchmark.streamCount
        FROM_TO_MAP.put("stroom.bufferSize", "stroom.feed.bufferSize");
        FROM_TO_MAP.put("stroom.clusterCallIgnoreSSLHostnameVerifier", "stroom.cluster.clusterCallIgnoreSSLHostnameVerifier");
        FROM_TO_MAP.put("stroom.clusterCallReadTimeout", "stroom.cluster.clusterCallReadTimeout");
        FROM_TO_MAP.put("stroom.clusterCallUseLocal", "stroom.cluster.clusterCallUseLocal");
        FROM_TO_MAP.put("stroom.clusterResponseTimeout", "stroom.clusterResponseTimeout");
        FROM_TO_MAP.put("stroom.contentPackImportEnabled", "stroom.contentPackImport.enabled");
        FROM_TO_MAP.put("stroom.dashboard.defaultMaxResults", "stroom.ui.defaultMaxResults");
        FROM_TO_MAP.put("stroom.databaseMultiInsertMaxBatchSize", "stroom.core.databaseMultiInsertMaxBatchSize");
        // TODO how to map connection pool props?
        // stroom.db.connectionPool.acquireIncrement
        // stroom.db.connectionPool.acquireRetryAttempts
        // stroom.db.connectionPool.acquireRetryDelay
        // stroom.db.connectionPool.checkoutTimeout
        // stroom.db.connectionPool.idleConnectionTestPeriod
        // stroom.db.connectionPool.initialPoolSize
        // stroom.db.connectionPool.maxConnectionAge
        // stroom.db.connectionPool.maxIdleTime
        // stroom.db.connectionPool.maxIdleTimeExcessConnections
        // stroom.db.connectionPool.maxPoolSize
        // stroom.db.connectionPool.minPoolSize
        // stroom.db.connectionPool.numHelperThreads
        // stroom.db.connectionPool.unreturnedConnectionTimeout
        // stroom.export.enabled
        // stroom.feed.receiptPolicyUuid
        FROM_TO_MAP.put("stroom.feedNamePattern", "stroom.feed.feedNamePattern");
        FROM_TO_MAP.put("stroom.fileSystemCleanBatchSize", "stroom.data.store.fileSystemCleanBatchSize");
        FROM_TO_MAP.put("stroom.fileSystemCleanDeleteOut", "stroom.data.store.fileSystemCleanDeleteOut");
        FROM_TO_MAP.put("stroom.fileSystemCleanOldAge", "stroom.data.store.fileSystemCleanOldAge");
        FROM_TO_MAP.put("stroom.helpUrl", "stroom.ui.helpUrl");

        // same names in 6 & master
        // stroom.index.ramBufferSizeMB
        // stroom.index.writer.cache.coreItems
        // stroom.index.writer.cache.maxItems
        // stroom.index.writer.cache.minItems
        // stroom.index.writer.cache.timeToIdle
        // stroom.index.writer.cache.timeToLive

        FROM_TO_MAP.put("stroom.internalstatistics.benchmarkCluster.docRefs", "stroom.statistics.internal.benchmarkCluster");
        FROM_TO_MAP.put("stroom.internalstatistics.cpu.docRefs", "stroom.statistics.internal.cpu");
        FROM_TO_MAP.put("stroom.internalstatistics.eventsPerSecond.docRefs", "stroom.statistics.internal.eventsPerSecond");
        FROM_TO_MAP.put("stroom.internalstatistics.heapHistogramBytes.docRefs", "stroom.statistics.internal.heapHistogramBytes");
        FROM_TO_MAP.put("stroom.internalstatistics.heapHistogramInstances.docRefs", "stroom.statistics.internal.heapHistogramInstances");
        FROM_TO_MAP.put("stroom.internalstatistics.memory.docRefs", "stroom.statistics.internal.memory");
        FROM_TO_MAP.put("stroom.internalstatistics.metaDataStreamSize.docRefs", "stroom.statistics.internal.metaDataStreamSize");
        FROM_TO_MAP.put("stroom.internalstatistics.metaDataStreamsReceived.docRefs", "stroom.statistics.internal.metaDataStreamsReceived");
        FROM_TO_MAP.put("stroom.internalstatistics.pipelineStreamProcessor.docRefs", "stroom.statistics.internal.pipelineStreamProcessor");
        FROM_TO_MAP.put("stroom.internalstatistics.streamTaskQueueSize.docRefs", "stroom.statistics.internal.streamTaskQueueSize");
        FROM_TO_MAP.put("stroom.internalstatistics.volumes.docRefs", "stroom.statistics.internal.volumes");
        FROM_TO_MAP.put("stroom.jdbcDriverClassName", "stroom.core.connection.jdbcDriverClassName");
        FROM_TO_MAP.put("stroom.jdbcDriverPassword", "stroom.core.connection.jdbcDriverPassword");
        FROM_TO_MAP.put("stroom.jdbcDriverUrl", "stroom.core.connection.jdbcDriverUrl");
        FROM_TO_MAP.put("stroom.jdbcDriverUsername", "stroom.core.connection.jdbcDriverUsername");
        FROM_TO_MAP.put("stroom.jpaDialect", "stroom.core.hibernate.dialect");
        FROM_TO_MAP.put("stroom.jpaHbm2DdlAuto", "stroom.core.hibernate.jpaHbm2DdlAuto");

        // same names in 6 & master
        // stroom.lifecycle.enabled
        // stroom.lifecycle.executionInterval

        // Now defined in a doc entity
        // stroom.kafka.bootstrap.servers

        // Now handled by auth service
        // stroom.loginHTML

        FROM_TO_MAP.put("stroom.maintenance.message", "stroom.ui.maintenanceMessage");
        FROM_TO_MAP.put("stroom.maintenance.preventLogin", "stroom.security.authentication.preventLogin");
        FROM_TO_MAP.put("stroom.maxAggregation", "stroom.proxyAggregation.maxAggregation");
        FROM_TO_MAP.put("stroom.maxAggregationScan", "stroom.proxyAggregation.maxAggregationScan");
        FROM_TO_MAP.put("stroom.maxStreamSize", "stroom.proxyAggregation.maxStreamSize");
        FROM_TO_MAP.put("stroom.namePattern", "stroom.ui.namePattern");
        FROM_TO_MAP.put("stroom.node", "stroom.node.node");

        // Same names in 6 & master
        // stroom.node.status.heapHistogram.classNameMatchRegex
        // stroom.node.status.heapHistogram.classNameReplacementRegex
        // stroom.node.status.heapHistogram.jMapExecutable

        FROM_TO_MAP.put("stroom.pageTitle", "?");
        // Same names in 6 & master
        // stroom.pipeline.appender.maxActiveDestinations
        // stroom.pipeline.xslt.maxElements

        // These are now all part of ProxyConfig that does not get written to the DB and thus has no prop name.
        // stroom.proxy.store.dir ?
        // stroom.proxy.store.format ?
        // stroom.proxy.store.rollCron ?

        FROM_TO_MAP.put("stroom.proxyDir", "stroom.proxyAggregation.proxyDir");
        FROM_TO_MAP.put("stroom.proxyThreads", "stroom.proxyAggregation.proxyThreads");
        FROM_TO_MAP.put("stroom.query.history.daysRetention", "stroom.queryHistory.daysRetention");
        FROM_TO_MAP.put("stroom.query.history.itemsRetention", "stroom.queryHistory.itemsRetention");
        FROM_TO_MAP.put("stroom.query.infoPopup.enabled", "stroom.ui.query.infoPopup.enabled");
        FROM_TO_MAP.put("stroom.query.infoPopup.title", "stroom.ui.query.infoPopup.title");
        FROM_TO_MAP.put("stroom.query.infoPopup.validationRegex", "stroom.ui.query.infoPopup.validationRegex");
        FROM_TO_MAP.put("stroom.rack", "stroom.node.rack");

        // Same name in 6 & master
        // stroom.search.extraction.maxThreads
        // stroom.search.extraction.maxThreadsPerTask
        // stroom.search.maxBooleanClauseCount
        // stroom.search.maxStoredDataQueueSize

        FROM_TO_MAP.put("stroom.search.process.defaultRecordLimit", "stroom.ui.process.defaultRecordLimit");
        FROM_TO_MAP.put("stroom.search.process.defaultTimeLimit", "stroom.ui.process.defaultTimeLimit");

        // Same name in 6 & master
        // stroom.search.shard.maxDocIdQueueSize
        // stroom.search.shard.maxThreads
        // stroom.search.shard.maxThreadsPerTask
        // stroom.search.storeSize

        FROM_TO_MAP.put("stroom.security.apitoken", "stroom.security.authentication.apiToken");
        FROM_TO_MAP.put("stroom.security.userNamePattern", "stroom.security.authentication.userNamePattern");
        FROM_TO_MAP.put("stroom.serviceDiscovery.curator.baseSleepTimeMs", "stroom.serviceDiscovery.curatorBaseSleepTimeMs");
        FROM_TO_MAP.put("stroom.serviceDiscovery.curator.maxRetries", "stroom.serviceDiscovery.curatorMaxRetries");
        FROM_TO_MAP.put("stroom.serviceDiscovery.curator.maxSleepTimeMs", "stroom.serviceDiscovery.curatorMaxSleepTimeMs");

        // TODO need to figure out what we are doing with service disco
        // stroom.serviceDiscovery.enabled
        // stroom.serviceDiscovery.servicesHostNameOrIpAddress
        // stroom.serviceDiscovery.servicesPort
        // ? stroom.serviceDiscovery.simpleLookup.basePath
        // stroom.serviceDiscovery.zookeeperBasePath
        // stroom.serviceDiscovery.zookeeperUrl
        // stroom.services.authentication.docRefType
        // stroom.services.authentication.name
        // stroom.services.authentication.version
        // stroom.services.authorisation.docRefType
        // stroom.services.authorisation.name
        // stroom.services.authorisation.version
        // ? stroom.services.sqlStatistics.docRefType ?
        // ? stroom.services.sqlStatistics.name ?
        // ? stroom.services.sqlStatistics.version ?
        // ? stroom.services.stroomIndex.docRefType ?
        // ? stroom.services.stroomIndex.name ?
        // ? stroom.services.stroomIndex.version ?
        // ? stroom.services.stroomStats.docRefType ?
        // ? stroom.services.stroomStats.name ?
        // ? stroom.services.stroomStats.version ?

        FROM_TO_MAP.put("stroom.services.stroomStats.internalStats.eventsPerMessage", "stroom.statistics.hbase.eventsPerMessage");
        FROM_TO_MAP.put("stroom.services.stroomStats.kafkaTopics.count", "stroom.statistics.hbase.kafkaTopics.count");
        FROM_TO_MAP.put("stroom.services.stroomStats.kafkaTopics.value", "stroom.statistics.hbase.kafkaTopics.value");
        FROM_TO_MAP.put("stroom.showSql", "stroom.core.hibernate.showSql");
        FROM_TO_MAP.put("stroom.splash.body", "stroom.ui.splash.body");
        FROM_TO_MAP.put("stroom.splash.enabled", "stroom.ui.splash.enabled");
        FROM_TO_MAP.put("stroom.splash.title", "stroom.ui.splash.title");
        FROM_TO_MAP.put("stroom.splash.version", "stroom.ui.splash.version");

        // No longer used, stat doc entities are totally separate
        // stroom.statistics.common.statisticEngines

        // TODO what to do about
        // ? stroom.statistics.sql.db.connectionPool.acquireIncrement
        // ? stroom.statistics.sql.db.connectionPool.acquireRetryAttempts
        // ? stroom.statistics.sql.db.connectionPool.acquireRetryDelay
        // ? stroom.statistics.sql.db.connectionPool.checkoutTimeout
        // ? stroom.statistics.sql.db.connectionPool.idleConnectionTestPeriod
        // ? stroom.statistics.sql.db.connectionPool.initialPoolSize
        // ? stroom.statistics.sql.db.connectionPool.maxConnectionAge
        // ? stroom.statistics.sql.db.connectionPool.maxIdleTime
        // ? stroom.statistics.sql.db.connectionPool.maxIdleTimeExcessConnections
        // ? stroom.statistics.sql.db.connectionPool.maxPoolSize
        // ? stroom.statistics.sql.db.connectionPool.minPoolSize
        // ? stroom.statistics.sql.db.connectionPool.numHelperThreads
        // ? stroom.statistics.sql.db.connectionPool.unreturnedConnectionTimeout

        FROM_TO_MAP.put("stroom.statistics.sql.jdbcDriverClassName", "stroom.statistics.sql.connection.jdbcDriverClassName");
        FROM_TO_MAP.put("stroom.statistics.sql.jdbcDriverPassword", "stroom.statistics.sql.connection.jdbcDriverPassword");
        FROM_TO_MAP.put("stroom.statistics.sql.jdbcDriverUrl", "stroom.statistics.sql.connection.jdbcDriverUrl");
        FROM_TO_MAP.put("stroom.statistics.sql.jdbcDriverUsername", "stroom.statistics.sql.connection.jdbcDriverUsername");

        // Same names in 6 & master
        // stroom.statistics.sql.maxProcessingAge
        // stroom.statistics.sql.search.fetchSize
        // stroom.statistics.sql.search.maxResults
        // stroom.statistics.sql.search.resultHandlerBatchSize
        // stroom.statistics.sql.statisticAggregationBatchSize

        // TODO lots of options for these; stroom.data.store, stroom.policy, stroom.process
        // ? stroom.stream.deleteBatchSize ? stroom.data.store.deleteBatchSize
        // ? stroom.stream.deletePurgeAge ? stroom.data.store.deletePurgeAge
        // ? stroom.streamAttribute.deleteAge ? stroom.processor.deleteAge
        // ? stroom.streamAttribute.deleteBatchSize ?

        FROM_TO_MAP.put("stroom.streamTask.assignTasks", "stroom.processor.assignTasks");
        FROM_TO_MAP.put("stroom.streamTask.createTasks", "stroom.processor.createTasks");

        // TODO lots of options for these; stroom.data.store, stroom.policy, stroom.process
        // ? stroom.streamTask.deleteAge ? stroom.processor.deleteAge
        // ? stroom.streamTask.deleteBatchSize ?

        FROM_TO_MAP.put("stroom.streamTask.fillTaskQueue", "stroom.processor.fillTaskQueue");
        FROM_TO_MAP.put("stroom.streamTask.queueSize", "stroom.processor.queueSize");
        FROM_TO_MAP.put("stroom.streamstore.preferLocalVolumes", "stroom.volumes.preferLocalVolumes");
        FROM_TO_MAP.put("stroom.streamstore.resilientReplicationCount", "stroom.volumes.resilientReplicationCount");
        FROM_TO_MAP.put("stroom.streamstore.volumeSelector", "stroom.volumes.volumeSelector");
        FROM_TO_MAP.put("stroom.theme.background-attachment", "stroom.ui.theme.backgroundAttachment");
        FROM_TO_MAP.put("stroom.theme.background-color", "stroom.ui.theme.backgroundColor");
        FROM_TO_MAP.put("stroom.theme.background-image", "stroom.ui.theme.backgroundImage");
        FROM_TO_MAP.put("stroom.theme.background-opacity", "stroom.ui.theme.backgroundOpacity");
        FROM_TO_MAP.put("stroom.theme.background-position", "stroom.ui.theme.backgroundPosition");
        FROM_TO_MAP.put("stroom.theme.background-repeat", "stroom.ui.theme.backgroundRepeat");
        FROM_TO_MAP.put("stroom.theme.labelColours", "stroom.ui.theme.labelColours");
        FROM_TO_MAP.put("stroom.theme.tube.opacity", "stroom.ui.theme.tubeOpacity");
        FROM_TO_MAP.put("stroom.theme.tube.visible", "stroom.ui.theme.tubeVisible");
        FROM_TO_MAP.put("stroom.unknownClassification", "stroom.ui.theme.tubeVisible");

        // Same name in 6 & master
        // stroom.volumes.createDefaultOnStart

        FROM_TO_MAP.put("stroom.welcomeHTML", "stroom.ui.welcomeHtml");
    }

    private final ConfigMapper configMapper;

    public V7_0_0_2__property_rename() {

        // initialise the config mapper so we can validate property keys
        configMapper = new ConfigMapper(new AppConfig());
    }

    @Override
    public void migrate(final Connection connection) throws Exception {
        try {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            // This line should only be un-commented for manual testing in development
//            loadTestDataForManualTesting(create);

            // Rename some property names
            FROM_TO_MAP.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> {
                        final String from = entry.getKey();
                        final String to = entry.getValue();

                        final Optional<ConfigRecord> optRec = create
                                .selectFrom(CONFIG)
                                .where(CONFIG.NAME.eq(from))
                                .fetchOptional();

                        optRec.ifPresent(rec -> {
                            LOGGER.info("Renaming DB property {} to {}", from, to);
                            boolean isToPathValid = configMapper.validatePropertyPath(to);
                            if (!isToPathValid) {
                                throw new RuntimeException(LambdaLogger.buildMessage(
                                        "Property name {} is not valid in the object model",
                                        to));
                            }
                            rec.setName(to);
                            rec.store();
                        });
                    });

            // Remove property entries that don't map to the object model
            create
                    .selectFrom(CONFIG)
                    .fetch()
                    .forEach(configRecord -> {
                        boolean isToPathValid = configMapper.validatePropertyPath(configRecord.getName());
                        if (!isToPathValid) {
                            LOGGER.warn("Property {} with value [{}] is not valid a valid property " +
                                            "in the object model. It will be deleted.",
                                    configRecord.getName(), configRecord.getVal());
                            configRecord.delete();
                        }
                    });

        } catch (final RuntimeException e) {
            LOGGER.error("Error renaming property", e);
            throw e;
        }
    }

    private void loadTestDataForManualTesting(final DSLContext create) {

        LOGGER.warn("Loading test data - Not for use in prod");
        final Map<String, String> testDataMap = Map.of(

                "stroom.aboutHTML", "myHtml",
                "stroom.advertisedUrl", "myUrl",
                "stroom.unknownProp", "some value"
        );

        testDataMap.forEach((key, value) ->
                create
                        .insertInto(CONFIG)
                        .columns(CONFIG.NAME, CONFIG.VAL)
                        .values(key, value)
                        .execute());
    }
}
