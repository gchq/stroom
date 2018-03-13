/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.StroomCacheManager;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.QueryEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Res;
import stroom.feed.shared.Feed;
import stroom.index.IndexShardManager;
import stroom.index.IndexShardWriterCache;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.jobsystem.shared.ClusterLock;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.node.NodeConfig;
import stroom.node.VolumeService;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.Volume;
import stroom.node.shared.VolumeState;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.ruleset.shared.Policy;
import stroom.script.shared.Script;
import stroom.security.AppPermission;
import stroom.security.DocumentPermission;
import stroom.security.Permission;
import stroom.security.User;
import stroom.security.UserGroupUser;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.streamstore.StreamAttributeKeyService;
import stroom.streamstore.fs.FileSystemUtil;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.StreamTaskCreator;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamTask;
import stroom.util.io.FileUtil;
import stroom.util.spring.StroomBeanStore;
import stroom.visualisation.shared.Visualisation;
import stroom.xmlschema.shared.XMLSchema;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
public class DatabaseCommonTestControl implements CommonTestControl {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCommonTestControl.class);

    private static final List<String> TABLES_TO_CLEAR = Arrays.asList(
            AppPermission.TABLE_NAME,
            ClusterLock.TABLE_NAME,
            Dashboard.TABLE_NAME,
            "doc",
            DocumentPermission.TABLE_NAME,
            "explorerTreeNode",
            "explorerTreePath",
            Feed.TABLE_NAME,
            Index.TABLE_NAME,
            Index.TABLE_NAME_INDEX_VOLUME, //link table between IDX and VOL so no entity of its own
            IndexShard.TABLE_NAME,
            Job.TABLE_NAME,
            JobNode.TABLE_NAME,
            Node.TABLE_NAME,
            Permission.TABLE_NAME,
            PipelineEntity.TABLE_NAME,
            Policy.TABLE_NAME,
            QueryEntity.TABLE_NAME,
            Rack.TABLE_NAME,
            Res.TABLE_NAME,
            Script.TABLE_NAME,
            StatisticStoreEntity.TABLE_NAME,
            Stream.TABLE_NAME,
            StreamAttributeKey.TABLE_NAME,
            StreamAttributeValue.TABLE_NAME,
            StreamProcessor.TABLE_NAME,
            StreamProcessorFilter.TABLE_NAME,
            StreamProcessorFilterTracker.TABLE_NAME,
            StroomStatsStoreEntity.TABLE_NAME,
            StreamTask.TABLE_NAME,
            StreamVolume.TABLE_NAME,
            TextConverter.TABLE_NAME,
            User.TABLE_NAME,
            UserGroupUser.TABLE_NAME,
            Visualisation.TABLE_NAME,
            Volume.TABLE_NAME,
            VolumeState.TABLE_NAME,
            XMLSchema.TABLE_NAME,
            XSLT.TABLE_NAME);

    private final VolumeService volumeService;
    private final ContentImportService contentImportService;
    private final StreamAttributeKeyService streamAttributeKeyService;
    private final IndexShardManager indexShardManager;
    private final IndexShardWriterCache indexShardWriterCache;
    private final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper;
    private final NodeConfig nodeConfig;
    private final StreamTaskCreator streamTaskCreator;
    private final StroomCacheManager stroomCacheManager;
    private final StroomBeanStore beanStore;

    @Inject
    DatabaseCommonTestControl(final VolumeService volumeService,
                              final ContentImportService contentImportService,
                              final StreamAttributeKeyService streamAttributeKeyService,
                              final IndexShardManager indexShardManager,
                              final IndexShardWriterCache indexShardWriterCache,
                              final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper,
                              final NodeConfig nodeConfig,
                              final StreamTaskCreator streamTaskCreator,
                              final StroomCacheManager stroomCacheManager,
                              final StroomBeanStore beanStore) {
        this.volumeService = volumeService;
        this.contentImportService = contentImportService;
        this.streamAttributeKeyService = streamAttributeKeyService;
        this.indexShardManager = indexShardManager;
        this.indexShardWriterCache = indexShardWriterCache;
        this.databaseCommonTestControlTransactionHelper = databaseCommonTestControlTransactionHelper;
        this.nodeConfig = nodeConfig;
        this.streamTaskCreator = streamTaskCreator;
        this.stroomCacheManager = stroomCacheManager;
        this.beanStore = beanStore;
    }

    @Override
    public void setup() {
        Instant startTime = Instant.now();
        //ensure the constraints are enabled in case teardown did not happen on a previous test
        databaseCommonTestControlTransactionHelper.enableConstraints();
        nodeConfig.setup();
        createStreamAttributeKeys();

        // Ensure we can create tasks.
        streamTaskCreator.startup();
        LOGGER.info("test environment setup completed in {}", Duration.between(startTime, Instant.now()));
    }

    /**
     * Clear down the database.
     */
    @Override
    public void teardown() {
        Instant startTime = Instant.now();
        // Make sure we are no longer creating tasks.
        streamTaskCreator.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardWriterCache.shutdown();
        indexShardManager.deleteFromDisk();

        // Delete the contents of all volumes.
        final List<Volume> volumes = volumeService.find(new FindVolumeCriteria());
        for (final Volume volume : volumes) {
            // The parent will also pick up the index shard (as well as the
            // store)
            FileUtil.deleteContents(FileSystemUtil.createFileTypeRoot(volume).getParent());
        }

        //ensure any hibernate entities are flushed down before we clear the tables
        databaseCommonTestControlTransactionHelper.clearContext();

        //clear all the tables using direct sql on a different connection
        //in theory trncating the tables should be quicker but it was takeing 1.5s to trancate all the tables
        //so used delete with no constraint checks instead
        databaseCommonTestControlTransactionHelper.clearTables(TABLES_TO_CLEAR);

        //ensure all the caches are empty
        stroomCacheManager.clear();

        final Set<Clearable> set = beanStore.getBeansOfType(Clearable.class);
        set.forEach(Clearable::clear);
        LOGGER.info("test environment teardown completed in {}", Duration.between(startTime, Instant.now()));
    }

    private void createStreamAttributeKeys() {
        final BaseResultList<StreamAttributeKey> list = streamAttributeKeyService
                .find(new FindStreamAttributeKeyCriteria());
        final HashSet<String> existingItems = new HashSet<>();
        for (final StreamAttributeKey streamAttributeKey : list) {
            existingItems.add(streamAttributeKey.getName());
        }
        for (final String name : StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.keySet()) {
            if (!existingItems.contains(name)) {
                try {
                    streamAttributeKeyService.save(new StreamAttributeKey(name,
                            StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.get(name)));
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public int countEntity(final Class<?> clazz) {
        return databaseCommonTestControlTransactionHelper.countEntity(clazz);
    }

    @Override
    public void deleteEntity(final Class<?> clazz) {
        databaseCommonTestControlTransactionHelper.deleteClass(clazz);
    }

    @Override
    public void shutdown() {
        databaseCommonTestControlTransactionHelper.shutdown();
    }

    // @Override
    @Override
    public void createRequiredXMLSchemas() {
        contentImportService.importXmlSchemas();
    }
}
