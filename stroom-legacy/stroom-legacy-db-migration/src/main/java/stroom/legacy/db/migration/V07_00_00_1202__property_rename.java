package stroom.legacy.db.migration;

import stroom.config.impl.db.jooq.tables.Config;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.JooqUtil;
import stroom.legacy.model_6_1.GlobalProperty;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@SuppressWarnings("unused") // used by FlyWay
@Deprecated
public class V07_00_00_1202__property_rename extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_00_00_1202__property_rename.class);

    // The mapping funcs are held as variables to save using the fully qualified name
    private static final Function<String, String> MSU_2_DURATION =
            V07_00_00_1202__property_rename::modelStringDurationToDuration;
    private static final Function<String, String> MSU_2_STROOM_DURATION =
            V07_00_00_1202__property_rename::modelStringDurationToStroomDuration;
    private static final Function<String, String> COMMA_DELIM_STR_2_LIST_OF_STR =
            V07_00_00_1202__property_rename::commaDelimitedStringToListOfString;
    private static final Function<String, String> COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS =
            V07_00_00_1202__property_rename::delimitedDocRefsToListOfDocRefs;

    @Override
    public void migrate(final Context flywayContext) {
        try {
            final DSLContext context = JooqUtil.createContext(flywayContext.getConnection());

            // This line should only be un-commented for manual testing in development
//            loadTestDataForManualTesting(context);

            // Rename some property names
            final Mappings mappings = new Mappings();
            final Set<String> migrated = new HashSet<>();

            LOGGER.info(() -> "Migrating 5.5 properties");
            migrateProperties(
                    context,
                    mappings,
                    stroom.legacy.model_5_5.DefaultProperties.getList(),
                    migrated);

            LOGGER.info(() -> "Migrating 6.1 properties");
            migrateProperties(
                    context,
                    mappings,
                    stroom.legacy.model_6_1.DefaultProperties.getList(),
                    migrated);

//            LOGGER.info(() -> "Migrating Other properties");
//            mappings.list()
//                    .stream()
//                    .map(Mapping::getOldName)
//                    .sorted(Comparator.naturalOrder())
//                    .forEach(oldName -> {
//                        if (!migrated.contains(oldName)) {
//                            migrated.add(oldName);
//                            migrateProperty(context, mappings, oldName);
//                        }
//                    });

        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error renaming property", e);
            throw e;
        }
    }

    private void migrateProperties(final DSLContext context,
                                   final Mappings mappings,
                                   final List<GlobalProperty> properties,
                                   final Set<String> migrated) {
        properties
                .stream()
                .map(GlobalProperty::getName)
                .sorted(Comparator.naturalOrder())
                .forEach(oldName -> {
                    if (!migrated.contains(oldName)) {
                        migrated.add(oldName);
                        migrateProperty(context, mappings, oldName);
                    }
                });
    }

    private void migrateProperty(final DSLContext context, final Mappings mappings, final String oldName) {
        final Optional<ConfigRecord> optionalOldRec = context
                .selectFrom(Config.CONFIG)
                .where(Config.CONFIG.NAME.eq(oldName))
                .fetchOptional();
        optionalOldRec.ifPresent(oldRec -> {

            final Mapping mapping = mappings.get(oldName);
            if (mapping != null) {
                final String newName = mapping.getNewName();
                final String oldValue = oldRec.getVal();
                final String newValue = mapping.getSerialisationMappingFunc().apply(oldValue);

                if (mapping.getOldName().equals(mapping.getNewName())) {
                    if (!Objects.equals(oldValue, newValue)) {
                        LOGGER.info(() -> "Changing value of DB property '" +
                                oldName +
                                "' from '" +
                                oldValue +
                                "' to '" +
                                newValue +
                                "'");
                        // Just update the old record.
                        oldRec.setVal(newValue);
                        oldRec.store();
                    }
                } else {
                    final Optional<ConfigRecord> optionalNewRec = context
                            .selectFrom(Config.CONFIG)
                            .where(Config.CONFIG.NAME.eq(mapping.getNewName()))
                            .fetchOptional();
                    if (optionalNewRec.isPresent()) {
                        final ConfigRecord newRec = optionalNewRec.get();
                        if (!Objects.equals(newRec.getVal(), newValue)) {
                            LOGGER.info(() -> "Updating value of new DB property '" +
                                    newName +
                                    "' from old property '" +
                                    oldName +
                                    "' from '" +
                                    oldValue +
                                    "' to '" +
                                    newValue +
                                    "'");
                            // Update the new record.
                            newRec.setVal(newValue);
                            newRec.store();
                        }

                        // Delete the old record.
                        oldRec.delete();

                    } else {
                        // Rename the old record.
                        LOGGER.info(() -> "Renaming DB property '" +
                                mapping.getOldName() +
                                "' to '" +
                                mapping.getNewName() +
                                "'");
                        oldRec.setName(mapping.getNewName());
                        // Update the value.
                        LOGGER.info(() -> "Changing value of DB property '" +
                                mapping.getNewName() +
                                "' from " +
                                "'" +
                                oldValue +
                                "' to '" +
                                newValue +
                                "'");
                        oldRec.setVal(newValue);

                        oldRec.store();
                    }
                }

            } else {
                LOGGER.info(() -> "Removing old property that has no mapping: " +
                        oldName +
                        "=" +
                        oldRec.getVal());
                oldRec.delete();
            }
        });
    }

    /**
     * If this is called before the mig happens you can seed the db with test data to manually test migrations
     */
    private void loadTestDataForManualTesting(final DSLContext context) {

        LOGGER.warn(() -> "Loading test data - Not for use in prod");
        final Map<String, String> testDataMap = Map.of(

                "stroom.aboutHTML",
                "myHtml",

                "stroom.advertisedUrl",
                "myUrl",

                "stroom.unknownProp",
                "some value",

                "stroom.internalstatistics.cpu.docRefs",
                "docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes),docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)",

                "stroom.internalstatistics.heapHistogramBytes.docRefs",
                "docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)",

                "stroom.annotation.standardComments",
                "This is comment one,This is comment two, This is comment three",

                "stroom.annotation.statusValues",
                "New,Assigned,Closed,Bingo,Bongo",

                "stroom.statistics.sql.maxProcessingAge",
                "100d"
        );

        testDataMap.forEach((key, value) ->
                context
                        .insertInto(Config.CONFIG)
                        .columns(Config.CONFIG.VERSION,
                                Config.CONFIG.CREATE_TIME_MS,
                                Config.CONFIG.CREATE_USER,
                                Config.CONFIG.UPDATE_TIME_MS,
                                Config.CONFIG.UPDATE_USER,
                                Config.CONFIG.NAME,
                                Config.CONFIG.VAL)
                        .values(1,
                                System.currentTimeMillis(),
                                "test",
                                System.currentTimeMillis(),
                                "test",
                                key,
                                value)
                        .execute());
    }

    public static class Mappings {

        private final Map<String, Mapping> mappings = new HashMap<>();
        private final Set<String> ignoredMappings = new HashSet<>();

        public Mappings() {
            ignoredMappings.add("stroom.advertisedUrl");
            ignoredMappings.add("stroom.auth.authentication.service.url");
            ignoredMappings.add("stroom.auth.jwt.enabletokenrevocationcheck");
            ignoredMappings.add("stroom.auth.clientId");
            ignoredMappings.add("stroom.auth.jwt.issuer");
            ignoredMappings.add("stroom.auth.clientSecret");
            ignoredMappings.add("stroom.auth.services.url");
            ignoredMappings.add("stroom.auth.services.verifyingSsl");

            ignoredMappings.add("stroom.kafka.bootstrap.servers");

            ignoredMappings.add("stroom.proxy.store.dir");
            ignoredMappings.add("stroom.proxy.store.format");
            ignoredMappings.add("stroom.proxy.store.rollCron");

            ignoredMappings.add("stroom.proxyBufferSize");
            ignoredMappings.add("stroom.streamstore.resilientReplicationCount");
            ignoredMappings.add("stroom.streamstore.preferLocalVolumes");
            ignoredMappings.add("stroom.streamTask.deleteBatchSize");
            ignoredMappings.add("stroom.benchmark.streamCount");
            ignoredMappings.add("stroom.benchmark.recordCount");
            ignoredMappings.add("stroom.benchmark.concurrentWriters");
            ignoredMappings.add("stroom.rack");
            ignoredMappings.add("stroom.pipeline.parser.maxPoolSize");
            ignoredMappings.add("stroom.pipeline.schema.maxPoolSize");
            ignoredMappings.add("stroom.pipeline.xslt.maxPoolSize");
            ignoredMappings.add("stroom.jpaHbm2DdlAuto");
            ignoredMappings.add("stroom.jpaDialect");
            ignoredMappings.add("stroom.showSql");

            ignoredMappings.add("stroom.db.connectionPool.initialPoolSize");
            ignoredMappings.add("stroom.db.connectionPool.minPoolSize");
            ignoredMappings.add("stroom.db.connectionPool.maxPoolSize");
            ignoredMappings.add("stroom.db.connectionPool.idleConnectionTestPeriod");
            ignoredMappings.add("stroom.db.connectionPool.maxIdleTime");
            ignoredMappings.add("stroom.db.connectionPool.acquireIncrement");
            ignoredMappings.add("stroom.db.connectionPool.acquireRetryAttempts");
            ignoredMappings.add("stroom.db.connectionPool.acquireRetryDelay");
            ignoredMappings.add("stroom.db.connectionPool.checkoutTimeout");
            ignoredMappings.add("stroom.db.connectionPool.maxIdleTimeExcessConnections");
            ignoredMappings.add("stroom.db.connectionPool.maxConnectionAge");
            ignoredMappings.add("stroom.db.connectionPool.unreturnedConnectionTimeout");
            ignoredMappings.add("stroom.db.connectionPool.numHelperThreads");

            ignoredMappings.add("stroom.security.userNamePattern");

            ignoredMappings.add("stroom.serviceDiscovery.simpleLookup.basePath");

            ignoredMappings.add("stroom.search.extraction.maxThreads");
            ignoredMappings.add("stroom.search.shard.maxThreads");

            ignoredMappings.add("stroom.services.authentication.docRefType");
            ignoredMappings.add("stroom.services.authentication.name");
            ignoredMappings.add("stroom.services.authentication.version");
            ignoredMappings.add("stroom.services.authorisation.docRefType");
            ignoredMappings.add("stroom.services.authorisation.name");
            ignoredMappings.add("stroom.services.authorisation.version");
            ignoredMappings.add("stroom.services.sqlStatistics.docRefType");
            ignoredMappings.add("stroom.services.sqlStatistics.name");
            ignoredMappings.add("stroom.services.sqlStatistics.version");
            ignoredMappings.add("stroom.services.stroomIndex.docRefType");
            ignoredMappings.add("stroom.services.stroomIndex.name");
            ignoredMappings.add("stroom.services.stroomIndex.version");
            ignoredMappings.add("stroom.services.stroomStats.docRefType");
            ignoredMappings.add("stroom.services.stroomStats.name");
            ignoredMappings.add("stroom.services.stroomStats.version");

            ignoredMappings.add("stroom.statistics.legacy.statisticAggregationBatchSize");

            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.initialPoolSize");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.minPoolSize");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.maxPoolSize");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.idleConnectionTestPeriod");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.maxIdleTime");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.acquireIncrement");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.acquireRetryAttempts");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.acquireRetryDelay");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.checkoutTimeout");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.maxIdleTimeExcessConnections");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.maxConnectionAge");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.unreturnedConnectionTimeout");
            ignoredMappings.add("stroom.statistics.sql.db.connectionPool.numHelperThreads");

            ignoredMappings.add("stroom.entity.maxCacheSize");
            ignoredMappings.add("stroom.referenceData.mapStore.maxCacheSize");
            ignoredMappings.add("stroom.loginHTML");

            ignoredMappings.add("stroom.security.documentPermissions.maxCacheSize");

            ignoredMappings.add("stroom.statistics.common.statisticEngines");
            ignoredMappings.add("stroom.statistics.sql.search.resultHandlerBatchSize");

            ignoredMappings.add("stroom.uiUrl");
            ignoredMappings.add("stroom.volumes.createDefaultOnStart");

            // v7.1 No longer uses these properties.
            ignoredMappings.add("stroom.maxConcurrentMappedFiles");
            ignoredMappings.add("stroom.maxFileScan");
            ignoredMappings.add("stroom.proxyDir");
            ignoredMappings.add("stroom.proxyThreads");
            ignoredMappings.add("stroom.bufferSize");
            ignoredMappings.add("stroom.maxAggregation");
            ignoredMappings.add("stroom.maxAggregationScan");
            ignoredMappings.add("stroom.maxStreamSize");

            // TODO what do we do about mapping c3po pool props to hikari?
            //   Can we map some/any of them to equiv hikari props?
            map("stroom.temp", "stroom.path.temp");
            map("stroom.feed.receiptPolicyUuid", "stroom.receive.receiptPolicyUuid");
            map("stroom.maxFileScan", "stroom.proxyAggregation.maxFileScan");
            map("stroom.maxConcurrentMappedFiles", "stroom.proxyAggregation.maxConcurrentMappedFiles");
            map("stroom.streamAttribute.deleteAge", "stroom.data.meta.metaValue.deleteAge", MSU_2_STROOM_DURATION);
            map("stroom.streamAttribute.deleteBatchSize", "stroom.data.meta.metaValue.deleteBatchSize");
            map("stroom.streamTask.deleteAge", "stroom.processor.deleteAge", MSU_2_STROOM_DURATION);
            map("stroom.stream.deletePurgeAge", "stroom.data.store.deletePurgeAge", MSU_2_STROOM_DURATION);
            map("stroom.stream.deleteBatchSize", "stroom.data.store.deleteBatchSize");
            map("stroom.lifecycle.enabled", "stroom.lifecycle.enabled");
            map("stroom.lifecycle.executionInterval", "stroom.job.executionInterval");
            map("stroom.pipeline.parser.secureProcessing", "stroom.pipeline.parser.secureProcessing");


            map("stroom.aboutHTML", "stroom.ui.aboutHtml");
            map("stroom.activity.chooseOnStartup", "stroom.ui.activity.chooseOnStartup");
            map("stroom.activity.editorBody", "stroom.ui.activity.editorBody");
            map("stroom.activity.editorTitle", "stroom.ui.activity.editorTitle");
            map("stroom.activity.enabled", "stroom.ui.activity.enabled");
            map("stroom.activity.managerTitle", "stroom.ui.activity.managerTitle");
