-- v6 prop mappings
-- UPDATE config set name = '' where name = '';

-- "cdt x"bD"ap0f'"bpf';"cpj0


insert into config (name, val) values ('stroom.internalstatistics.cpu.docRefs', 'docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes),docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)');
insert into config (name, val) values ('stroom.internalstatistics.memory.docRefs', 'docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)');

UPDATE config set 
  name = 'stroom.ui.aboutHTML' 
  where name = 'stroom.aboutHtml';

UPDATE config set 
  name = 'stroom.ui.activity.chooseOnStartup' 
  where name = 'stroom.activity.chooseOnStartup';

UPDATE config set 
  name = 'stroom.ui.activity.editorBody' 
  where name = 'stroom.activity.editorBody';

UPDATE config set 
  name = 'stroom.ui.activity.editorTitle' 
  where name = 'stroom.activity.editorTitle';

UPDATE config set 
  name = 'stroom.ui.activity.enabled' 
  where name = 'stroom.activity.enabled';

UPDATE config set 
  name = 'stroom.ui.activity.managerTitle' 
  where name = 'stroom.activity.managerTitle';

UPDATE config set 
  name = 'stroom.ui.url.ui' 
  where name = 'stroom.advertisedUrl';

UPDATE config set 
  name = 'stroom.security.authentication.authenticationServiceUrl' 
  where name = 'stroom.auth.authentication.service.url';

UPDATE config set 
  name = 'stroom.security.authentication.jwt.enableTokenRevocationCheck' 
  where name = 'stroom.auth.jwt.enabletokenrevocationcheck';

UPDATE config set 
  name = 'stroom.security.authentication.jwt.jwtIssuer' 
  where name = 'stroom.auth.jwt.issuer';

UPDATE config set 
  name = 'stroom.security.authentication.authServicesBaseUrl' 
  where name = 'stroom.auth.services.url';

UPDATE config set 
  name = 'stroom.security.authentication.authenticationRequired' 
  where name = 'stroom.authentication.required';

-- ? stroom.benchmark.concurrentWriters
-- ? stroom.benchmark.recordCount
-- ? stroom.benchmark.streamCount
UPDATE config set 
  name = 'stroom.feed.bufferSize' 
  where name = 'stroom.bufferSize';

UPDATE config set 
  name = 'stroom.cluster.clusterCallIgnoreSSLHostnameVerifier' 
  where name = 'stroom.clusterCallIgnoreSSLHostnameVerifier';

UPDATE config set 
  name = 'stroom.cluster.clusterCallReadTimeout' 
  where name = 'stroom.clusterCallReadTimeout';

UPDATE config set 
  name = 'stroom.cluster.clusterCallUseLocal' 
  where name = 'stroom.clusterCallUseLocal';

UPDATE config set 
  name = 'stroom.clusterResponseTimeout' 
  where name = 'stroom.clusterResponseTimeout';

UPDATE config set 
  name = 'stroom.contentPackImport.enabled' 
  where name = 'stroom.contentPackImportEnabled';

UPDATE config set 
  name = 'stroom.ui.defaultMaxResults' 
  where name = 'stroom.dashboard.defaultMaxResults';

UPDATE config set 
  name = 'stroom.core.databaseMultiInsertMaxBatchSize' 
  where name = 'stroom.databaseMultiInsertMaxBatchSize';

-- stroom.db.connectionPool.acquireIncrement
-- stroom.db.connectionPool.acquireRetryAttempts
-- stroom.db.connectionPool.acquireRetryDelay
-- stroom.db.connectionPool.checkoutTimeout
-- stroom.db.connectionPool.idleConnectionTestPeriod
-- stroom.db.connectionPool.initialPoolSize
-- stroom.db.connectionPool.maxConnectionAge
-- stroom.db.connectionPool.maxIdleTime
-- stroom.db.connectionPool.maxIdleTimeExcessConnections
-- stroom.db.connectionPool.maxPoolSize
-- stroom.db.connectionPool.minPoolSize
-- stroom.db.connectionPool.numHelperThreads
-- stroom.db.connectionPool.unreturnedConnectionTimeout
-- stroom.export.enabled
-- stroom.feed.receiptPolicyUuid
UPDATE config set 
  name = 'stroom.feed.feedNamePattern' 
  where name = 'stroom.feedNamePattern';

UPDATE config set 
  name = 'stroom.data.store.fileSystemCleanBatchSize' 
  where name = 'stroom.fileSystemCleanBatchSize';

UPDATE config set 
  name = 'stroom.data.store.fileSystemCleanDeleteOut' 
  where name = 'stroom.fileSystemCleanDeleteOut';

