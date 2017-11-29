/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import stroom.cache.StroomCacheManager;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.QueryEntity;
import stroom.dictionary.shared.Dictionary;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Res;
import stroom.explorer.server.ExplorerNodeService;
import stroom.feed.shared.Feed;
import stroom.index.server.IndexShardManager;
import stroom.index.server.IndexShardService;
import stroom.index.server.IndexShardWriterCache;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.lifecycle.LifecycleServiceImpl;
import stroom.node.server.NodeConfig;
import stroom.node.server.VolumeService;
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
import stroom.security.server.DocumentPermission;
import stroom.security.server.Permission;
import stroom.security.server.User;
import stroom.security.server.UserGroupUser;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.streamstore.server.StreamAttributeKeyService;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamTask;
import stroom.visualisation.shared.Visualisation;
import stroom.xmlschema.shared.XMLSchema;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
@Component
public class DatabaseCommonTestControl implements CommonTestControl, ApplicationContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCommonTestControl.class);

    @Resource
    private VolumeService volumeService;
    @Resource
    private IndexShardService indexShardService;
    @Resource
    private ContentImportService contentImportService;
    @Resource
    private StreamAttributeKeyService streamAttributeKeyService;
    @Resource
    private IndexShardManager indexShardManager;
    @Resource
    private IndexShardWriterCache indexShardWriterCache;
    @Resource
    private DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper;
    @Resource
    private NodeConfig nodeConfig;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private LifecycleServiceImpl lifecycleServiceImpl;
    @Resource
    private StroomCacheManager stroomCacheManager;
    @Resource
    private ExplorerNodeService explorerNodeService;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setup() {
        Instant startTime = Instant.now();
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
        deleteEntity(StreamTask.class);

        deleteEntity(StreamVolume.class);
        deleteEntity(StreamAttributeValue.class);
        deleteEntity(Stream.class);

        deleteEntity(Policy.class);

        deleteEntity(QueryEntity.class);
        deleteEntity(Dashboard.class);
        deleteEntity(Visualisation.class);
        deleteEntity(Script.class);
        deleteEntity(Res.class);
        deleteEntity(Dictionary.class);
        deleteEntity(StatisticStoreEntity.class);
        deleteEntity(StroomStatsStoreEntity.class);

        // Make sure we are no longer creating tasks.
        streamTaskCreator.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardWriterCache.shutdown();
        indexShardManager.deleteFromDisk();

        deleteEntity(IndexShard.class);
        deleteEntity(Index.class);

        deleteEntity(Feed.class);

        deleteEntity(XMLSchema.class);
        deleteEntity(TextConverter.class);
        deleteEntity(XSLT.class);

        deleteEntity(StreamProcessorFilter.class);
        deleteEntity(StreamProcessorFilterTracker.class);
        deleteEntity(StreamProcessor.class);

        deleteEntity(PipelineEntity.class);

        deleteEntity(UserGroupUser.class);
        deleteEntity(DocumentPermission.class);
        deleteEntity(Permission.class);
        deleteEntity(User.class);

        // Delete all explorer nodes.
        explorerNodeService.deleteAllNodes();

        // deleteTable("sys_user_role");
        // deleteTable("sys_user_group");
        // deleteTable("sys_user");
        // deleteTable("sys_group");

        // Delete the contents of all volumes.
        final List<Volume> volumes = volumeService.find(new FindVolumeCriteria());
        for (final Volume volume : volumes) {
            // The parent will also pick up the index shard (as well as the
            // store)
            FileSystemUtil.deleteContents(FileSystemUtil.createFileTypeRoot(volume).getParent());
        }

        // These are static
        deleteEntity(JobNode.class);
        deleteEntity(Job.class);

        deleteEntity(Volume.class);
        deleteEntity(VolumeState.class);
        deleteEntity(Node.class);
        deleteEntity(Rack.class);

        databaseCommonTestControlTransactionHelper.truncateTable("doc");

        databaseCommonTestControlTransactionHelper.clearContext();
        stroomCacheManager.clear();

        final Map<String, Clearable> clearableBeanMap = applicationContext.getBeansOfType(Clearable.class, false,
                false);
        for (final Clearable clearable : clearableBeanMap.values()) {
            clearable.clear();
        }
        LOGGER.info("test environment teardown completed in {}", Duration.between(startTime, Instant.now()));
    }

    public void createStreamAttributeKeys() {
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