//        map("stroom.advertisedUrl", "stroom.ui.url.ui");

            map("stroom.annotation.statusValues", COMMA_DELIM_STR_2_LIST_OF_STR);
            map("stroom.annotation.standardComments", COMMA_DELIM_STR_2_LIST_OF_STR);
            map("stroom.annotation.createText",
                    "stroom.annotation.createText");  // name and serialised form are the same

//        map("stroom.auth.authentication.service.url",
//                "stroom.security.authentication.openId.authenticationServiceUrl");
//        map("stroom.auth.jwt.enabletokenrevocationcheck",
//                "stroom.security.authentication.jwt.enableTokenRevocationCheck");
//        map("stroom.auth.jwt.issuer", "stroom.security.authentication.jwt.jwtIssuer");
//        map("stroom.auth.clientId", "stroom.security.authentication.openId.clientId");
//        map("stroom.auth.jwt.issuer", "stroom.security.authentication.openId.issuer");
//        map("stroom.auth.clientSecret", "stroom.security.authentication.openId.clientSecret");
//        map("stroom.auth.services.url", "stroom.security.authentication.authServicesBaseUrl");
            map("stroom.authentication.required", "stroom.security.authentication.authenticationRequired");
            // same names in 6 & master
            // stroom.benchmark.concurrentWriters
            // stroom.benchmark.recordCount
            // stroom.benchmark.streamCount
            map("stroom.bufferSize", "stroom.receive.bufferSize");
            map("stroom.clusterCallIgnoreSSLHostnameVerifier",
                    "stroom.cluster.clusterCallIgnoreSSLHostnameVerifier");
            map("stroom.clusterCallReadTimeout",
                    "stroom.cluster.clusterCallReadTimeout",
                    MSU_2_STROOM_DURATION);
            map("stroom.clusterCallUseLocal", "stroom.cluster.clusterCallUseLocal");
            map("stroom.clusterResponseTimeout",
                    "stroom.cluster.clusterResponseTimeout",
                    MSU_2_STROOM_DURATION);
            map("stroom.contentPackImportEnabled", "stroom.contentPackImport.enabled");
            map("stroom.dashboard.defaultMaxResults", "stroom.ui.defaultMaxResults");
            map("stroom.databaseMultiInsertMaxBatchSize",
                    "stroom.processor.databaseMultiInsertMaxBatchSize");
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
            map("stroom.export.enabled", "stroom.export.enabled");
            // stroom.feed.receiptPolicyUuid
            map("stroom.feedNamePattern", "stroom.feed.feedNamePattern");
            map("stroom.fileSystemCleanBatchSize", "stroom.data.store.fileSystemCleanBatchSize");
            map("stroom.fileSystemCleanDeleteOut", "stroom.data.store.fileSystemCleanDeleteOut");
            map("stroom.fileSystemCleanOldAge",
                    "stroom.data.store.fileSystemCleanOldAge",
                    MSU_2_STROOM_DURATION);
            map("stroom.helpUrl", "stroom.ui.helpUrl");

            // same names in 6 & master
            map("stroom.index.ramBufferSizeMB", "stroom.index.ramBufferSizeMB");
            map("stroom.index.writer.cache.coreItems", "stroom.index.writer.cache.coreItems");
            map("stroom.index.writer.cache.maxItems", "stroom.index.writer.cache.maxItems");
            map("stroom.index.writer.cache.minItems", "stroom.index.writer.cache.minItems");
            map("stroom.index.writer.cache.timeToIdle", "stroom.index.writer.cache.timeToIdle", MSU_2_STROOM_DURATION);
            map("stroom.index.writer.cache.timeToLive", "stroom.index.writer.cache.timeToLive", MSU_2_STROOM_DURATION);

            map(
                    "stroom.internalstatistics.benchmarkCluster.docRefs",
                    "stroom.statistics.internal.benchmarkCluster",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.cpu.docRefs",
                    "stroom.statistics.internal.cpu",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.eventsPerSecond.docRefs",
                    "stroom.statistics.internal.eventsPerSecond",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.heapHistogramBytes.docRefs",
                    "stroom.statistics.internal.heapHistogramBytes",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.heapHistogramInstances.docRefs",
                    "stroom.statistics.internal.heapHistogramInstances",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.memory.docRefs",
                    "stroom.statistics.internal.memory",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.metaDataStreamSize.docRefs",
                    "stroom.statistics.internal.metaDataStreamSize",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.metaDataStreamsReceived.docRefs",
                    "stroom.statistics.internal.metaDataStreamsReceived",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.pipelineStreamProcessor.docRefs",
                    "stroom.statistics.internal.pipelineStreamProcessor",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.streamTaskQueueSize.docRefs",
                    "stroom.statistics.internal.streamTaskQueueSize",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);
            map(
                    "stroom.internalstatistics.volumes.docRefs",
                    "stroom.statistics.internal.volumes",
                    COMMA_DELIM_DOCREFS_2_LIST_OF_DOCREFS);

            map("stroom.jdbcDriverClassName", "stroom.core.db.connection.jdbcDriverClassName");
            map("stroom.jdbcDriverPassword", "stroom.core.db.connection.jdbcDriverPassword");
            map("stroom.jdbcDriverUrl", "stroom.core.db.connection.jdbcDriverUrl");
            map("stroom.jdbcDriverUsername", "stroom.core.db.connection.jdbcDriverUsername");

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
            map("stroom.node.status.heapHistogram.classNameMatchRegex",
                    "stroom.node.status.heapHistogram.classNameMatchRegex");
            map("stroom.node.status.heapHistogram.classNameReplacementRegex",
                    "stroom.node.status.heapHistogram.classNameReplacementRegex");
            // stroom.node.status.heapHistogram.jMapExecutable

            map("stroom.pageTitle", "stroom.ui.htmlTitle");
            map("stroom.htmlTitle", "stroom.ui.htmlTitle");

            // Same names in 6 & master
            map("stroom.pipeline.appender.maxActiveDestinations", "stroom.pipeline.appender.maxActiveDestinations");
            map("stroom.pipeline.xslt.maxElements", "stroom.pipeline.xslt.maxElements");

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


            map("stroom.search.storeSize", "stroom.search.storeSize");
            map("stroom.search.shard.maxDocIdQueueSize", "stroom.search.shard.maxDocIdQueueSize");
            map("stroom.search.shard.maxThreads", "stroom.search.shard.maxThreads");
            map("stroom.search.shard.maxThreadsPerTask", "stroom.search.shard.maxThreadsPerTask");
            map("stroom.search.extraction.maxThreads", "stroom.search.extraction.maxThreads");
            map("stroom.search.extraction.maxThreadsPerTask", "stroom.search.extraction.maxThreadsPerTask");
            map("stroom.search.extraction.maxStreamEventMapSize", "stroom.search.extraction.maxStreamEventMapSize");
            map("stroom.search.maxBooleanClauseCount", "stroom.search.maxBooleanClauseCount");
            map("stroom.search.maxStoredDataQueueSize", "stroom.search.maxStoredDataQueueSize");


            map("stroom.search.process.defaultRecordLimit", "stroom.ui.process.defaultRecordLimit");
            map("stroom.search.process.defaultTimeLimit", "stroom.ui.process.defaultTimeLimit");

            // Same name in 6 & master
            // stroom.search.impl.shard.maxDocIdQueueSize
            // stroom.search.impl.shard.maxThreads
            // stroom.search.impl.shard.maxThreadsPerTask
            // stroom.search.storeSize

            map("stroom.security.web.content.frameOptions",
                    "stroom.security.webContent.frameOptions");
            map("stroom.security.web.content.securityPolicy",
                    "stroom.security.webContent.contentSecurityPolicy");
            map("stroom.security.web.content.typeOptions",
                    "stroom.security.webContent.contentTypeOptions");
            map("stroom.security.web.content.xssProtection",
                    "stroom.security.webContent.xssProtection");