UPDATE config set 
  name = 'stroom.data.store.fileSystemCleanOldAge' 
  where name = 'stroom.fileSystemCleanOldAge';

UPDATE config set 
  name = 'stroom.ui.helpUrl' 
  where name = 'stroom.helpUrl';

-- ? stroom.index.ramBufferSizeMB
-- ? stroom.index.writer.cache.coreItems
-- ? stroom.index.writer.cache.maxItems
-- ? stroom.index.writer.cache.minItems
-- ? stroom.index.writer.cache.timeToIdle
-- ? stroom.index.writer.cache.timeToLive
UPDATE config set 
  name = 'stroom.statistics.internal.benchmarkCluster' 
  where name = 'stroom.internalstatistics.benchmarkCluster.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.cpu' 
  where name = 'stroom.internalstatistics.cpu.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.eventsPerSecond' 
  where name = 'stroom.internalstatistics.eventsPerSecond.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.heapHistogramBytes' 
  where name = 'stroom.internalstatistics.heapHistogramBytes.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.heapHistogramInstances' 
  where name = 'stroom.internalstatistics.heapHistogramInstances.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.memory' 
  where name = 'stroom.internalstatistics.memory.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.metaDataStreamSize' 
  where name = 'stroom.internalstatistics.metaDataStreamSize.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.metaDataStreamsReceived' 
  where name = 'stroom.internalstatistics.metaDataStreamsReceived.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.pipelineStreamProcessor' 
  where name = 'stroom.internalstatistics.pipelineStreamProcessor.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.streamTaskQueueSize' 
  where name = 'stroom.internalstatistics.streamTaskQueueSize.docRefs';

UPDATE config set 
  name = 'stroom.statistics.internal.volumes' 
  where name = 'stroom.internalstatistics.volumes.docRefs';

UPDATE config set 
  name = 'stroom.core.connection.jdbcDriverClassName' 
  where name = 'stroom.jdbcDriverClassName';

UPDATE config set 
  name = 'stroom.core.connection.jdbcDriverPassword' 
  where name = 'stroom.jdbcDriverPassword';

UPDATE config set 
  name = 'stroom.core.connection.jdbcDriverUrl' 
  where name = 'stroom.jdbcDriverUrl';

UPDATE config set 
  name = 'stroom.core.connection.jdbcDriverUsername' 
  where name = 'stroom.jdbcDriverUsername';

UPDATE config set 
  name = 'stroom.core.hibernate.dialect' 
  where name = 'stroom.jpaDialect';

UPDATE config set 
  name = 'stroom.core.hibernate.jpaHbm2DdlAuto' 
  where name = 'stroom.jpaHbm2DdlAuto';

-- stroom.lifecycle.enabled
-- stroom.lifecycle.executionInterval
-- stroom.kafka.bootstrap.servers
-- stroom.loginHTML 
UPDATE config set 
  name = 'stroom.ui.maintenanceMessage' 
  where name = 'stroom.maintenance.message';

UPDATE config set 
  name = 'stroom.security.authentication.preventLogin' 
  where name = 'stroom.maintenance.preventLogin';

UPDATE config set 
  name = 'stroom.proxyAggregation.maxAggregation' 
  where name = 'stroom.maxAggregation';

UPDATE config set 
  name = 'stroom.proxyAggregation.maxAggregationScan' 
  where name = 'stroom.maxAggregationScan';

UPDATE config set 
  name = 'stroom.proxyAggregation.maxStreamSize' 
  where name = 'stroom.maxStreamSize';

UPDATE config set 
  name = 'stroom.ui.namePattern' 
  where name = 'stroom.namePattern';

UPDATE config set 
  name = 'stroom.node.node' 
  where name = 'stroom.node';

-- stroom.node.status.heapHistogram.classNameMatchRegex
-- stroom.node.status.heapHistogram.classNameReplacementRegex
-- stroom.node.status.heapHistogram.jMapExecutable
UPDATE config set 
  name = '?' 
  where name = 'stroom.pageTitle';

-- stroom.pipeline.appender.maxActiveDestinations
-- stroom.pipeline.xslt.maxElements
-- stroom.proxy.store.dir ? 
-- stroom.proxy.store.format ?
-- stroom.proxy.store.rollCron ?
-- stroom.proxyDir ?
-- stroom.proxyThreads ?
UPDATE config set 
  name = 'stroom.queryHistory.daysRetention' 
  where name = 'stroom.query.history.daysRetention';

UPDATE config set 
  name = 'stroom.queryHistory.itemsRetention' 
  where name = 'stroom.query.history.itemsRetention';

