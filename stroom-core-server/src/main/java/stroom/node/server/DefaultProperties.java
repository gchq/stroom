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

package stroom.node.server;

import stroom.node.shared.GlobalProperty;

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
        list.add(new GlobalProperty.Builder()
                .name("stroom.developmentMode")
                .value("false")
                .description("")
                .build());

        // SECURITY PROPERTIES 
        list.add(new GlobalProperty.Builder()
                .name("stroom.security.allowCertificateAuthentication")
                .value("false")
                .description("Choose whether Stroom should allow UI access using certificates")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.security.certificateDNPattern")
                .value("CN=[^ ]+ [^ ]+ \\(?([a-zA-Z0-9]+)\\)?")
                .description("The regular expression to use to extract user names from a certificate DN")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.security.userNamePattern")
                .value("^[a-zA-Z0-9_-]{3,}$")
                .description("The regex pattern for user names")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.daysToAccountExpiry")
                .value("90")
                .description("Number of days before we disable an account")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.daysToUnusedAccountExpiry")
                .value("30")
                .description("Number of days before we disable an unused account")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.daysToPasswordExpiry")
                .value("90")
                .description("Number of days before passwords expire")
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
                .value("1000000")
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
                .value("1000")
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
                .name("stroom.search.maxResults")
                .value("1000000,100,10,1")
                .description("The maximum number of search results to keep in memory at each level.")
                .editable(true)
                .build());

        // SEARCH BASED PROCESSING 
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.process.defaultTimeLimit")
                .value("30")
                .description("The default number of minutes that batch search processing will be limited by.")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.search.process.defaultRecordLimit")
                .value("1000000")
                .description("The default number of records that batch search processing will be limited by.")
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
                .value("3")
                .description("The initial size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.minPoolSize")
                .value("3")
                .description("The minimum size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.db.connectionPool.maxPoolSize")
                .value("15")
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
                .value("3")
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
                .value("3")
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
                .value("3")
                .description("The initial size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.minPoolSize")
                .value("3")
                .description("The minimum size of the DB connection pool")
                .editable(true)
                .requireRestart(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.sql.db.connectionPool.maxPoolSize")
                .value("15")
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
                .value("3")
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
                .value("3")
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

        // EMAIL 
        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.host")
                .value("")
                .description("Email service host name, e.g. smtp.gmail.com")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.port")
                .value("587")
                .description("Email service port, e.g. 587")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.protocol")
                .value("smtp")
                .description("Email service protocol, e.g. smtp")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.userName")
                .value("")
                .description("Email service username, e.g. XXXXX@gmail.com")
                .editable(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.password")
                .value("")
                .description("Email service password")
                .editable(true)
                .password(true)
                .build());
        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.propertiesFile")
                .value("~/.stroom/mail.properties")
                .description("Additional mail properties can be defined in a file at the specified path, e.g. ~/.stroom/mail.properties")
                .editable(true)
                .build());

        list.add(new GlobalProperty.Builder()
                .name("stroom.mail.userDomain")
                .value("")
                .description("User Domain (to append to user names)")
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
                .value("url(../images/theme/grid.png)")
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

        // Common statistics store properties 
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.common.statisticEngines")
                .value("sql")
                .description("Comma delimited list of enabled engines that implement Statistic Event Store (currently 'sql')")
                .editable(true)
                .requireUiRestart(true)
                .build());

        // Legacy statistics store properties 
        list.add(new GlobalProperty.Builder()
                .name("stroom.statistics.legacy.statisticAggregationBatchSize")
                .value("1000000")
                .description("Number of STAT_VAL_SRC records to merge into STAT_VAL in one batch")
                .editable(true)
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
                .name("stroom.node.status.heapHistogram.jMapExecutable")
                .value("jmap")
                .description("The jmap executable name if it is available on the PATH or a fully qualified form")
                .build());

        return Collections.unmodifiableList(list);
    }
}
