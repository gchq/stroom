/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License,Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,software
 * distributed under the License is distributed on an "AS IS" BASIS,* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.properties;

import stroom.node.shared.GlobalProperty;
import stroom.util.ByteSizeUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultProperties {
    private DefaultProperties() {
    }

    public static List<GlobalProperty> getList() {
        final List<GlobalProperty> list = new ArrayList<>();

        list.add(new GlobalProperty.Builder()
                .name("stroom.temp")
                .description("Temp folder to write stuff to. Should only be set per node in application property file")
                .build());


        // Stroom Proxy Repository and Aggregation
        list.add(new GlobalProperty.Builder()
                .name("stroom.feed.receiptPolicyUuid")
                .value("")
                .description("The UUID of the data receipt policy to use")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.proxyDir")
                .value("${stroom.temp}/proxy")
                .description("Folder to look for Stroom Proxy Content to aggregate")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.proxyThreads")
                .value("10")
                .description("Number of threads used in aggregation")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.maxAggregation")
                .value("10000")
                .description("This stops the aggregation after a certain size / nested streams")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.maxAggregationScan")
                .value("100000")
                .description("The limit of files to inspect before aggregation begins (should be bigger than maxAggregation)")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.maxStreamSize")
                .value("1G")
                .description("This stops the aggregation after a certain size / nested streams")
                .editable(true)
                .build());


        // STREAM STORE
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamstore.resilientReplicationCount")
                .value("1")
                .description("Set to determine how many volume locations will be used to store a single stream")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamstore.preferLocalVolumes")
                .value("false")
                .description("Should the stream store always attempt to write to local volumes before writing to remote ones?")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamstore.volumeSelector")
                .value("RoundRobin")
                .description("How should volumes be selected for use? Possible volume selectors include ('MostFreePercent', 'MostFree', 'Random', 'RoundRobinIgnoreLeastFreePercent', 'RoundRobinIgnoreLeastFree', 'RoundRobin', 'WeightedFreePercentRandom', 'WeightedFreeRandom') default is 'RoundRobin'")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamAttribute.deleteAge")
                .value("30d")
                .description("The age of streams that we store meta data in the database for")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamAttribute.deleteBatchSize")
                .value("1000")
                .description("How many stream attributes we want to try and delete in a single batch")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.stream.deletePurgeAge")
                .value("7d")
                .description("How long a stream is left logically deleted before it is deleted from the database")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.stream.deleteBatchSize")
                .value("1000")
                .description("How many streams we want to try and delete in a single batch")
                .editable(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.fileSystemCleanBatchSize")
                .value("20")
                .description("Set child jobs to be created by the file system clean sub task")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.fileSystemCleanDeleteOut")
                .value("false")
                .description("Write a delete out in the root of the volume rather than physically deleting the files")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.fileSystemCleanOldAge")
                .value("1d")
                .description("Duration until a file is deemed old")
                .editable(true)
                .build());

        // STREAM TASKS
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamTask.fillTaskQueue")
                .value("true")
                .description("Should the master node fill the task queue ready for workers to fetch tasks?")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamTask.createTasks")
                .value("true")
                .description("Should the master node create new tasks for stream processor filters?")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamTask.assignTasks")
                .value("true")
                .description("Should the master node assign tasks to workers when tasks are requested?")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamTask.deleteAge")
                .value("1d")
                .description("How long to keep tasks on the database for before deleting them (if they are complete)")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamTask.deleteBatchSize")
                .value("1000")
                .description("How many streams tasks we want to try and delete in a single batch")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.streamTask.queueSize")
                .value("1000")
                .description("Maximum number of tasks to cache ready for processing per processor filter")
                .editable(true)
                .build());

        // BENCHMARK
        list.add(new GlobalProperty.Builder()
                .name("stroom.benchmark.streamCount")
                .value("1000")
                .description("Set the number of streams to be created during a benchmark test")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.benchmark.recordCount")
                .value("10000")
                .description("Set the number of records to be created for each stream during a benchmark test")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.benchmark.concurrentWriters")
                .value("10")
                .description("Set the number of threads to use concurrently to write test streams")
                .editable(true)
                .build());


        list.add(new GlobalProperty.Builder()
                .name("stroom.bufferSize")
                .value("4096")
                .description("If set the default buffer size to use")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.node")
                .value("tba")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.rack")
                .value("tba")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.lifecycle.enabled")
                .value("true")
                .description("Set this to false for development and testing purposes otherwise the Stroom will try and process files automatically outside of test cases.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.lifecycle.executionInterval")
                .value("10s")
                .description("How frequently should the lifecycle service attempt execution.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.maintenance.message")
                .value("")
                .description("Provide a warning message to users about an outage or other significant event.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.maintenance.preventLogin")
                .value("false")
                .description("Prevent new logins to the system. This is useful if the system is scheduled to have an outage.")
                .editable(true)
                .build());

        // INDEX PROPERTIES
        list.add(new GlobalProperty.Builder()
                .name("stroom.index.ramBufferSizeMB")
                .value("1024")
                .description("The amount of RAM Lucene can use to buffer when indexing in Mb")
                .editable(true)
                .build());


        // INDEX WRITER CACHE PROPERTIES
        list.add(new GlobalProperty.Builder()
                .name("stroom.index.writer.cache.timeToLive")
                .value("")
                .description("How long a cache item can live before it is removed from the cache during a sweep")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.index.writer.cache.timeToIdle")
                .value("10m")
                .description("How long a cache item can idle before it is removed from the cache during a sweep")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.index.writer.cache.minItems")
                .value("0")
                .description("The minimum number of items that will be left in the cache after a sweep")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.index.writer.cache.coreItems")
                .value("50")
                .description("The number of items that we hope to keep in the cache if items aren't removed due to TTL or TTI constraints")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.index.writer.cache.maxItems")
                .value("100")
                .description("The maximum number of items that can be kept in the cache. LRU items are removed to ensure we do not exceed this amount")
                .editable(true)
                .build());

        // QUERY HISTORY PROPERTIES
        list.add(new GlobalProperty.Builder()
                .name("stroom.query.history.itemsRetention")
                .value("100")
                .description("The maximum number of query history items that will be retained")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.query.history.daysRetention")
                .value("365")
                .description("The number of days query history items will be retained for")
                .editable(true)
                .build());

        // SEARCH PROPERTIES
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.maxStoredDataQueueSize")
                .value("1000")
                .description("The maximum number documents that will have stored data retrieved from the index shard and queued prior to further processing")
                .editable(true)
                .build());

        // SEARCH SHARD
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.shard.maxOpen")
                .value("5")
                .description("The number of open Lucene index shards to cache on each node")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.shard.maxDocIdQueueSize")
                .value("1000000")
                .description("The maximum number of doc ids that will be queued ready for stored data to be retrieved from the index shard")
                .editable(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.search.shard.maxThreads")
                .value("4")
                .description("The absolute maximum number of threads per node, used to search Lucene index shards across all searches")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.shard.maxThreadsPerTask")
                .value("2")
                .description("The maximum number of threads per search, per node, used to search Lucene index shards")
                .editable(true)
                .build());

        // SEARCH EXTRACTION
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.extraction.maxThreads")
                .value("4")
                .description("The absolute maximum number of threads per node, used to extract search results from streams using a pipeline")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.extraction.maxThreadsPerTask")
                .value("2")
                .description("The maximum number of threads per search, per node, used to extract search results from streams using a pipeline")
                .editable(true)
                .build());

        // SEARCH SENDER
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.maxBooleanClauseCount")
                .value("1024")
                .description("The maximum number of clauses that a boolean search can contain.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.storeSize")
                .value("1000000,100,10,1")
                .description("The maximum number of search results to keep in memory at each level.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.dashboard.defaultMaxResults")
                .value("1000000,100,10,1")
                .description("The default maximum number of search results to return to the dashboard, unless the user requests lower values")
                .editable(true)
                .build());

        // SEARCH BASED PROCESSING
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.process.defaultTimeLimit")
                .value("30")
                .description("The default number of minutes that search processing will be limited by.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.process.defaultRecordLimit")
                .value("1000000")
                .description("The default number of records that search processing will be limited by.")
                .editable(true)
                .build());

        // PIPELINE PROPERTIES
        list.add(new GlobalProperty.Builder()
                .name("stroom.pipeline.xslt.maxElements")
                .value("1000000")
                .description("The maximum number of elements that the XSLT filter will expect to receive before it errors. This protects Stroom from ruinning out of memory in cases where an appropriate XML splitter has not been used in a pipeline.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.pipeline.appender.maxActiveDestinations")
                .value("100")
                .description("The maximum number active destinations that Stroom will allow rolling appenders to be writing to at any one time.")
                .editable(true)
                .build());

        // MAIN DATA SOURCE
        list.add(new GlobalProperty.Builder()
                .name("stroom.jpaHbm2DdlAuto")
                .value("validate")
                .description("Set by property file to enable auto schema creation")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.jdbcDriverClassName")
                .value("org.hsqldb.jdbcDriver")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.jpaDialect")
                .value("org.hibernate.dialect.HSQLDialect")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.jdbcDriverUrl")
                .value("jdbc:hsqldb:file:${stroom.temp}/stroom/HSQLDB.DAT;shutdown=true")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.jdbcDriverUsername")
                .value("sa")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.jdbcDriverPassword")
                .value("")
                .description("Should only be set per node in application property file")
                .password(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.showSql")
                .value("false")
                .description("Log SQL")
                .editable(true)
                .requireRestart(true)
                .build());

        // C3P0
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.initialPoolSize")
                .value("1")
                .description("The initial size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.minPoolSize")
                .value("1")
                .description("The minimum size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.maxPoolSize")
                .value("10")
                .description("The maximum size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.idleConnectionTestPeriod")
                .value("0")
                .description("If this is a number greater than 0, we will test all idle, pooled but unchecked-out connections, every this number of seconds")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.maxIdleTime")
                .value("0")
                .description("The seconds a connection can remain pooled but unused before being discarded. Zero means idle connections never expire.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.acquireIncrement")
                .value("1")
                .description("Determines how many connections to list.add to the pool in one go.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.acquireRetryAttempts")
                .value("30")
                .description("Determines how many attempts we make to acquire a connection.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.acquireRetryDelay")
                .value("1000")
                .description("Determines how long we wait (in milliseconds) between attempts to acquire a connection.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.checkoutTimeout")
                .value("0")
                .description("Determines how long (in seconds) a connection can be checked out from the pool before it is discarded.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.maxIdleTimeExcessConnections")
                .value("60")
                .description("Determines how long (in seconds) excess connections remain in the pool before being discarded.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.maxConnectionAge")
                .value("0")
                .description("Determines how long in seconds a connection will remain in the pool before being discarded.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.unreturnedConnectionTimeout")
                .value("0")
                .description("How long to wait in seconds before unreturned connections are forcibly closed by the pool. This property helps with the diagnosis of connection leaks, i.e. areas of an application that might be getting a connection but never returning it. This should not be used in a production environment.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.numHelperThreads")
                .value("1")
                .description("The connection pool is very asynchronous. Slow JDBC operations are generally performed by helper threads that don't hold contended locks. Spreading these operations over multiple threads can significantly improve performance by allowing multiple operations to be performed simultaneously.")
                .editable(true)
                .requireRestart(true)
                .build());

        // SQL STATISTICS DATA SOURCE
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.jdbcDriverClassName")
                .value("org.hsqldb.jdbcDriver")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.jdbcDriverUrl")
                .value("jdbc:hsqldb:file:${stroom.temp}/statistics/HSQLDB.DAT;shutdown=true")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.jdbcDriverUsername")
                .value("sa")
                .description("Should only be set per node in application property file")
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.jdbcDriverPassword")
                .value("")
                .description("Should only be set per node in application property file")
                .password(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.databaseMultiInsertMaxBatchSize")
                .value("500")
                .description("The maximum number of rows to insert in a single multi insert statement, e.g. INSERT INTO X VALUES (...), (...), (...)")
                .editable(true)
                .build());

        // C3P0
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.initialPoolSize")
                .value("1")
                .description("The initial size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.minPoolSize")
                .value("1")
                .description("The minimum size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.maxPoolSize")
                .value("10")
                .description("The maximum size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.idleConnectionTestPeriod")
                .value("0")
                .description("If this is a number greater than 0, we will test all idle, pooled but unchecked-out connections, every this number of seconds")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.maxIdleTime")
                .value("0")
                .description("The seconds a connection can remain pooled but unused before being discarded. Zero means idle connections never expire.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.acquireIncrement")
                .value("1")
                .description("Determines how many connections to list.add to the pool in one go.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.acquireRetryAttempts")
                .value("30")
                .description("Determines how many attempts we make to acquire a connection.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.acquireRetryDelay")
                .value("1000")
                .description("Determines how long we wait (in milliseconds) between attempts to acquire a connection.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.checkoutTimeout")
                .value("0")
                .description("Determines how long (in seconds) a connection can be checked out from the pool before it is discarded.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.maxIdleTimeExcessConnections")
                .value("60")
                .description("Determines how long (in seconds) excess connections remain in the pool before being discarded.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.maxConnectionAge")
                .value("0")
                .description("Determines how long in seconds a connection will remain in the pool before being discarded.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.unreturnedConnectionTimeout")
                .value("0")
                .description("How long to wait in seconds before unreturned connections are forcibly closed by the pool. This property helps with the diagnosis of connection leaks, i.e. areas of an application that might be getting a connection but never returning it. This should not be used in a production environment.")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.numHelperThreads")
                .value("1")
                .description("The connection pool is very asynchronous. Slow JDBC operations are generally performed by helper threads that don't hold contended locks. Spreading these operations over multiple threads can significantly improve performance by allowing multiple operations to be performed simultaneously.")
                .editable(true)
                .requireRestart(true)
                .build());

        // Entity Names
        list.add(new GlobalProperty.Builder()
                .name("stroom.namePattern")
                .value("^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$")
                .description("The regex pattern for entity names")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.feedNamePattern")
                .value("^[A-Z0-9_-]{3,}$")
                .description("The regex pattern for feed names")
                .editable(true)
                .build());

        // Welcome text
        list.add(new GlobalProperty.Builder()
                .name("stroom.pageTitle")
                .value("Stroom")
                .description("The page title for Stroom shown in the browser tab")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.loginHTML")
                .value("<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>")
                .description("HTML")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.welcomeHTML")
                .value("<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>")
                .description("HTML")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.aboutHTML")
                .value("<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>")
                .description("HTML")
                .editable(true)
                .requireRestart(true)
                .build());

        // CLUSTER COMMUNICATION
        list.add(new GlobalProperty.Builder()
                .name("stroom.clusterCallUseLocal")
                .value("true")
                .description("Do local calls when calling our own local services (true is an optimisation)")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.clusterCallReadTimeout")
                .value("30s")
                .description("Time in ms (but can be specified as 10s, 1m) before throwing read timeout")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.clusterCallIgnoreSSLHostnameVerifier")
                .value("true")
                .description("If cluster calls are using SSL then choose if we want to ignore host name verification")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.clusterResponseTimeout")
                .value("30s")
                .description("Time in ms (but can be specified as 10s, 1m) before giving up on cluster results")
                .editable(true)
                .build());

        // EXPORT
        list.add(new GlobalProperty.Builder()
                .name("stroom.export.enabled")
                .value("false")
                .description("Determines if the system will allow configuration to be exported via the export servlet")
                .editable(true)
                .build());

        // UI
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.background-attachment")
                .value("scroll")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.background-color")
                .value("#1E88E5") // #FF6F00
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.background-image")
                .value("none")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.background-position")
                .value("0 0")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.background-repeat")
                .value("repeat")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.background-opacity")
                .value("0")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.tube.visible")
                .value("hidden")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.tube.opacity")
                .value("0.6")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.unknownClassification")
                .value("UNKNOWN CLASSIFICATION")
                .description("GUI")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.theme.labelColours")
                .value("TEST1=#FF0000,TEST2=#FF9900")
                .description("A comma separated list of KV pairs to provide colours for labels.")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.helpUrl")
                .value("")
                .description("The URL of hosted help files.")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // Query info popup
        list.add(new GlobalProperty.Builder()
                .name("stroom.query.infoPopup.enabled")
                .value("false")
                .description("If you would like users to provide some query info when performing a query set this property to true.")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.query.infoPopup.title")
                .value("Please Provide Query Info")
                .description("The title of the query info popup.")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.query.infoPopup.validationRegex")
                .value("^[\\s\\S]{3,}$")
                .description("A regex used to validate query info.")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // SQL statistics store properties
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.statisticAggregationBatchSize")
                .value("1000000")
                .description("Number of SQL_STAT_VAL_SRC records to merge into SQL_STAT_VAL in one batch")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.search.maxResults")
                .value("100000")
                .description("The maximum number of records that can be returned from the statistics DB in a single query prior to aggregation")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.search.fetchSize")
                .value("5000")
                .description("Gives the JDBC driver a hint as to the number of rows that should be fetched from the database when more rows are needed for ResultSet objects generated by this Statement. Depends on 'useCursorFetch=true' being set in the JDBC connect string. If not set, the JDBC driver's default will be used.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.search.resultHandlerBatchSize")
                .value("5000")
                .description("The number of database rows to pass to the result handler")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.maxProcessingAge")
                .value("")
                .description("The maximum age (e.g. '90d') of statistics to process and retain, i.e. any statistics with an statistic event time older than the current time minus maxProcessingAge will be silently dropped.  Existing statistic data over this age will be purged during statistic aggregation. Leave blank to process/retain all data.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.contentPackImportEnabled")
                .value("true")
                .description("If true any content packs found in ${CATALINA_BASE}/contentPackImport/ will be imported into Stroom. Only intended for use on new Stroom instances to reduce the risk of overwriting existing entities")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.volumes.createDefaultOnStart")
                .value("true")
                .description("If no existing volumes are present a default volume will be created on application start. The volume will live in the volumes sub directory of the Stroom installation directory")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.node.status.heapHistogram.classNameMatchRegex")
                .value("^stroom\\..*$")
                .description("A single regex that will be used to filter classes from the jmap histogram internal statistic based on their name. e.g '^(stroom\\..*)$'. If no value is supplied all classes will be included. If a value is supplied only those class names matching the regex will be included.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.node.status.heapHistogram.classNameReplacementRegex")
                // This is XML so XML escaping rules apply, not java's 
                .value("((?<=\\$Proxy)[0-9]+|(?<=\\$\\$)[0-9a-f]+|(?<=\\$\\$Lambda\\$)[0-9]+\\/[0-9]+)")
                .description("A single regex that will be used to replace all matches in the class name with '--REPLACED--'. This is to prevent ids for anonymous inner classes and lambdas from being included in the class name. E.g '....DocRefResourceHttpClient$$Lambda$46/1402766141' becomes '....DocRefResourceHttpClient$$Lambda$--REPLACED--'. ")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.node.status.heapHistogram.jMapExecutable")
                .value("jmap")
                .description("The jmap executable name if it is available on the PATH or a fully qualified form")
                .build());


        // External Service properties, including DocRef.type name mappings
        // ========================================START=========================================== 

        // Stroom-Index 
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomIndex.name")
                .value("stroom-index")
                .description("Name of the index service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomIndex.version")
                .value("1")
                .description("Version of the index service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomIndex.docRefType")
                .value("Index")
                .description("The entity type for the index service")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // Authentication 
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.authentication.name")
                .value("authentication")
                .description("Name of the authentication service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.authentication.version")
                .value("1")
                .description("Version of the authentication service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.authentication.docRefType")
                .value("authentication")
                .description("The entity type for the authentication service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.advertisedUrl")
                .value("")
                .description("The URL of Stroom as provided to the browser")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.auth.services.url")
                .value("http://auth-service:8099")
                .description("The URL of the auth service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.auth.authentication.service.url")
                .value("http://auth-service:8099/authentication/v1")
                .description("The URL of the authentication service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.auth.jwt.issuer")
                .value("stroom")
                .description("The issuer to expect when verifying JWTs.")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.auth.jwt.enabletokenrevocationcheck")
                .value("true")
                .description("Whether or not to enable remote calls to the auth service to check if a token we have has been revoked.")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.security.apitoken")
                .value("eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE1Mzg2NDM1NTQsInN1YiI6ImFkbWluIiwiaXNzIjoic3Ryb29tIn0.J8dqtQf9gGXQlKU_rAye46lUKlJR8-vcyrYhOD0Rxoc")
                .description("The API token Stroom will use to authenticate itself when accessing other services")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.security.userNamePattern")
                .value("^[a-zA-Z0-9_-]{3,}$")
                .description("The regex pattern for user names")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.authentication.required")
                .value("true")
                .description("Choose whether Stroom requires authenticated access")
                .build());

        // Authorisation 
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.authorisation.name")
                .value("authorisation")
                .description("Name of the authorisation service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.authorisation.version")
                .value("1")
                .description("Version of the authorisation service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.authorisation.docRefType")
                .value("authorisation")
                .description("The entity type for the authorisation service")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // Stroom-Stats 
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomStats.name")
                .value("stroom-stats")
                .description("The name of the stroom-stats service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomStats.version")
                .value("2")
                .description("The version of the stroom-stats service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomStats.docRefType")
                .value("StroomStatsStore")
                .description("The entity type for the stroom-stats service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomStats.kafkaTopics.count")
                .value("statisticEvents-Count")
                .description("The kafka topic to send Count type stroom-stats statistic events to")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomStats.kafkaTopics.value")
                .value("statisticEvents-Value")
                .description("The kafka topic to send Value type stroom-stats statistic events to")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.stroomStats.internalStats.eventsPerMessage")
                .value("100")
                .description("The number of internal statistic events to batch together in a single Kafka message. High numbers reduce network overhead but limit the parallelism.")
                .editable(true)
                .build());

        // SQL Statistics 
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.sqlStatistics.name")
                .value("sql_statistics")
                .description("The name of the built-in sql statistics service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.sqlStatistics.version")
                .value("1")
                .description("The version of the built-in sql statistics service")
                .editable(true)
                .requireUiRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.services.sqlStatistics.docRefType")
                .value("StatisticStore")
                .description("The entity type for the sql statistics service")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // ========================================END=========================================== 


        // Kafka properties 
        // ========================================START=========================================== 
        list.add(new GlobalProperty.Builder()
                .name("stroom.kafka.bootstrap.servers")
                .value("localhost:9092")
                .description("The list of kafka brokers to initially connect to to obtain the full set of kafka brokers, in the form `host1:port,host2:port,etc'")
                .editable(true)
                .requireUiRestart(true)
                .build());


        // ========================================END=========================================== 


        // Service discovery properties 
        // ========================================START=========================================== 
        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.enabled")
                .value("true")
                .description("Set this to true to use Zookeeper for service discovery. Set this to false to use resolve all services locally, i.e. 127.0.0.1")
                .editable(true)
                .requireRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.simpleLookup.basePath")
                .value("http://127.0.0.1:8080")
                .description("The base path to connect to local services on, when not using service discovery")
                .editable(true)
                .requireRestart(true)
                .build());


        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.zookeeperUrl")
                .value("localhost:2181")
                .description("The Zookeeper quorum connection string, required for service discovery, in the form 'host1:port1,host2:port2,host3:port3'. The root znode to use in Zookeeper is defined in the property stroom.serviceDiscovery.zookeeperBasePath")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.servicesHostNameOrIpAddress")
                .value("localhost")
                .description("The external facing address that stroom will register its services with service discovery. If this property is empty stroom will try to establish the hostname. Recommended to be left blank in production to avoid having host specific configuration, unless the hostname is that of a load balancer in front of stroom instances.")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.servicesPort")
                .value("8080")
                .description("The external facing port that stroom will register its services with service discovery")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.curator.baseSleepTimeMs")
                .value("5000")
                .description("Initial time in ms between retries to establish a connection to zookeeper")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.curator.maxSleepTimeMs")
                .value("300000")
                .description("Maximum time in ms between retries to establish a connection to zookeeper")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.curator.maxRetries")
                .value("100")
                .description("Maximum number of retries to establish a connection to zookeeper before giving up")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.serviceDiscovery.zookeeperBasePath")
                .value("/stroom-services")
                .description("The base path to use in zookeeper for Curator service discover. All services registering or querying discoverable services must use the same value for this base path. Must start with a '/'")
                .editable(true)
                .requireUiRestart(true)
                .build());
        // ========================================END=========================================== 


        // Internal statistics definitions
        // Each internal statistic can have multiple docRef definitions, one for each statistics engine,
        //            currently 'sql_statistics' and 'stroom-stats'
        // Valid values for docRef.type are 'StatisticStore' and 'StroomStatsStore'
        // The name and uuid so match those defined for the statistic in stroom-content
        // If an internal statistic docRefs property has an empty value then the statistic events will be silently
        // ignored.

        // ========================================START=========================================== 
        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.benchmarkCluster.docRefs")
                .value("docRef(StatisticStore,946a88c6-a59a-11e6-bdc4-0242ac110002,Benchmark-Cluster Test),docRef(StroomStatsStore,2503f703-5ce0-4432-b9d4-e3272178f47e,Benchmark-Cluster Test)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.pipelineStreamProcessor.docRefs")
                .value("docRef(StatisticStore,946a80fc-a59a-11e6-bdc4-0242ac110002,PipelineStreamProcessor),docRef(StroomStatsStore,efd9bad4-0bab-460f-ae98-79e9717deeaf,PipelineStreamProcessor)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.metaDataStreamSize.docRefs")
                .value("docRef(StatisticStore,946a8814-a59a-11e6-bdc4-0242ac110002,Meta Data-Stream Size),docRef(StroomStatsStore,3b25d63b-5472-44d0-80e8-8eea94f40f14,Meta Data-Stream Size)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.eventsPerSecond.docRefs")
                .value("docRef(StatisticStore,a9936548-2572-448b-9d5b-8543052c4d92,EPS),docRef(StroomStatsStore,cde67df0-0f77-45d3-b2c0-ee8bb7b3c9c6,EPS)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.cpu.docRefs")
                .value("docRef(StatisticStore,af08c4a7-ee7c-44e4-8f5e-e9c6be280434,CPU),docRef(StroomStatsStore,1edfd582-5e60-413a-b91c-151bd544da47,CPU)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.metaDataStreamsReceived.docRefs")
                .value("docRef(StatisticStore,946a87bc-a59a-11e6-bdc4-0242ac110002,Meta Data-Streams Received),docRef(StroomStatsStore,5535f493-29ae-4ee6-bba6-735aa3104136,Meta Data-Streams Received)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.streamTaskQueueSize.docRefs")
                .value("docRef(StatisticStore,946a7f0f-a59a-11e6-bdc4-0242ac110002,Stream Task Queue Size),docRef(StroomStatsStore,4ce8d6e7-94be-40e1-8294-bf29dd089962,Stream Task Queue Size)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.volumes.docRefs")
                .value("docRef(StatisticStore,ac4d8d10-6f75-4946-9708-18b8cb42a5a3,Volumes),docRef(StroomStatsStore,60f4f5f0-4cc3-42d6-8fe7-21a7cec30f8e,Volumes)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.memory.docRefs")
                .value("docRef(StatisticStore,77c09ccb-e251-4ca5-bca0-56a842654397,Memory),docRef(StroomStatsStore,d8a7da4f-ef6d-47e0-b16a-af26367a2798,Memory)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...'")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.heapHistogramInstances.docRefs")
                .value("docRef(StatisticStore,e4f243b8-2c70-4d6e-9d5a-16466bf8764f,Heap Histogram Instances),docRef(StroomStatsStore,bdd933a4-4309-47fd-98f6-1bc2eb555f20,Heap Histogram Instances)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...'")
                .editable(true)
                .requireUiRestart(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.internalstatistics.heapHistogramBytes.docRefs")
                .value("docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes),docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)")
                .description("Comma delimited list of zero to many DocRefs in the form 'docRef(type,uuid,name),docRef(type,uuid,name),...'")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // ========================================END=========================================== 

        // Stroom Proxy Store for Pipeline Use 
        list.add(new GlobalProperty.Builder()
                .name("stroom.proxy.store.dir")
                .value("${stroom.temp}/stroom-proxy")
                .description("The stroom proxy dir to write data to from a pipeline")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.proxy.store.format")
                .value("#{'$'}{pathId}/#{'$'}{id}")
                .description("The format to use for the stroom proxy store")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.proxy.store.rollCron")
                .value("")
                .description("How often should the stroom proxy store be rolled")
                .editable(true)
                .build());

        // ========================================START===========================================
        // Reference data loader properties

        list.add(new GlobalProperty.Builder()
                .name("stroom.refloader.offheapstore.localDir")
                .value("${stroom.temp}/refDataOffHeapStore")
                .description("The full directory path  to use for storing the reference data store. It MUST be on local disk, NOT network storage, due to use of memory mapped files. The directory will be created if it doesn't exist.")
                .editable(false)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.refloader.offheapstore.maxStoreSize")
                .value(Long.toString(ByteSizeUnit.GIBIBYTE.longBytes(50)))
                .description("The maximum size in bytes for the ref loader off heap store. There must be available space on the disk to accommodate this size. It can be larger than the amount of available RAM.")
                .editable(false)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.refloader.offheapstore.deleteAge")
                .value("30d")
                .description("The time to retain reference data for in the off heap store. The time is taken from the time that the reference stream was last accessed, e.g. a lookup was made against it.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.refloader.offheapstore.maxReaders")
                .value("100")
                .description("The maximum number of concurrent readers/threads that can use the offheapstore.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.refloader.offheapstore.maxPutsBeforeCommit")
                .value("1000")
                .description("The maximum number of puts into the store before the transaction is committed. There is only one write transaction available long running transactions are not desirable.")
                .editable(false)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.refloader.offheapstore.valueBufferCapacity")
                .value("500000")
                .description("The size in bytes allocated to the value buffers used in the offheapstore. This should be large enough to accommodate reference data values.")
                .editable(false)
                .build());

        // ========================================END===========================================

        return Collections.unmodifiableList(list);
    }
}