UPDATE config set 
  name = 'stroom.ui.query.infoPopup.enabled' 
  where name = 'stroom.query.infoPopup.enabled';

UPDATE config set 
  name = 'stroom.ui.query.infoPopup.title' 
  where name = 'stroom.query.infoPopup.title';

UPDATE config set 
  name = 'stroom.ui.query.infoPopup.validationRegex' 
  where name = 'stroom.query.infoPopup.validationRegex';

UPDATE config set 
  name = 'stroom.node.rack' 
  where name = 'stroom.rack';

-- stroom.search.extraction.maxThreads
-- stroom.search.extraction.maxThreadsPerTask
-- stroom.search.maxBooleanClauseCount
-- stroom.search.maxStoredDataQueueSize
UPDATE config set 
  name = 'stroom.ui.process.defaultRecordLimit' 
  where name = 'stroom.search.process.defaultRecordLimit';

UPDATE config set 
  name = 'stroom.ui.process.defaultTimeLimit' 
  where name = 'stroom.search.process.defaultTimeLimit';

-- stroom.search.shard.maxDocIdQueueSize
-- stroom.search.shard.maxThreads
-- stroom.search.shard.maxThreadsPerTask
-- stroom.search.storeSize
UPDATE config set 
  name = 'stroom.security.authentication.apiToken' 
  where name = 'stroom.security.apitoken';

UPDATE config set 
  name = 'stroom.security.authentication.userNamePattern' 
  where name = 'stroom.security.userNamePattern';

UPDATE config set 
  name = 'stroom.serviceDiscovery.curatorBaseSleepTimeMs' 
  where name = 'stroom.serviceDiscovery.curator.baseSleepTimeMs';

UPDATE config set 
  name = 'stroom.serviceDiscovery.curatorMaxRetries' 
  where name = 'stroom.serviceDiscovery.curator.maxRetries';

UPDATE config set 
  name = 'stroom.serviceDiscovery.curatorMaxSleepTimeMs' 
  where name = 'stroom.serviceDiscovery.curator.maxSleepTimeMs';

-- stroom.serviceDiscovery.enabled
-- stroom.serviceDiscovery.servicesHostNameOrIpAddress
-- stroom.serviceDiscovery.servicesPort
-- ? stroom.serviceDiscovery.simpleLookup.basePath
-- stroom.serviceDiscovery.zookeeperBasePath
-- stroom.serviceDiscovery.zookeeperUrl
-- stroom.services.authentication.docRefType
-- stroom.services.authentication.name
-- stroom.services.authentication.version
-- stroom.services.authorisation.docRefType
-- stroom.services.authorisation.name
-- stroom.services.authorisation.version
-- ? stroom.services.sqlStatistics.docRefType ?
-- ? stroom.services.sqlStatistics.name ?
-- ? stroom.services.sqlStatistics.version ?
-- ? stroom.services.stroomIndex.docRefType ?
-- ? stroom.services.stroomIndex.name ?
-- ? stroom.services.stroomIndex.version ?
-- ? stroom.services.stroomStats.docRefType ?
UPDATE config set 
  name = 'stroom.statistics.hbase.eventsPerMessage' 
  where name = 'stroom.services.stroomStats.internalStats.eventsPerMessage';

UPDATE config set 
  name = 'stroom.statistics.hbase.kafkaTopics.count' 
  where name = 'stroom.services.stroomStats.kafkaTopics.count';

UPDATE config set 
  name = 'stroom.statistics.hbase.kafkaTopics.value' 
  where name = 'stroom.services.stroomStats.kafkaTopics.value';

-- ? stroom.services.stroomStats.name ?
-- ? stroom.services.stroomStats.version ?
UPDATE config set 
  name = 'stroom.core.hibernate.showSql' 
  where name = 'stroom.showSql';

UPDATE config set 
  name = 'stroom.ui.splash.body' 
  where name = 'stroom.splash.body';

UPDATE config set 
  name = 'stroom.ui.splash.enabled' 
  where name = 'stroom.splash.enabled';

UPDATE config set 
  name = 'stroom.ui.splash.title' 
  where name = 'stroom.splash.title';

UPDATE config set 
  name = 'stroom.ui.splash.version' 
  where name = 'stroom.splash.version';

