package stroom.config.global.impl.db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.JooqUtil;
import stroom.util.shared.ModelStringUtil;

import java.time.Duration;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;

@SuppressWarnings("unused")
        // used by FlyWay
public class V07_00_00_002__property_rename extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_002__property_rename.class);

    // Pkg private so we can access it for testing
    static final Map<String, String> FROM_TO_MAP = new HashMap<>();
    static final List<Mapping> MAPPINGS = new ArrayList<>();

    static void addMapping(final String oldName,
                           final String newName,
                           final Function<String, String> serialisationMappingFunc) {
        MAPPINGS.add(new Mapping(oldName, newName, serialisationMappingFunc));
    }

    static void addMapping(final String oldName,
                           final String newName) {
        MAPPINGS.add(new Mapping(oldName, newName));
    }

    static {

        // TODO what do we do about mapping c3po pool props to hikari?
        //   Can we map some/any of them to equiv hikari props?

        addMapping("stroom.aboutHTML", "stroom.ui.aboutHtml");
        addMapping("stroom.activity.chooseOnStartup", "stroom.ui.activity.chooseOnStartup");
        addMapping("stroom.activity.editorBody", "stroom.ui.activity.editorBody");
        addMapping("stroom.activity.editorTitle", "stroom.ui.activity.editorTitle");
        addMapping("stroom.activity.enabled", "stroom.ui.activity.enabled");
        addMapping("stroom.activity.managerTitle", "stroom.ui.activity.managerTitle");
        addMapping("stroom.advertisedUrl", "stroom.ui.url.ui");
        addMapping("stroom.auth.authentication.service.url", "stroom.security.authentication.authenticationServiceUrl");
        addMapping("stroom.auth.jwt.enabletokenrevocationcheck", "stroom.security.authentication.jwt.enableTokenRevocationCheck");
        addMapping("stroom.auth.jwt.issuer", "stroom.security.authentication.jwt.jwtIssuer");
        addMapping("stroom.auth.services.url", "stroom.security.authentication.authServicesBaseUrl");
        addMapping("stroom.authentication.required", "stroom.security.authentication.authenticationRequired");
        // same names in 6 & master
        // stroom.benchmark.concurrentWriters
        // stroom.benchmark.recordCount
        // stroom.benchmark.streamCount
        addMapping("stroom.bufferSize", "stroom.receive.bufferSize");
        addMapping("stroom.clusterCallIgnoreSSLHostnameVerifier", "stroom.cluster.clusterCallIgnoreSSLHostnameVerifier");
        addMapping("stroom.clusterCallReadTimeout", "stroom.cluster.clusterCallReadTimeout");
        addMapping("stroom.clusterCallUseLocal", "stroom.cluster.clusterCallUseLocal");
        addMapping("stroom.clusterResponseTimeout", "stroom.cluster.clusterResponseTimeout");
        addMapping("stroom.contentPackImportEnabled", "stroom.contentPackImport.enabled");
        addMapping("stroom.dashboard.defaultMaxResults", "stroom.ui.defaultMaxResults");
        addMapping("stroom.databaseMultiInsertMaxBatchSize", "stroom.processor.databaseMultiInsertMaxBatchSize");
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
        addMapping("stroom.feedNamePattern", "stroom.feed.feedNamePattern");
        addMapping("stroom.fileSystemCleanBatchSize", "stroom.data.store.fileSystemCleanBatchSize");
        addMapping("stroom.fileSystemCleanDeleteOut", "stroom.data.store.fileSystemCleanDeleteOut");
        addMapping("stroom.fileSystemCleanOldAge", "stroom.data.store.fileSystemCleanOldAge");
        addMapping("stroom.helpUrl", "stroom.ui.helpUrl");

        // same names in 6 & master
        // stroom.index.ramBufferSizeMB
        // stroom.index.writer.cache.coreItems
        // stroom.index.writer.cache.maxItems
        // stroom.index.writer.cache.minItems
        // stroom.index.writer.cache.timeToIdle
        // stroom.index.writer.cache.timeToLive

        addMapping("stroom.internalstatistics.benchmarkCluster.docRefs", "stroom.statistics.internal.benchmarkCluster");
        addMapping("stroom.internalstatistics.cpu.docRefs", "stroom.statistics.internal.cpu");
        addMapping("stroom.internalstatistics.eventsPerSecond.docRefs", "stroom.statistics.internal.eventsPerSecond");
        addMapping("stroom.internalstatistics.heapHistogramBytes.docRefs", "stroom.statistics.internal.heapHistogramBytes");
        addMapping("stroom.internalstatistics.heapHistogramInstances.docRefs", "stroom.statistics.internal.heapHistogramInstances");
        addMapping("stroom.internalstatistics.memory.docRefs", "stroom.statistics.internal.memory");
        addMapping("stroom.internalstatistics.metaDataStreamSize.docRefs", "stroom.statistics.internal.metaDataStreamSize");
        addMapping("stroom.internalstatistics.metaDataStreamsReceived.docRefs", "stroom.statistics.internal.metaDataStreamsReceived");
        addMapping("stroom.internalstatistics.pipelineStreamProcessor.docRefs", "stroom.statistics.internal.pipelineStreamProcessor");
        addMapping("stroom.internalstatistics.streamTaskQueueSize.docRefs", "stroom.statistics.internal.streamTaskQueueSize");
        addMapping("stroom.internalstatistics.volumes.docRefs", "stroom.statistics.internal.volumes");
        addMapping("stroom.jdbcDriverClassName", "stroom.core.db.connection.jdbcDriverClassName");
        addMapping("stroom.jdbcDriverPassword", "stroom.core.db.connection.jdbcDriverPassword");
        addMapping("stroom.jdbcDriverUrl", "stroom.core.db.connection.jdbcDriverUrl");
        addMapping("stroom.jdbcDriverUsername", "stroom.core.db.connection.jdbcDriverUsername");

        // same names in 6 & master
        // stroom.lifecycle.enabled
        // stroom.lifecycle.executionInterval

        // Now defined in a doc entity
        // stroom.kafka.bootstrap.servers

        // Now handled by auth service
        // stroom.loginHTML

        addMapping("stroom.maintenance.message", "stroom.ui.maintenanceMessage");
        addMapping("stroom.maintenance.preventLogin", "stroom.security.authentication.preventLogin");
        addMapping("stroom.maxAggregation", "stroom.proxyAggregation.maxFilesPerAggregate");
        addMapping("stroom.maxAggregationScan", "stroom.proxyAggregation.maxFileScan");
        addMapping("stroom.maxStreamSize", "stroom.proxyAggregation.maxUncompressedFileSize");
        addMapping("stroom.namePattern", "stroom.ui.namePattern");
        addMapping("stroom.node", "stroom.node.name");

        // Same names in 6 & master
        // stroom.node.status.heapHistogram.classNameMatchRegex
        // stroom.node.status.heapHistogram.classNameReplacementRegex
        // stroom.node.status.heapHistogram.jMapExecutable

        addMapping("stroom.pageTitle", "stroom.ui.htmlTitle");

        // Same names in 6 & master
        // stroom.pipeline.appender.maxActiveDestinations
        // stroom.pipeline.xslt.maxElements

        // These are now all part of ProxyConfig that does not get written to the DB and thus has no prop name.
        // stroom.proxy.store.dir ?
        // stroom.proxy.store.format ?
        // stroom.proxy.store.rollCron ?

        addMapping("stroom.proxyDir", "stroom.proxyAggregation.proxyDir");
        addMapping("stroom.proxyThreads", "stroom.proxyAggregation.proxyThreads");
        addMapping("stroom.query.history.daysRetention", "stroom.queryHistory.daysRetention");
        addMapping("stroom.query.history.itemsRetention", "stroom.queryHistory.itemsRetention");
        addMapping("stroom.query.infoPopup.enabled", "stroom.ui.query.infoPopup.enabled");
        addMapping("stroom.query.infoPopup.title", "stroom.ui.query.infoPopup.title");
        addMapping("stroom.query.infoPopup.validationRegex", "stroom.ui.query.infoPopup.validationRegex");

        // Same name in 6 & master
        // stroom.search.impl.extraction.maxThreads
        // stroom.search.impl.extraction.maxThreadsPerTask
        // stroom.search.maxBooleanClauseCount
        // stroom.search.maxStoredDataQueueSize

        addMapping("stroom.search.process.defaultRecordLimit", "stroom.ui.process.defaultRecordLimit");
        addMapping("stroom.search.process.defaultTimeLimit", "stroom.ui.process.defaultTimeLimit");

        // Same name in 6 & master
        // stroom.search.impl.shard.maxDocIdQueueSize
        // stroom.search.impl.shard.maxThreads
        // stroom.search.impl.shard.maxThreadsPerTask
        // stroom.search.storeSize

        addMapping("stroom.security.userNamePattern", "stroom.security.authentication.userNamePattern");
        addMapping("stroom.serviceDiscovery.curator.baseSleepTimeMs", "stroom.serviceDiscovery.curatorBaseSleepTimeMs");
        addMapping("stroom.serviceDiscovery.curator.maxRetries", "stroom.serviceDiscovery.curatorMaxRetries");
        addMapping("stroom.serviceDiscovery.curator.maxSleepTimeMs", "stroom.serviceDiscovery.curatorMaxSleepTimeMs");

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

        addMapping("stroom.services.stroomStats.internalStats.eventsPerMessage", "stroom.statistics.hbase.eventsPerMessage");
        addMapping("stroom.services.stroomStats.kafkaTopics.count", "stroom.statistics.hbase.kafkaTopics.count");
        addMapping("stroom.services.stroomStats.kafkaTopics.value", "stroom.statistics.hbase.kafkaTopics.value");

        addMapping("stroom.splash.body", "stroom.ui.splash.body");
        addMapping("stroom.splash.enabled", "stroom.ui.splash.enabled");
        addMapping("stroom.splash.title", "stroom.ui.splash.title");
        addMapping("stroom.splash.version", "stroom.ui.splash.version");

        // No longer used, stat doc entities are totally separate
        // stroom.statistics.impl.common.statisticEngines

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

        addMapping("stroom.statistics.sql.jdbcDriverClassName", "stroom.statistics.sql.db.connection.jdbcDriverClassName");
        addMapping("stroom.statistics.sql.jdbcDriverPassword", "stroom.statistics.sql.db.connection.jdbcDriverPassword");
        addMapping("stroom.statistics.sql.jdbcDriverUrl", "stroom.statistics.sql.db.connection.jdbcDriverUrl");
        addMapping("stroom.statistics.sql.jdbcDriverUsername", "stroom.statistics.sql.db.connection.jdbcDriverUsername");

        // Same names in 6 & master
        // stroom.statistics.sql.maxProcessingAge
        // stroom.statistics.impl.sql.search.fetchSize
        // stroom.statistics.impl.sql.search.maxResults
        // stroom.statistics.impl.sql.search.resultHandlerBatchSize
        // stroom.statistics.sql.statisticAggregationBatchSize

        // TODO lots of options for these; stroom.data.store, stroom.policy, stroom.process
        // ? stroom.stream.deleteBatchSize ? stroom.data.store.deleteBatchSize
        // ? stroom.stream.deletePurgeAge ? stroom.data.store.deletePurgeAge
        // ? stroom.streamAttribute.deleteAge ? stroom.process.deleteAge
        // ? stroom.streamAttribute.deleteBatchSize ?

        addMapping("stroom.streamTask.assignTasks", "stroom.processor.assignTasks");
        addMapping("stroom.streamTask.createTasks", "stroom.processor.createTasks");

        // TODO lots of options for these; stroom.data.store, stroom.policy, stroom.process
        // ? stroom.streamTask.deleteAge ? stroom.process.deleteAge
        // ? stroom.streamTask.deleteBatchSize ?

        addMapping("stroom.streamTask.fillTaskQueue", "stroom.processor.fillTaskQueue");
        addMapping("stroom.streamTask.queueSize", "stroom.processor.queueSize");
        addMapping("stroom.streamstore.preferLocalVolumes", "stroom.volumes.preferLocalVolumes");
        addMapping("stroom.streamstore.resilientReplicationCount", "stroom.volumes.resilientReplicationCount");
        addMapping("stroom.streamstore.volumeSelector", "stroom.volumes.volumeSelector");
        addMapping("stroom.theme.background-attachment", "stroom.ui.theme.backgroundAttachment");
        addMapping("stroom.theme.background-color", "stroom.ui.theme.backgroundColor");
        addMapping("stroom.theme.background-image", "stroom.ui.theme.backgroundImage");
        addMapping("stroom.theme.background-opacity", "stroom.ui.theme.backgroundOpacity");
        addMapping("stroom.theme.background-position", "stroom.ui.theme.backgroundPosition");
        addMapping("stroom.theme.background-repeat", "stroom.ui.theme.backgroundRepeat");
        addMapping("stroom.theme.labelColours", "stroom.ui.theme.labelColours");
        addMapping("stroom.theme.tube.opacity", "stroom.ui.theme.tubeOpacity");
        addMapping("stroom.theme.tube.visible", "stroom.ui.theme.tubeVisible");
        addMapping("stroom.unknownClassification", "stroom.ui.theme.tubeVisible");

        // Same name in 6 & master
        // stroom.volumes.createDefaultOnStart

        addMapping("stroom.welcomeHTML", "stroom.ui.welcomeHtml");
    }

    @Override
    public void migrate(final Context flywayContext) {
        try {
            final DSLContext context = JooqUtil.createContext(flywayContext.getConnection());

            // This line should only be un-commented for manual testing in development
//            loadTestDataForManualTesting(create);

            // Rename some property names
            MAPPINGS.stream()
                    .sorted(Comparator.comparing(Mapping::getOldName))
                    .forEach(mapping -> {

                        final Optional<ConfigRecord> optRec = context
                                .selectFrom(CONFIG)
                                .where(CONFIG.NAME.eq(mapping.getOldName()))
                                .fetchOptional();

                        optRec.ifPresent(rec -> {
                            // We can't validate the dest key as we don't know if the java model
                            // is applicable to this mig script.
                            LOGGER.info("Renaming DB property {} to {}", mapping.getOldName(), mapping.getNewName());
                            rec.setName(mapping.getNewName());
                            String oldValue = rec.getVal();
                            if (oldValue != null && !oldValue.isEmpty()) {
                                String newValue = mapping.serialisationMappingFunc.apply(oldValue);
                                if (!oldValue.equals(newValue)) {
                                    LOGGER.info("  Changing value of DB property {} from [{}] to [{}]",
                                        mapping.getOldName(), oldValue, newValue);
                                    rec.setVal(newValue);
                                }
                            }
                            rec.store();
                        });
                    });


            // we can't remove keys that are not in the model as there may be more mig scripts
            // after this one that do more migration.  The model is a shifting sand relative to this
            // mig script.

            // Remove property entries that don't map to the object model
//            context
//                    .selectFrom(CONFIG)
//                    .fetch()
//                    .forEach(configRecord -> {
//                        boolean isToPathValid = configMapper.validatePropertyPath(configRecord.getName());
//                        if (!isToPathValid) {
//                            LOGGER.warn("Property {} with value [{}] is not valid a valid property " +
//                                            "in the object model. It will be deleted.",
//                                    configRecord.getName(), configRecord.getVal());
//                            configRecord.delete();
//                        }
//                    });

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

    private static class Mapping {
        private final String oldName;
        private final String newName;
        private final Function<String, String> serialisationMappingFunc;

        private Mapping(final String oldName, final String newName, final Function<String, String> serialisationMappingFunc) {
            this.oldName = oldName;
            this.newName = newName;
            this.serialisationMappingFunc = serialisationMappingFunc;
        }

        private Mapping(final String oldName, final String newName) {
            this(oldName, newName, Function.identity());
        }

        String getOldName() {
            return oldName;
        }

        String getNewName() {
            return newName;
        }

        Function<String, String> getSerialisationMappingFunc() {
            return serialisationMappingFunc;
        }
    }

    /**
     * ModelString e.g. 30d to an ISO-8601 duration string, e.g. P
     * @param oldValue
     * @return
     */
    static String modelStringDurationToDuration(final String oldValue) {
        if (oldValue == null) {
           return null;
        } else if (oldValue.isBlank()) {
            return "";
        } else {
            final Long durationMs = ModelStringUtil.parseDurationString(oldValue);
            return Duration.ofMillis(durationMs).toString();
        }
    }

    /**
     * ModelString e.g. 30d to an ISO-8601 duration string, e.g. P
     * @param oldValue
     * @return
     */
    static String modelStringDurationToPeriod(final String oldValue) {
        if (oldValue == null) {
            return null;
        } else if (oldValue.isBlank()) {
            return "";
        } else {
            final Long durationMs = ModelStringUtil.parseDurationString(oldValue);

            return Period.ofDays((int) Duration.ofMillis(durationMs).toDays()).toString();
        }
    }
}