//        map("stroom.security.userNamePattern",
//                "stroom.security.authentication.userNamePattern");

            map("stroom.serviceDiscovery.curator.baseSleepTimeMs",
                    "stroom.serviceDiscovery.curatorBaseSleepTimeMs");
            map("stroom.serviceDiscovery.curator.maxRetries",
                    "stroom.serviceDiscovery.curatorMaxRetries");
            map("stroom.serviceDiscovery.curator.maxSleepTimeMs",
                    "stroom.serviceDiscovery.curatorMaxSleepTimeMs");

            // TODO need to figure out what we are doing with service disco
            map("stroom.serviceDiscovery.enabled", "stroom.serviceDiscovery.enabled");
            map("stroom.serviceDiscovery.servicesHostNameOrIpAddress",
                    "stroom.serviceDiscovery.servicesHostNameOrIpAddress");
            map("stroom.serviceDiscovery.servicesPort", "stroom.serviceDiscovery.servicesPort");
//        map("stroom.serviceDiscovery.simpleLookup.basePath", "stroom.serviceDiscovery.simpleLookup.basePath");
            map("stroom.serviceDiscovery.zookeeperBasePath", "stroom.serviceDiscovery.zookeeperBasePath");
            map("stroom.serviceDiscovery.zookeeperUrl", "stroom.serviceDiscovery.zookeeperUrl");
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

            map("stroom.services.stroomStats.internalStats.eventsPerMessage",
                    "stroom.statistics.hbase.eventsPerMessage");
            map("stroom.services.stroomStats.kafkaTopics.count",
                    "stroom.statistics.hbase.kafkaTopics.count");
            map("stroom.services.stroomStats.kafkaTopics.value",
                    "stroom.statistics.hbase.kafkaTopics.value");

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

            map("stroom.statistics.sql.jdbcDriverClassName",
                    "stroom.statistics.sql.db.connection.jdbcDriverClassName");
            map("stroom.statistics.sql.jdbcDriverPassword",
                    "stroom.statistics.sql.db.connection.jdbcDriverPassword");
            map("stroom.statistics.sql.jdbcDriverUrl",
                    "stroom.statistics.sql.db.connection.jdbcDriverUrl");
            map("stroom.statistics.sql.jdbcDriverUsername",
                    "stroom.statistics.sql.db.connection.jdbcDriverUsername");

            // Same names in 6 & master
            map("stroom.statistics.sql.maxProcessingAge", MSU_2_STROOM_DURATION);
            map("stroom.statistics.sql.search.fetchSize", "stroom.statistics.sql.search.fetchSize");
            map("stroom.statistics.sql.search.maxResults", "stroom.statistics.sql.search.maxResults");
            // stroom.statistics.impl.sql.search.resultHandlerBatchSize
            map("stroom.statistics.sql.statisticAggregationBatchSize",
                    "stroom.statistics.sql.statisticAggregationBatchSize");

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
            map("stroom.streamstore.resilientReplicationCount",
                    "stroom.volumes.resilientReplicationCount");
            map("stroom.streamstore.volumeSelector", "stroom.volumes.volumeSelector");
            map("stroom.theme.background-attachment", "stroom.ui.theme.backgroundAttachment");
            map("stroom.theme.background-color", "stroom.ui.theme.backgroundColour");
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

            ignoredMappings.add("stroom.developmentMode");
            map("stroom.security.allowCertificateAuthentication",
                    "stroom.security.identity.allowCertificateAuthentication");
            map("stroom.security.certificateDNPattern", "stroom.security.identity.certificateCnPattern");
            ignoredMappings.add("stroom.daysToAccountExpiry");
            ignoredMappings.add("stroom.daysToUnusedAccountExpiry");
            ignoredMappings.add("stroom.daysToPasswordExpiry");
            ignoredMappings.add("stroom.search.shard.maxOpen");
            ignoredMappings.add("stroom.search.maxResults");
            map("stroom.mail.host", "stroom.security.identity.email.smtp.host");
            map("stroom.mail.port", "stroom.security.identity.email.smtp.port");
            ignoredMappings.add("stroom.mail.protocol");
            map("stroom.mail.userName", "stroom.security.identity.email.smtp.username");
            map("stroom.mail.password", "stroom.security.identity.email.smtp.password");
            ignoredMappings.add("stroom.mail.propertiesFile");
            ignoredMappings.add("stroom.mail.userDomain");
            ignoredMappings.add("stroom.node.status.heapHistogram.jMapExecutable");

            // Test we have mapped all v5 properties.
            final StringBuilder sb = new StringBuilder();
            checkMappings(stroom.legacy.model_5_5.DefaultProperties.getList(), "5.5", sb);
            checkMappings(stroom.legacy.model_6_1.DefaultProperties.getList(), "6.1", sb);
            if (sb.length() > 0) {
                LOGGER.error(() -> sb.toString());
                throw new RuntimeException(sb.toString());
            }
        }

        public Mapping get(final String oldName) {
            return mappings.get(oldName);
        }

        public boolean ignore(final String oldName) {
            return ignoredMappings.contains(oldName);
        }

        /**
         * Rename prop and change its serialised form
         */
        private void map(final String oldName,
                         final String newName,
                         final Function<String, String> serialisationMappingFunc) {
            Objects.requireNonNull(oldName);
            Objects.requireNonNull(newName);
            Objects.requireNonNull(serialisationMappingFunc);
            mappings.put(oldName, new Mapping(oldName, newName, serialisationMappingFunc));
        }

        /**
         * Change to serialised form, no name change
         */
        private void map(final String name,
                         final Function<String, String> serialisationMappingFunc) {
            map(name, name, serialisationMappingFunc);
        }

        /**
         * Simple rename of the prop
         */
        private void map(final String oldName,
                         final String newName) {
            Objects.requireNonNull(oldName);
            Objects.requireNonNull(newName);
            mappings.put(oldName, new Mapping(oldName, newName));
        }

        private void checkMappings(final List<GlobalProperty> list,
                                   final String prefix,
                                   final StringBuilder sb) {
            // Test we have mapped all v5 properties.
            list.forEach(globalProperty -> {
                final String name = globalProperty.getName();
                if (!ignoredMappings.contains(name)) {
                    final Mapping mapping = mappings.get(name);
                    if (mapping == null) {
                        sb.append("No ")
                                .append(prefix)
                                .append(" mapping exists for: ")
                                .append(name)
                                .append("\n");
                    }
                }
            });
        }

        public Collection<Mapping> list() {
            return mappings.values();
        }
    }

    public static class Mapping {

        private final String oldName;
        private final String newName;
        private final Function<String, String> serialisationMappingFunc;

        private Mapping(final String oldName,
                        final String newName,
                        final Function<String, String> serialisationMappingFunc) {
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
        if (oldValue == null || oldValue.isBlank()) {
            return null;
//        } else if (oldValue.isBlank()) {
//            return "";
        } else if (oldValue.matches("^[0-9]+[dD]$")) {
            // special case for days to stop Duration turning them into hours
            // e.g. 30d becomes PT720H rather than P30D
            String daysPart = oldValue.replaceAll("[dD]$", "");
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
        if (oldValue == null || oldValue.isBlank()) {
            return null;
//        } else if (oldValue.isBlank()) {
//            return "";
        } else {
            StroomDuration stroomDuration = StroomDuration.parse(oldValue);
            // We want the ISO format so create a new one with the underlying duration so we
            // lose the old string format
            return StroomDuration.of(stroomDuration.getDuration()).toString();
        }
    }

    static String commaDelimitedStringToListOfString(final String oldValue) {
        return delimitedStringToListOfString(",", oldValue);
    }

    static String delimitedStringToListOfString(final String delimiter, final String oldValue) {
        if (oldValue == null || oldValue.isBlank()) {
            return null;
//        } else if (oldValue.isBlank()) {
//            return "";
        } else {
            // Our list serialisation is like '|A|B|C', i.e. prefixed with the delimiter in use
            return delimiter + oldValue;
        }
    }

    /**
     * [docRef(type1,uuid1,name1),docRef(type2,uuid2,name2)] => [|,docRef(type1,uuid1,name1)|,docRef(type2,uuid2,name2)]
     */
    static String delimitedDocRefsToListOfDocRefs(final String oldValue) {
        if (oldValue == null || oldValue.isBlank()) {
            return null;
//        } else if (oldValue.isBlank()) {
//            return "";
        } else {
            // Our List<DocRef> serialisation is like '|,docRef(type1,uuid1,name1)|docRef(type2,uuid2,name2)',
            // i.e. prefixed with the outer delimiter then each docref is prefixed with the inner delimiter
            String newValue = oldValue.replace("),docRef", ")|,docRef");
            newValue = "|," + newValue;
            return newValue;
        }
    }
}