-- stroom.statistics.common.statisticEngines
-- ? stroom.statistics.legacy.statisticAggregationBatchSize
-- ? stroom.statistics.sql.db.connectionPool.acquireIncrement
-- ? stroom.statistics.sql.db.connectionPool.acquireRetryAttempts
-- ? stroom.statistics.sql.db.connectionPool.acquireRetryDelay
-- ? stroom.statistics.sql.db.connectionPool.checkoutTimeout
-- ? stroom.statistics.sql.db.connectionPool.idleConnectionTestPeriod
-- ? stroom.statistics.sql.db.connectionPool.initialPoolSize
-- ? stroom.statistics.sql.db.connectionPool.maxConnectionAge
-- ? stroom.statistics.sql.db.connectionPool.maxIdleTime
-- ? stroom.statistics.sql.db.connectionPool.maxIdleTimeExcessConnections
-- ? stroom.statistics.sql.db.connectionPool.maxPoolSize
-- ? stroom.statistics.sql.db.connectionPool.minPoolSize
-- ? stroom.statistics.sql.db.connectionPool.numHelperThreads
-- ? stroom.statistics.sql.db.connectionPool.unreturnedConnectionTimeout
UPDATE config set 
  name = 'stroom.statistics.sql.connection.jdbcDriverClassName' 
  where name = 'stroom.statistics.sql.jdbcDriverClassName';

UPDATE config set 
  name = 'stroom.statistics.sql.connection.jdbcDriverPassword' 
  where name = 'stroom.statistics.sql.jdbcDriverPassword';

UPDATE config set 
  name = 'stroom.statistics.sql.connection.jdbcDriverUrl' 
  where name = 'stroom.statistics.sql.jdbcDriverUrl';

UPDATE config set 
  name = 'stroom.statistics.sql.connection.jdbcDriverUsername' 
  where name = 'stroom.statistics.sql.jdbcDriverUsername';

-- stroom.statistics.sql.maxProcessingAge
-- stroom.statistics.sql.search.fetchSize
-- stroom.statistics.sql.search.maxResults
-- stroom.statistics.sql.search.resultHandlerBatchSize
-- stroom.statistics.sql.statisticAggregationBatchSize
-- ? stroom.stream.deleteBatchSize ? stroom.data.store.deleteBatchSize
-- ? stroom.stream.deletePurgeAge ? stroom.data.store.deletePurgeAge
-- ? stroom.streamAttribute.deleteAge ? stroom.process.deleteAge
-- ? stroom.streamAttribute.deleteBatchSize ?
UPDATE config set 
  name = 'stroom.process.assignTasks' 
  where name = 'stroom.streamTask.assignTasks';

UPDATE config set 
  name = 'stroom.process.createTasks' 
  where name = 'stroom.streamTask.createTasks';

-- ? stroom.streamTask.deleteAge ? stroom.process.deleteAge
-- ? stroom.streamTask.deleteBatchSize ?
UPDATE config set 
  name = 'stroom.process.fillTaskQueue' 
  where name = 'stroom.streamTask.fillTaskQueue';

UPDATE config set 
  name = 'stroom.process.queueSize' 
  where name = 'stroom.streamTask.queueSize';

UPDATE config set 
  name = 'stroom.volumes.preferLocalVolumes' 
  where name = 'stroom.streamstore.preferLocalVolumes';

UPDATE config set 
  name = 'stroom.volumes.resilientReplicationCount' 
  where name = 'stroom.streamstore.resilientReplicationCount';

UPDATE config set 
  name = 'stroom.volumes.volumeSelector' 
  where name = 'stroom.streamstore.volumeSelector';

UPDATE config set 
  name = 'stroom.ui.theme.backgroundAttachment' 
  where name = 'stroom.theme.background-attachment';

UPDATE config set 
  name = 'stroom.ui.theme.backgroundColor' 
  where name = 'stroom.theme.background-color';

UPDATE config set 
  name = 'stroom.ui.theme.backgroundImage' 
  where name = 'stroom.theme.background-image';

UPDATE config set 
  name = 'stroom.ui.theme.backgroundOpacity' 
  where name = 'stroom.theme.background-opacity';

UPDATE config set 
  name = 'stroom.ui.theme.backgroundPosition' 
  where name = 'stroom.theme.background-position';

UPDATE config set 
  name = 'stroom.ui.theme.backgroundRepeat' 
  where name = 'stroom.theme.background-repeat';

UPDATE config set 
  name = 'stroom.ui.theme.labelColours' 
  where name = 'stroom.theme.labelColours';

UPDATE config set 
  name = 'stroom.ui.theme.tubeOpacity' 
  where name = 'stroom.theme.tube.opacity';

UPDATE config set 
  name = 'stroom.ui.theme.tubeVisible' 
  where name = 'stroom.theme.tube.visible';

UPDATE config set 
  name = 'stroom.ui.theme.tubeVisible' 
  where name = 'stroom.unknownClassification';

-- stroom.volumes.createDefaultOnStart
UPDATE config set 
  name = 'stroom.ui.welcomeHtml' 
  where name = 'stroom.welcomeHTML';


