package stroom.config.global.impl.db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.JooqUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;

@SuppressWarnings("unused") // used by FlyWay
public class V07_00_00_002__property_rename extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_002__property_rename.class);

    // Pkg private so we can access it for testing
    static final Map<String, String> FROM_TO_MAP = new HashMap<>();
    static final List<Mapping> MAPPINGS = new ArrayList<>();

    // The mapping funcs are held as variables to save using the fully qualified name
    private static final Function<String, String> MSU_2_DURATION =
        V07_00_00_002__property_rename::modelStringDurationToDuration;
    private static final Function<String, String> MSU_2_STROOM_DURATION =
        V07_00_00_002__property_rename::modelStringDurationToStroomDuration;

    static void map(final String oldName,
                    final String newName,
                    final Function<String, String> serialisationMappingFunc) {
        MAPPINGS.add(new Mapping(oldName, newName, serialisationMappingFunc));
    }

    static void map(final String name,
                    final Function<String, String> serialisationMappingFunc) {
        MAPPINGS.add(new Mapping(name, name, serialisationMappingFunc));
    }

    static void map(final String oldName,
                    final String newName) {
        MAPPINGS.add(new Mapping(oldName, newName));
    }

    static {

        // TODO what do we do about mapping c3po pool props to hikari?
        //   Can we map some/any of them to equiv hikari props?

        map("stroom.aboutHTML", "stroom.ui.aboutHtml");
        map("stroom.activity.chooseOnStartup", "stroom.ui.activity.chooseOnStartup");
        map("stroom.activity.editorBody", "stroom.ui.activity.editorBody");
        map("stroom.activity.editorTitle", "stroom.ui.activity.editorTitle");
        map("stroom.activity.enabled", "stroom.ui.activity.enabled");
        map("stroom.activity.managerTitle", "stroom.ui.activity.managerTitle");
        map("stroom.advertisedUrl", "stroom.ui.url.ui");
        map("stroom.auth.authentication.service.url", "stroom.security.authentication.authenticationServiceUrl");
        map("stroom.auth.jwt.enabletokenrevocationcheck", "stroom.security.authentication.jwt.enableTokenRevocationCheck");
        map("stroom.auth.jwt.issuer", "stroom.security.authentication.jwt.jwtIssuer");
        map("stroom.auth.services.url", "stroom.security.authentication.authServicesBaseUrl");
        map("stroom.authentication.required", "stroom.security.authentication.authenticationRequired");
        // same names in 6 & master
        // stroom.benchmark.concurrentWriters
        // stroom.benchmark.recordCount
        // stroom.benchmark.streamCount
        map("stroom.bufferSize", "stroom.receive.bufferSize");
        map("stroom.clusterCallIgnoreSSLHostnameVerifier", "stroom.cluster.clusterCallIgnoreSSLHostnameVerifier");
        map("stroom.clusterCallReadTimeout", "stroom.cluster.clusterCallReadTimeout", MSU_2_STROOM_DURATION);
        map("stroom.clusterCallUseLocal", "stroom.cluster.clusterCallUseLocal");
        map("stroom.clusterResponseTimeout", "stroom.cluster.clusterResponseTimeout", MSU_2_STROOM_DURATION);
        map("stroom.contentPackImportEnabled", "stroom.contentPackImport.enabled");
        map("stroom.dashboard.defaultMaxResults", "stroom.ui.defaultMaxResults");
        map("stroom.databaseMultiInsertMaxBatchSize", "stroom.processor.databaseMultiInsertMaxBatchSize");
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
        map("stroom.feedNamePattern", "stroom.feed.feedNamePattern");
        map("stroom.fileSystemCleanBatchSize", "stroom.data.store.fileSystemCleanBatchSize");
        map("stroom.fileSystemCleanDeleteOut", "stroom.data.store.fileSystemCleanDeleteOut");
        map("stroom.fileSystemCleanOldAge", "stroom.data.store.fileSystemCleanOldAge", MSU_2_STROOM_DURATION);
        map("stroom.helpUrl", "stroom.ui.helpUrl");

        // same names in 6 & master
        // stroom.index.ramBufferSizeMB
        // stroom.index.writer.cache.coreItems
        // stroom.index.writer.cache.maxItems
        // stroom.index.writer.cache.minItems
        // stroom.index.writer.cache.timeToIdle
        // stroom.index.writer.cache.timeToLive

        map("stroom.internalstatistics.benchmarkCluster.docRefs", "stroom.statistics.internal.benchmarkCluster");
        map("stroom.internalstatistics.cpu.docRefs", "stroom.statistics.internal.cpu");
        map("stroom.internalstatistics.eventsPerSecond.docRefs", "stroom.statistics.internal.eventsPerSecond");
        map("stroom.internalstatistics.heapHistogramBytes.docRefs", "stroom.statistics.internal.heapHistogramBytes");
        map("stroom.internalstatistics.heapHistogramInstances.docRefs", "stroom.statistics.internal.heapHistogramInstances");
        map("stroom.internalstatistics.memory.docRefs", "stroom.statistics.internal.memory");
        map("stroom.internalstatistics.metaDataStreamSize.docRefs", "stroom.statistics.internal.metaDataStreamSize");
        map("stroom.internalstatistics.metaDataStreamsReceived.docRefs", "stroom.statistics.internal.metaDataStreamsReceived");
        map("stroom.internalstatistics.pipelineStreamProcessor.docRefs", "stroom.statistics.internal.pipelineStreamProcessor");
        map("stroom.internalstatistics.streamTaskQueueSize.docRefs", "stroom.statistics.internal.streamTaskQueueSize");
        map("stroom.internalstatistics.volumes.docRefs", "stroom.statistics.internal.volumes");
        map("stroom.jdbcDriverClassName", "stroom.core.db.connection.jdbcDriverClassName");
        map("stroom.jdbcDriverPassword", "stroom.core.db.connection.jdbcDriverPassword");
        map("stroom.jdbcDriverUrl", "stroom.core.db.connection.jdbcDriverUrl");
        map("stroom.jdbcDriverUsername", "stroom.core.db.connection.jdbcDriverUsername");

        // same names in 6 & master
        // stroom.lifecycle.enabled
        // stroom.lifecycle.executionInterval

        // Now defined in a doc entity
        // stroom.kafka.bootstrap.servers

        // Now handled by auth service
        // stroom.loginHTML

        map("stroom.maintenance.message", "stroom.ui.maintenanceMessage");
        map("stroom.maintenance.preventLogin", "stroom.security.authentication.preventLogin");
        map("stroom.maxAggregation", "stroom.proxyAggregation.maxFilesPerAggregate");
        map("stroom.maxAggregationScan", "stroom.proxyAggregation.maxFileScan");
        map("stroom.maxStreamSize", "stroom.proxyAggregation.maxUncompressedFileSize");
        map("stroom.namePattern", "stroom.ui.namePattern");
        map("stroom.node", "stroom.node.name");

        // Same names in 6 & master
        // stroom.node.status.heapHistogram.classNameMatchRegex
        // stroom.node.status.heapHistogram.classNameReplacementRegex
        // stroom.node.status.heapHistogram.jMapExecutable

        map("stroom.pageTitle", "stroom.ui.htmlTitle");

        // Same names in 6 & master
        // stroom.pipeline.appender.maxActiveDestinations
        // stroom.pipeline.xslt.maxElements

        // These are now all part of ProxyConfig that does not get written to the DB and thus has no prop name.
        // stroom.proxy.store.dir ?
        // stroom.proxy.store.format ?
        // stroom.proxy.store.rollCron ?

        map("stroom.proxyDir", "stroom.proxyAggregation.proxyDir");
        map("stroom.proxyThreads", "stroom.proxyAggregation.proxyThreads");
        map("stroom.query.history.daysRetention", "stroom.queryHistory.daysRetention");
        map("stroom.query.history.itemsRetention", "stroom.queryHistory.itemsRetention");
        map("stroom.query.infoPopup.enabled", "stroom.ui.query.infoPopup.enabled");
        map("stroom.query.infoPopup.title", "stroom.ui.query.infoPopup.title");
        map("stroom.query.infoPopup.validationRegex", "stroom.ui.query.infoPopup.validationRegex");

        // Same name in 6 & master
        // stroom.search.impl.extraction.maxThreads
        // stroom.search.impl.extraction.maxThreadsPerTask
        // stroom.search.maxBooleanClauseCount
        // stroom.search.maxStoredDataQueueSize

        map("stroom.search.process.defaultRecordLimit", "stroom.ui.process.defaultRecordLimit");
        map("stroom.search.process.defaultTimeLimit", "stroom.ui.process.defaultTimeLimit");

        // Same name in 6 & master
        // stroom.search.impl.shard.maxDocIdQueueSize
        // stroom.search.impl.shard.maxThreads
        // stroom.search.impl.shard.maxThreadsPerTask
        // stroom.search.storeSize

        map("stroom.security.userNamePattern", "stroom.security.authentication.userNamePattern");
        map("stroom.serviceDiscovery.curator.baseSleepTimeMs", "stroom.serviceDiscovery.curatorBaseSleepTimeMs");
        map("stroom.serviceDiscovery.curator.maxRetries", "stroom.serviceDiscovery.curatorMaxRetries");
        map("stroom.serviceDiscovery.curator.maxSleepTimeMs", "stroom.serviceDiscovery.curatorMaxSleepTimeMs");

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

        map("stroom.services.stroomStats.internalStats.eventsPerMessage", "stroom.statistics.hbase.eventsPerMessage");
        map("stroom.services.stroomStats.kafkaTopics.count", "stroom.statistics.hbase.kafkaTopics.count");
        map("stroom.services.stroomStats.kafkaTopics.value", "stroom.statistics.hbase.kafkaTopics.value");

        map("stroom.splash.body", "stroom.ui.splash.body");
        map("stroom.splash.enabled", "stroom.ui.splash.enabled");
        map("stroom.splash.title", "stroom.ui.splash.title");
        map("stroom.splash.version", "stroom.ui.splash.version");

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

        map("stroom.statistics.sql.jdbcDriverClassName", "stroom.statistics.sql.db.connection.jdbcDriverClassName");
        map("stroom.statistics.sql.jdbcDriverPassword", "stroom.statistics.sql.db.connection.jdbcDriverPassword");
        map("stroom.statistics.sql.jdbcDriverUrl", "stroom.statistics.sql.db.connection.jdbcDriverUrl");
        map("stroom.statistics.sql.jdbcDriverUsername", "stroom.statistics.sql.db.connection.jdbcDriverUsername");

        // Same names in 6 & master
        map("stroom.statistics.sql.maxProcessingAge", MSU_2_STROOM_DURATION);
        // stroom.statistics.impl.sql.search.fetchSize
        // stroom.statistics.impl.sql.search.maxResults
        // stroom.statistics.impl.sql.search.resultHandlerBatchSize
        // stroom.statistics.sql.statisticAggregationBatchSize

        // TODO lots of options for these; stroom.data.store, stroom.policy, stroom.process
        // ? stroom.stream.deleteBatchSize ? stroom.data.store.deleteBatchSize
        // ? stroom.stream.deletePurgeAge ? stroom.data.store.deletePurgeAge //TODO map to StroomDuration
        // ? stroom.streamAttribute.deleteAge ? stroom.process.deleteAge //TODO map to StroomDuration
        // ? stroom.streamAttribute.deleteBatchSize ?

        map("stroom.streamTask.assignTasks", "stroom.processor.assignTasks");
        map("stroom.streamTask.createTasks", "stroom.processor.createTasks");

        // TODO lots of options for these; stroom.data.store, stroom.policy, stroom.process
        // ? stroom.streamTask.deleteAge ? stroom.process.deleteAge //TODO map to StroomDuration
        // ? stroom.streamTask.deleteBatchSize ?

        map("stroom.streamTask.fillTaskQueue", "stroom.processor.fillTaskQueue");
        map("stroom.streamTask.queueSize", "stroom.processor.queueSize");
        map("stroom.streamstore.preferLocalVolumes", "stroom.volumes.preferLocalVolumes");
        map("stroom.streamstore.resilientReplicationCount", "stroom.volumes.resilientReplicationCount");
        map("stroom.streamstore.volumeSelector", "stroom.volumes.volumeSelector");
        map("stroom.theme.background-attachment", "stroom.ui.theme.backgroundAttachment");
        map("stroom.theme.background-color", "stroom.ui.theme.backgroundColor");
        map("stroom.theme.background-image", "stroom.ui.theme.backgroundImage");
        map("stroom.theme.background-opacity", "stroom.ui.theme.backgroundOpacity");
        map("stroom.theme.background-position", "stroom.ui.theme.backgroundPosition");
        map("stroom.theme.background-repeat", "stroom.ui.theme.backgroundRepeat");
        map("stroom.theme.labelColours", "stroom.ui.theme.labelColours");
        map("stroom.theme.tube.opacity", "stroom.ui.theme.tubeOpacity");
        map("stroom.theme.tube.visible", "stroom.ui.theme.tubeVisible");
        map("stroom.unknownClassification", "stroom.ui.theme.tubeVisible");

        // Same name in 6 & master
        // stroom.volumes.createDefaultOnStart

        map("stroom.welcomeHTML", "stroom.ui.welcomeHtml");
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
     * ModelString e.g. 30d to an ISO-8601 duration string, e.g. P30D
     */
    static String modelStringDurationToDuration(final String oldValue) {
        if (oldValue == null) {
           return null;
        } else if (oldValue.isBlank()) {
            return "";
        } else if (oldValue.matches("^[0-9]+[dD]$")) {
            // special case for days to stop Duration turning them into hours
            // e.g. 30d becomes PT720H rather than P30D
            String daysPart = oldValue.replaceAll("[dD]$","");
            return Duration.parse("P" + daysPart + "D").toString();
        } else {
            final Long durationMs = ModelStringUtil.parseDurationString(oldValue);
            return Duration.ofMillis(durationMs).toString();
        }
    }

    /**
     * ModelString e.g. 30d to an ISO-8601 duration string, e.g. P30D
     */
    static String modelStringDurationToStroomDuration(final String oldValue) {
        if (oldValue == null) {
            return null;
        } else if (oldValue.isBlank()) {
            return "";
        } else {
            return StroomDuration.parse(oldValue).toString();
        }
    }
}
