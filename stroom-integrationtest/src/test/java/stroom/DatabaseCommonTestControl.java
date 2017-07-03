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

package stroom;

import net.sf.ehcache.CacheManager;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.Query;
import stroom.dictionary.shared.Dictionary;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Folder;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.Res;
import stroom.feed.shared.Feed;
import stroom.importexport.server.ImportExportSerializer;
import stroom.index.server.IndexShardManager;
import stroom.index.server.IndexShardWriterImpl;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.lifecycle.LifecycleServiceImpl;
import stroom.node.server.NodeConfig;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.Volume;
import stroom.node.shared.VolumeService;
import stroom.node.shared.VolumeState;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.script.shared.Script;
import stroom.security.server.DocumentPermission;
import stroom.security.server.Permission;
import stroom.security.server.UserGroupUser;
import stroom.security.server.User;
import stroom.statistics.shared.StatisticStore;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeKeyService;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamTask;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.visualisation.shared.Visualisation;
import stroom.xmlschema.shared.XMLSchema;

import javax.annotation.Resource;
import java.io.File;
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
    @Resource
    private VolumeService volumeService;
    @Resource
    private IndexShardService indexShardService;
    @Resource
    private ImportExportSerializer importExportSerializer;
    @Resource
    private StreamAttributeKeyService streamAttributeKeyService;
    @Resource
    private IndexShardManager indexShardManager;
    @Resource
    private DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper;
    @Resource
    private NodeConfig nodeConfig;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private LifecycleServiceImpl lifecycleServiceImpl;
    @Resource
    private CacheManager cacheManager;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setup() {
        nodeConfig.setup();
        createStreamAttributeKeys();

        // Ensure we can create tasks.
        streamTaskCreator.startup();
    }

    /**
     * Clear down the database.
     */
    @Override
    public void teardown() {
        deleteEntity(StreamTask.class);

        deleteEntity(StreamVolume.class);
        deleteEntity(StreamAttributeValue.class);
        deleteEntity(Stream.class);

        deleteEntity(Query.class);
        deleteEntity(Dashboard.class);
        deleteEntity(Visualisation.class);
        deleteEntity(Script.class);
        deleteEntity(Res.class);
        deleteEntity(Dictionary.class);
        deleteEntity(StatisticStore.class);

        // Make sure we are no longer creating tasks.
        streamTaskCreator.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardManager.shutdown();

        for (final IndexShard indexShard : indexShardService.find(new FindIndexShardCriteria())) {
            final IndexShardWriterImpl writer = new IndexShardWriterImpl(indexShardService, null, indexShard.getIndex(),
                    indexShard);
            writer.delete();
            writer.deleteFromDisk();
        }
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

        // Delete folders last as they are the parent for many other entities.
        deleteEntity(Folder.class);

        // deleteTable("sys_user_role");
        // deleteTable("sys_user_group");
        // deleteTable("sys_user");
        // deleteTable("sys_group");

        // Delete the contents of all volumes.
        final List<Volume> volumes = volumeService.find(new FindVolumeCriteria());
        for (final Volume volume : volumes) {
            // The parent will also pick up the index shard (as well as the
            // store)
            FileSystemUtil.deleteContents(FileSystemUtil.createFileTypeRoot(volume).getParentFile());
        }

        // These are static
        deleteEntity(JobNode.class);
        deleteEntity(Job.class);

        deleteEntity(Volume.class);
        deleteEntity(VolumeState.class);
        deleteEntity(Node.class);
        deleteEntity(Rack.class);

        databaseCommonTestControlTransactionHelper.clearContext();
        cacheManager.clearAll();

        final Map<String, Clearable> clearableBeanMap = applicationContext.getBeansOfType(Clearable.class, false,
                false);
        for (final Clearable clearable : clearableBeanMap.values()) {
            clearable.clear();
        }
    }

    public void createStreamAttributeKeys() {
        final BaseResultList<StreamAttributeKey> list = streamAttributeKeyService
                .find(new FindStreamAttributeKeyCriteria());
        final HashSet<String> existingItems = new HashSet<String>();
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
        // Import schemas if we haven't done so already.
        final int schemaCount = countEntity(XMLSchema.class);
        if (schemaCount == 0) {
            // Import the schemas.
            final File xsdDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "samples/config/XML Schemas");
            importExportSerializer.read(xsdDir.toPath(), null, ImportMode.IGNORE_CONFIRMATION);
        }
    }
}
