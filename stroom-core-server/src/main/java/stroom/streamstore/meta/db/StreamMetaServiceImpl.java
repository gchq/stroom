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

package stroom.streamstore.meta.db;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.entity.StroomDatabaseInfo;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
import stroom.entity.util.EntityServiceLogUtil;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;
import stroom.entity.util.SqlUtil;
import stroom.feed.FeedDocCache;
import stroom.feed.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.EffectiveMetaDataCriteria;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamstore.ExpressionToFindCriteria.Context;
import stroom.streamstore.FeedEntityService;
import stroom.streamstore.FindFeedCriteria;
import stroom.streamstore.OldFindStreamCriteria;
import stroom.streamstore.StreamTypeEntityService;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.meta.StreamMetaService;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeCondition;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeFieldUse;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamPermissionException;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamStatusId;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * A file system stream store.
 * </p>
 * <p>
 * <p>
 * Stores streams in the stream store indexed by some meta data.
 * </p>
 */
@Singleton
// @Transactional
public class StreamMetaServiceImpl implements StreamEntityService, StreamMetaService {
    private static final String MYSQL_INDEX_STRM_CRT_MS_IDX = "STRM_CRT_MS_IDX";
    private static final String MYSQL_INDEX_STRM_FK_FD_ID_CRT_MS_IDX = "STRM_FK_FD_ID_CRT_MS_IDX";
    private static final String MYSQL_INDEX_STRM_EFFECT_MS_IDX = "STRM_EFFECT_MS_IDX";
    private static final String MYSQL_INDEX_STRM_PARNT_STRM_ID_IDX = "STRM_PARNT_STRM_ID_IDX";
    private static final String MYSQL_INDEX_STRM_FK_STRM_PROC_ID_CRT_MS_IDX = "STRM_FK_STRM_PROC_ID_CRT_MS_IDX";
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamMetaServiceImpl.class);
    private static final Set<String> SOURCE_FETCH_SET;
    private static final FieldMap FIELD_MAP = new FieldMap()
            .add(OldFindStreamCriteria.FIELD_ID, BaseEntity.ID, "id")
            .add(StreamDataSource.CREATE_TIME, StreamEntity.CREATE_MS, "createMs");

    static {
        final Set<String> set = new HashSet<>();
        set.add(FeedEntity.ENTITY_TYPE);
        set.add(StreamTypeEntity.ENTITY_TYPE);
        SOURCE_FETCH_SET = set;
    }

    private final StroomEntityManager entityManager;
    private final StroomDatabaseInfo stroomDatabaseInfo;
    private final FeedEntityService feedService;
    private final FeedStore feedStore;
    private final FeedDocCache feedDocCache;
    private final StreamTypeEntityService streamTypeService;
    private final StreamProcessorService streamProcessorService;
    private final ExpressionToFindCriteria expressionToFindCriteria;
    private final SecurityContext securityContext;
    private final Security security;

    @Inject
    StreamMetaServiceImpl(final StroomEntityManager entityManager,
                          final StroomDatabaseInfo stroomDatabaseInfo,
                          final FeedEntityService feedService,
                          @Named("cachedStreamTypeService") final StreamTypeEntityService streamTypeService,
                          @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                          final FeedStore feedStore,
                          final FeedDocCache feedDocCache,
                          final ExpressionToFindCriteria expressionToFindCriteria,
                          final SecurityContext securityContext,
                          final Security security) {
        this.entityManager = entityManager;
        this.stroomDatabaseInfo = stroomDatabaseInfo;
        this.feedService = feedService;
        this.feedStore = feedStore;
        this.streamTypeService = streamTypeService;
        this.streamProcessorService = streamProcessorService;
        this.feedDocCache = feedDocCache;
        this.expressionToFindCriteria = expressionToFindCriteria;
        this.securityContext = securityContext;
        this.security = security;
    }

//    public static void main(final String[] args) {
//        final int MAX = 200;
//        final OldFindStreamCriteria outerCriteria = new OldFindStreamCriteria();
//        outerCriteria.obtainPageRequest().setLength(1000);
//        outerCriteria.setSort(StreamDataSource.CREATE_TIME, Direction.DESCENDING, false);
//        final FileSystemStreamStoreImpl fileSystemStreamStore = new FileSystemStreamStoreImpl(null, null, null, null, null,
//                null, null, null, null, null, null, null);
//        final SqlBuilder sql = new SqlBuilder();
//
//        sql.append("SELECT U.* FROM ( ");
//        boolean doneOne = false;
//        for (int i = 0; i < MAX; i++) {
//            if (doneOne) {
//                sql.append(" UNION ");
//            }
//            sql.append("( ");
//            final OldFindStreamCriteria findStreamCriteria = new OldFindStreamCriteria();
//            findStreamCriteria.obtainFeeds().obtainInclude().add((long) i);
//            findStreamCriteria.obtainPageRequest().setLength(1000);
//            findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS.getId());
//            findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_REFERENCE.getId());
//            findStreamCriteria.setSort(StreamDataSource.CREATE_TIME, Direction.DESCENDING, false);
//            fileSystemStreamStore.rawBuildSQL(findStreamCriteria, sql);
//            sql.append(") \n");
//            doneOne = true;
//        }
//        sql.append(" ) AS U ");
//        sql.appendOrderBy(FIELD_MAP.getSqlFieldMap(), outerCriteria, "U");
//        sql.applyRestrictionCriteria(outerCriteria);
//
//        System.out.println(sql.toString());
//
//        System.out.println("=========================");
//
//        final SqlBuilder sql2 = new SqlBuilder();
//        final OldFindStreamCriteria findStreamCriteria = new OldFindStreamCriteria();
//        for (int i = 0; i < MAX; i++) {
//            findStreamCriteria.obtainFeeds().obtainInclude().add((long) i);
//        }
//        findStreamCriteria.obtainPageRequest().setLength(1000);
//        findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS.getId());
//        findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_REFERENCE.getId());
//        findStreamCriteria.setSort(StreamDataSource.CREATE_TIME, Direction.DESCENDING, false);
//        fileSystemStreamStore.rawBuildSQL(findStreamCriteria, sql2);
//        System.out.println(sql2.toString());
//    }

    @Override
    public Stream createStream(final StreamProperties streamProperties) {
        final StreamTypeEntity streamType = streamTypeService.getOrCreate(streamProperties.getStreamTypeName());
        final FeedEntity feed = feedService.getOrCreate(streamProperties.getFeedName());

        final StreamEntity stream = new StreamEntity();

        if (streamProperties.getParent() != null) {
            stream.setParentStreamId(streamProperties.getParent().getId());
        }

        stream.setFeed(feed);
        stream.setStreamType(streamType);
        stream.setStreamProcessor(streamProperties.getStreamProcessor());
        if (streamProperties.getStreamTask() != null) {
            stream.setStreamTaskId(streamProperties.getStreamTask().getId());
        }
        stream.setCreateMs(streamProperties.getCreateMs());
        stream.setEffectiveMs(streamProperties.getEffectiveMs());
        stream.setStatusMs(streamProperties.getStatusMs());
        stream.setPstatus(StreamStatusId.LOCKED);

        return save(stream);

//        // Flush to the DB
//        entityManager.flush();
//
//        return s;
    }

    @Override
    public boolean canReadStream(final long id) {
        try {
            return getStream(id) != null;
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public Stream getStream(final long id) {
        return load(id, null, false);
    }

    @Override
    public Stream getStream(final long id, final boolean anyStatus) {
        return load(id, null, anyStatus);
    }

    @Override
    public Stream updateStatus(final long id, final StreamStatus streamStatus) {
        // TODO : @66 update via SQL directly
        StreamEntity streamEntity = load(id, null, true);
        streamEntity.setPstatus(StreamStatusId.getPrimitiveValue(streamStatus));
        return save(streamEntity);
//        // Flush to the DB
//        entityManager.flush();
//        return streamEntity;
    }

//    /**
//     * Load a stream by id.
//     *
//     * @param id The stream id to load a stream for.
//     * @return The loaded stream if it exists (has not been physically deleted)
//     * and is not logically deleted or locked, null otherwise.
//     */
//    @Override
//    public StreamEntity load(final long id) {
//        return load(id, null, false);
//    }
//
//    /**
//     * Load a stream by id.
//     *
//     * @param id        The stream id to load a stream for.
//     * @param anyStatus Used to specify if this method will return streams that are
//     *                  logically deleted or locked. If false only unlocked streams
//     *                  will be returned, null otherwise.
//     * @return The loaded stream if it exists (has not been physically deleted)
//     * else null. Also returns null if one exists but is logically
//     * deleted or locked unless <code>anyStatus</code> is true.
//     */
//    @Override
//    public StreamEntity load(final long id, final boolean anyStatus) {
//        return load(id, null, anyStatus);
//    }

    // @Override
    @SuppressWarnings("unchecked")
    private StreamEntity load(final long id, final Set<String> fetchSet, final boolean anyStatus) {
        StreamEntity entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(StreamEntity.class.getName());
        sql.append(" AS e");

        // Always fetch feed when loading an individual stream.
        sql.append(" INNER JOIN FETCH e.feed");

        if (fetchSet != null) {
//            if (fetchSet.contains(Feed.DOCUMENT_TYPE)) {
//                sql.append(" INNER JOIN FETCH e.feed");
//            }
            if (fetchSet.contains(StreamTypeEntity.ENTITY_TYPE)) {
                sql.append(" INNER JOIN FETCH e.streamType");
            }
            if (fetchSet.contains(StreamProcessor.ENTITY_TYPE)) {
                sql.append(" INNER JOIN FETCH e.streamProcessor");
            }
        }

        sql.append(" WHERE e.id = ");
        sql.arg(id);

        final List<StreamEntity> resultList = entityManager.executeQueryResultList(sql);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
            if (!anyStatus) {
                switch (entity.getStatus()) {
                    case LOCKED:
                        entity = null;
                        break;
                    case DELETED:
                        entity = null;
                        break;
                    case UNLOCKED:
                }
            }
        }

        // Ensure user has permission to read this stream.
        if (entity != null) {
            if (!securityContext.hasDocumentPermission(FeedDoc.DOCUMENT_TYPE, getFeedUuid(entity.getFeedName()), DocumentPermissionNames.READ)) {
                throw new StreamPermissionException(securityContext.getUserId(), "You do not have permission to read stream with id=" + id);
            }
        }

        return entity;
    }

    private StreamEntity save(final StreamEntity stream) {
        return entityManager.saveEntity(stream);
    }

//    /**
//     * <p>
//     * Open a existing stream source.
//     * </p>
//     *
//     * @param streamId the id of the stream to open.
//     * @return The stream source if the stream can be found.
//     * @throws StreamException in case of a IO error or stream volume not visible or non
//     *                         existent.
//     */
//    @Override
//    public StreamSource openStreamSource(final long streamId) throws StreamException {
//        return openStreamSource(streamId, false);
//    }
//
//    /**
//     * <p>
//     * Open a existing stream source.
//     * </p>
//     *
//     * @param streamId  The stream id to open a stream source for.
//     * @param anyStatus Used to specify if this method will return stream sources that
//     *                  are logically deleted or locked. If false only unlocked stream
//     *                  sources will be returned, null otherwise.
//     * @return The loaded stream source if it exists (has not been physically
//     * deleted) else null. Also returns null if one exists but is
//     * logically deleted or locked unless <code>anyStatus</code> is
//     * true.
//     * @throws StreamException Could be thrown if no volume
//     */
//    @Override
//    public StreamSource openStreamSource(final long streamId, final boolean anyStatus) throws StreamException {
//        StreamSource streamSource = null;
//
//        final StreamEntity stream = load(streamId, SOURCE_FETCH_SET, anyStatus);
//        if (stream != null) {
//            LOGGER.debug("openStreamSource() {}", stream.getId());
//
//            final Set<StreamVolume> volumeSet = findStreamVolume(stream.getId());
//            if (volumeSet.isEmpty()) {
//                final String message = "Unable to find any volume for " + stream;
//                LOGGER.warn(message);
//                throw new StreamException(message);
//            }
//            final StreamVolume volumeToUse = StreamVolumeUtil.pickBestVolume(volumeSet, nodeCache.getDefaultNode());
//            if (volumeToUse == null) {
//                final String message = "Unable to access any volume for " + stream
//                        + " perhaps the stream is on a private volume";
//                LOGGER.warn(message);
//                throw new StreamException(message);
//            }
//            streamSource = FileSystemStreamSource.create(stream, volumeToUse, stream.getStreamTypeName());
//        }
//
//        return streamSource;
//    }
//
//    /**
//     * Utility to lock a stream.
//     */
//    private Set<StreamVolume> obtainLockForUpdate(final StreamEntity stream) throws StreamException {
//        LOGGER.debug("obtainLock() Entry " + stream);
//        Set<StreamVolume> lock;
//        try {
//            if (stream.isPersistent()) {
//                // Lock the object
//                lock = findStreamVolume(stream.getId());
//                if (lock.isEmpty()) {
//                    throw new StreamException("Not all volumes are unlocked");
//                }
//                final StreamEntity dbStream = lock.iterator().next().getStream();
//                dbStream.updateStatus(StreamStatus.LOCKED);
//
//                entityManager.saveEntity(dbStream);
//
//            } else {
//                final Set<Volume> volumeSet = volumeService.getStreamVolumeSet(nodeCache.getDefaultNode());
//                if (volumeSet.isEmpty()) {
//                    throw new StreamException("Failed to get lock as no writeable volumes");
//                }
//
//                // First time call (no file yet exists)
//                stream.updateStatus(StreamStatus.LOCKED);
//                entityManager.saveEntity(stream);
//
//                // Flush to the DB
//                entityManager.flush();
//
//                lock = new HashSet<>();
//
//                for (final Volume volume : volumeSet) {
//                    StreamVolume streamVolume = new StreamVolume();
//                    streamVolume.setStream(stream);
//                    streamVolume.setVolume(volume);
//                    streamVolume = entityManager.saveEntity(streamVolume);
//
//                    lock.add(streamVolume);
//                }
//            }
//            // Flush to the DB
//            entityManager.flush();
//            LOGGER.debug("obtainLock() Exit " + lock);
//            return lock;
//        } catch (final RuntimeException e) {
//            LOGGER.warn("Failed to get lock on " + stream, e);
//            resolveException(e);
//            return null;
//        }
//    }
//
//    private void resolveException(final RuntimeException e) {
//        throw e;
////        if (e instanceof RuntimeException) {
////            throw (RuntimeException) ex;
////        }
////        throw new StreamException(ex);
//    }
//
//    private StreamEntity unLock(final StreamEntity stream, final MetaMap metaMap, final boolean append) {
//        if (StreamStatus.UNLOCKED.equals(stream.getStatus())) {
//            throw new IllegalStateException("Attempt to unlock a stream that is already unlocked");
//        }
//
//        // Write the child meta data
//        if (!metaMap.isEmpty()) {
//            try {
//                streamAttributeValueFlush.persitAttributes(stream, append, metaMap);
//            } catch (final RuntimeException e) {
//                LOGGER.error("unLock() - Failed to persist attributes in new transaction... will ignore");
//            }
//        }
//
//        LOGGER.debug("unlock() " + stream);
//        stream.updateStatus(StreamStatus.UNLOCKED);
//        // Attach object (may throw a lock exception)
//        final StreamEntity lock = entityManager.saveEntity(stream);
//
//        // Flush to the DB
//        entityManager.flush();
//        return lock;
//    }
//
//    @Override
//    public StreamTarget openStreamTarget(final StreamProperties streamProperties) {
//        final StreamEntity stream = createStream(streamProperties);
//        return openStreamTarget(stream, false);
//    }
//
//    @Override
//    public StreamTarget openExistingStreamTarget(final long streamId) throws StreamException {
//        final StreamEntity stream = load(streamId);
//        return openStreamTarget(stream, true);
//    }
//
//    //    @Secured(feature = Stream.DOCUMENT_TYPE, permission = DocumentPermissionNames.UPDATE)
//    @SuppressWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
//    private StreamTarget openStreamTarget(final StreamEntity stream, final boolean append) {
//        return entityManagerSupport.transactionResult(em -> {
//            LOGGER.debug("openStreamTarget() " + stream);
//
//            if (!append && stream.isPersistent()) {
//                throw new StreamException("Trying to create a stream target that already exists");
//            } else if (append && !stream.isPersistent()) {
//                throw new StreamException("Trying to append to a stream target that doesn't exist");
//            }
//
//            final Set<StreamVolume> lock = obtainLockForUpdate(stream);
//            if (lock != null) {
//                final StreamEntity dbStream = lock.iterator().next().getStream();
//                final String streamType = dbStream.getStreamTypeName();
//                final FileSystemStreamTarget target = FileSystemStreamTarget.create(dbStream, lock,
//                        streamType, append);
//
//                // TODO - one day allow appending to the stream (not just add child
//                // streams)
//                if (!append) {
//                    // Force Creation of the files
//                    target.getOutputStream();
//                }
//
//                syncAttributes(stream, dbStream, target);
//
//                return target;
//            }
//            LOGGER.error("openStreamTarget() Failed to obtain lock");
//            return null;
//        });
//    }
//
//    private void syncAttributes(final StreamEntity stream, final StreamEntity dbStream, final FileSystemStreamTarget target) {
//        updateAttribute(target, StreamAttributeConstants.STREAM_ID, String.valueOf(dbStream.getId()));
//
//        if (dbStream.getParentStreamId() != null) {
//            updateAttribute(target, StreamAttributeConstants.PARENT_STREAM_ID,
//                    String.valueOf(dbStream.getParentStreamId()));
//        }
//
//        updateAttribute(target, StreamAttributeConstants.FEED, dbStream.getFeedName());
//        updateAttribute(target, StreamAttributeConstants.STREAM_TYPE, dbStream.getStreamTypeName());
//        updateAttribute(target, StreamAttributeConstants.CREATE_TIME, DateUtil.createNormalDateTimeString(stream.getCreateMs()));
//        if (stream.getEffectiveMs() != null) {
//            updateAttribute(target, StreamAttributeConstants.EFFECTIVE_TIME, DateUtil.createNormalDateTimeString(stream.getEffectiveMs()));
//        }
//    }
//
//    private void updateAttribute(final StreamTarget target, final String key, final String value) {
//        if (!target.getAttributeMap().containsKey(key)) {
//            target.getAttributeMap().put(key, value);
//        }
//    }

    // /**
    // * Overridden.
    // *
    // * @see stroom.streamstore.api.StreamStore#deleteLocks()
    // */
    // @Override
    // public void deleteLocks() {
    // Stream stream = new Stream();
    // meta.setStatus(StreamStatus.LOCKED);
    // deleteStream(meta);
    // }

    @Override
    public Long deleteStream(final long streamId) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDeleteStream(streamId, true));
    }

    @Override
    public Long deleteStream(final long streamId, final boolean lockCheck) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDeleteStream(streamId, lockCheck));
    }

    private Long doLogicalDeleteStream(final long streamId, final boolean lockCheck) {
        final StreamEntity loaded = load(streamId, SOURCE_FETCH_SET, true);

//        if (stream == null || !stream.isPersistent()) {
//            throw new IllegalArgumentException(
//                    "deleteStream does not support delete by example.  You must supply a real stream");
//        }
//
//        if (stream.getFeed() == null || !Hibernate.isInitialized(stream.getFeed())) {
//            throw new IllegalArgumentException(
//                    "You can only delete streams with a loaded feed");
//        }

        // Don't bother to try and set the status of deleted streams to deleted.
        if (StreamStatus.DELETED.equals(loaded.getStatus())) {
            return 0L;
        }

        // Don't delete if the stream is not unlocked and we are checking for unlocked.
        if (lockCheck && !StreamStatus.UNLOCKED.equals(loaded.getStatus())) {
            return 0L;
        }

        // Ensure the user has permission to delete this stream.
        if (!securityContext.hasDocumentPermission(FeedDoc.DOCUMENT_TYPE, getFeedUuid(loaded.getFeedName()), DocumentPermissionNames.DELETE)) {
            throw new StreamPermissionException(securityContext.getUserId(), "You do not have permission to delete stream with id=" + loaded.getId());
        }

        loaded.updateStatus(StreamStatus.DELETED);
        save(loaded);

        return 1L;

//        final OldFindStreamCriteria findStreamCriteria = new OldFindStreamCriteria();
//        findStreamCriteria.obtainStreamIdSet().add(stream.getId());
//        if (lockCheck) {
//            findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
//        }
//        return fileSystemStreamStoreTransactionHelper.updateStreamStatus(findStreamCriteria, StreamStatus.DELETED,
//                System.currentTimeMillis());
    }

//    @Override
//    public Long deleteStreamTarget(final StreamTarget target) {
//        // Make sure the stream is closed.
//        try {
//            target.close();
//        } catch (final IOException e) {
//            LOGGER.error("Unable to delete stream target!", e.getMessage(), e);
//        }
//
//        // Make sure the stream data is deleted.
//        // Attach object (may throw a lock exception)
//        final StreamEntity db = entityManager.saveEntity(target.getStream());
//        return doLogicalDeleteStream(db.getId(), false);
//    }
//
//    @Override
//    public void closeStreamSource(final StreamSource streamSource) {
//        try {
//            // Close the stream source.
//            streamSource.close();
//        } catch (final IOException e) {
//            LOGGER.error("Unable to close stream source!", e.getMessage(), e);
//        }
//    }
//
//    @Override
//    public void closeStreamTarget(final StreamTarget streamTarget) {
//        entityManagerSupport.transaction(em -> {
//            // If we get error on closing the stream we must return it to the caller
//            IOException streamCloseException = null;
//
//            try {
//                // Close the stream target.
//                streamTarget.close();
//            } catch (final IOException e) {
//                LOGGER.error("closeStreamTarget() - Error on closing stream {}", streamTarget, e);
//                streamCloseException = e;
//            }
//
//            updateAttribute(streamTarget, StreamAttributeConstants.STREAM_SIZE,
//                    String.valueOf(((FileSystemStreamTarget) streamTarget).getStreamSize()));
//
//            updateAttribute(streamTarget, StreamAttributeConstants.FILE_SIZE,
//                    String.valueOf(((FileSystemStreamTarget) streamTarget).getTotalFileSize()));
//
//            try {
//                boolean doneManifest = false;
//
//                // Are we appending?
//                if (streamTarget.isAppend()) {
//                    final Set<Path> childFile = FileSystemStreamTypeUtil.createChildStreamPath(
//                            ((FileSystemStreamTarget) streamTarget).getFiles(false), StreamTypeNames.MANIFEST);
//
//                    // Does the manifest exist ... overwrite it
//                    if (FileSystemUtil.isAllFile(childFile)) {
//                        streamTarget.getAttributeMap()
//                                .write(FileSystemStreamTypeUtil.getOutputStream(StreamTypeNames.MANIFEST, childFile), true);
//                        doneManifest = true;
//                    }
//                }
//
//                if (!doneManifest) {
//                    // No manifest done yet ... output one if the parent dir's exist
//                    if (FileSystemUtil.isAllParentDirectoryExist(((FileSystemStreamTarget) streamTarget).getFiles(false))) {
//                        streamTarget.getAttributeMap()
//                                .write(streamTarget.addChildStream(StreamTypeNames.MANIFEST).getOutputStream(), true);
//                    } else {
//                        LOGGER.warn("closeStreamTarget() - Closing target file with no directory present");
//                    }
//
//                }
//            } catch (final IOException e) {
//                LOGGER.error("closeStreamTarget() - Error on writing Manifest {}", streamTarget, e);
//            }
//
//            if (streamCloseException == null) {
//                // Unlock will update the meta data so set it back on the stream
//                // target so the client has the up to date copy
//                ((FileSystemStreamTarget) streamTarget).setMetaData(
//                        unLock(streamTarget.getStream(), streamTarget.getAttributeMap(), streamTarget.isAppend()));
//            } else {
//                throw new UncheckedIOException(streamCloseException);
//            }
//        });
//    }
//
//    @Override
//    // @Transactional
//    public long getLockCount() {
//        final HqlBuilder sql = new HqlBuilder();
//        sql.append("SELECT count(*) FROM ");
//        sql.append(StreamEntity.class.getName());
//        sql.append(" S WHERE S.pstatus = ");
//        sql.arg(StreamStatusId.LOCKED);
//
//        return entityManager.executeQueryLongResult(sql);
//    }
//
//    /**
//     * Return the meta data volumes for a stream id.
//     */
//    @Override
//    @SuppressWarnings("unchecked")
//    // @Transactional
//    public Set<StreamVolume> findStreamVolume(final Long metaDataId) {
//        final HqlBuilder sql = new HqlBuilder();
//        sql.append("SELECT sv FROM ");
//        sql.append(StreamVolume.class.getName());
//        sql.append(" sv WHERE sv.stream.id = ");
//        sql.arg(metaDataId);
//        return new HashSet<>(entityManager.executeQueryResultList(sql));
//    }

    @Override
    public BaseResultList<Stream> find(final FindStreamCriteria criteria) {
        final OldFindStreamCriteria oldFindStreamCriteria = expressionToFindCriteria.convert(criteria);
        return find(oldFindStreamCriteria);
    }

    @Override
    // @Transactional
    public BaseResultList<Stream> find(final OldFindStreamCriteria originalCriteria) {
        final boolean relationshipQuery = originalCriteria.getFetchSet().contains(StreamEntity.ENTITY_TYPE);
        final PageRequest pageRequest = originalCriteria.getPageRequest();
        if (relationshipQuery) {
            originalCriteria.setPageRequest(null);
        }

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final OldFindStreamCriteria queryCriteria = new OldFindStreamCriteria();
        queryCriteria.copyFrom(originalCriteria);

        // Ensure that included feeds are restricted to ones the user can read.
        restrictCriteriaByFeedPermissions(queryCriteria, DocumentPermissionNames.READ);

        // If the current user is not an admin and no feeds are readable that have been requested then return an empty array.
        if (!securityContext.isAdmin() && queryCriteria.obtainFeeds().obtainInclude().size() == 0) {
            final List<Stream> rtnList = new ArrayList<>();
            return BaseResultList.createCriterialBasedList(rtnList, originalCriteria);
        }

        final SqlBuilder sql = new SqlBuilder();
        buildRawSelectSQL(queryCriteria, sql);
        List<Stream> rtnList = entityManager.executeNativeQueryResultList(sql, StreamEntity.class);

        // Bug where union queries return back more results than we expected
        if (queryCriteria.obtainPageRequest().getLength() != null
                && rtnList.size() > queryCriteria.obtainPageRequest().getLength() + 1) {
            final ArrayList<Stream> limitedList = new ArrayList<>();
            for (int i = 0; i <= queryCriteria.obtainPageRequest().getLength(); i++) {
                limitedList.add(rtnList.get(i));
            }
            rtnList = limitedList;
        }

        EntityServiceLogUtil.logQuery(LOGGER, "find()", logExecutionTime, rtnList, sql);

        // Only return back children or parents?
        if (originalCriteria.getFetchSet().contains(StreamEntity.ENTITY_TYPE)) {
            final List<Stream> workingList = rtnList;
            rtnList = new ArrayList<>();

            for (final Stream stream : workingList) {
                Stream parent = stream;
                Stream lastParent = parent;

                // Walk up to the root of the tree
                while (parent.getParentStreamId() != null && (parent = findParent(parent)) != null) {
                    lastParent = parent;
                }

                // Add the match
                rtnList.add(lastParent);

                // Add the children
                List<Stream> children = findChildren(originalCriteria, Collections.singletonList(lastParent));
                while (children.size() > 0) {
                    rtnList.addAll(children);
                    children = findChildren(originalCriteria, children);
                }
            }
        }

        for (final Stream stream : rtnList) {
            final StreamEntity streamEntity = (StreamEntity) stream;

            if (originalCriteria.getFetchSet().contains(StreamProcessor.ENTITY_TYPE)) {
                final StreamProcessor streamProcessor = streamProcessorService.load(streamEntity.getStreamProcessor());
                streamEntity.setStreamProcessor(streamProcessor);
                if (streamProcessor != null) {
                    streamEntity.getStreamProcessor().setPipelineUuid(streamProcessor.getPipelineUuid());
                    streamEntity.getStreamProcessor().setPipelineName(streamProcessor.getPipelineName());
                }
            }
//            if (originalCriteria.getFetchSet().contains(StreamTypeEntity.ENTITY_TYPE)) {
//                final StreamTypeEntity streamType = streamEntity.getStreamType();
//                streamEntity.setStreamType(streamType);
//            }
        }

        if (relationshipQuery) {
            final long maxSize = rtnList.size();
            if (pageRequest != null && pageRequest.getOffset() != null) {
                // Move by an offset?
                if (pageRequest.getOffset() > 0) {
                    rtnList = rtnList.subList(pageRequest.getOffset().intValue(), rtnList.size());
                }
            }
            if (pageRequest != null && pageRequest.getLength() != null) {
                if (rtnList.size() > pageRequest.getLength()) {
                    rtnList = rtnList.subList(0, pageRequest.getLength() + 1);
                }
            }
            originalCriteria.setPageRequest(pageRequest);
            return BaseResultList.createCriterialBasedList(rtnList, originalCriteria, maxSize);
        } else {
            return BaseResultList.createCriterialBasedList(rtnList, originalCriteria);
        }
    }

    private void restrictCriteriaByFeedPermissions(final OldFindStreamCriteria findStreamCriteria, final String requiredPermission) {
        // We only need to restrict data by feed for non admins.
        if (!securityContext.isAdmin()) {
            // If the user is filtering by feed then make sure they can read all of the feeds that they are filtering by.
            final EntityIdSet<FeedEntity> feeds = findStreamCriteria.obtainFeeds().obtainInclude();

            // Ensure a user cannot match all feeds.
            feeds.setMatchAll(Boolean.FALSE);
            final List<FeedEntity> restrictedFeeds = getRestrictedFeeds(requiredPermission);

            if (feeds.size() > 0) {
                final Set<Long> restrictedFeedIds =
                        restrictedFeeds.stream().map(FeedEntity::getId).collect(Collectors.toSet());

                // Retain only the feeds that the user has the required permission on.
                feeds.getSet().retainAll(restrictedFeedIds);

            } else {
                feeds.addAllEntities(restrictedFeeds);
            }
        }
    }

    private List<FeedEntity> getRestrictedFeeds(final String requiredPermission) {
        final List<DocRef> docRefs = feedStore.list();
        final List<FeedEntity> filtered = new ArrayList<>();
        for (final DocRef docRef : docRefs) {
            if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), requiredPermission)) {
                filtered.add(FeedEntity.createStub(feedService.getId(docRef.getName())));
            }
        }
        return filtered;
    }

    private void buildRawSelectSQL(final OldFindStreamCriteria queryCriteria, final SqlBuilder sql) {
        final PageRequest pageRequest = queryCriteria.obtainPageRequest();

        // If we are doing more than one feed query (but less than 20) query
        // using union
        if (queryCriteria.getFeeds() != null
                && queryCriteria.getFeeds().getExclude() == null
                && queryCriteria.getFeeds().getInclude() != null
                && queryCriteria.getFeeds().getInclude().size() > 1
                && queryCriteria.getFeeds().getInclude().size() < 20
                && (pageRequest.getOffset() != null
                && pageRequest.getOffset() <= 1000)
                && (pageRequest.getLength() != null
                && pageRequest.getLength() <= 1000)) {
            sql.append("SELECT U.* FROM (");
            boolean doneOne = false;
            for (final Long feedId : queryCriteria.getFeeds().getInclude()) {
                if (doneOne) {
                    sql.append(" UNION ALL");
                }
                sql.append(" (");
                final OldFindStreamCriteria unionCriteria = new OldFindStreamCriteria();
                unionCriteria.copyFrom(queryCriteria);
                unionCriteria.obtainFeeds().clear();
                unionCriteria.obtainFeeds().obtainInclude().add(feedId);
                unionCriteria.obtainPageRequest().setOffset(0L);
                unionCriteria.obtainPageRequest().setLength((int) (pageRequest.getOffset() + pageRequest.getLength()));
                rawBuildSQL(unionCriteria, sql);
                sql.append(")");
                doneOne = true;
            }
            sql.append(") AS U");
            sql.appendOrderBy(FIELD_MAP.getSqlFieldMap(), queryCriteria, "U");
            sql.applyRestrictionCriteria(queryCriteria);
        } else {
            rawBuildSQL(queryCriteria, sql);
        }
    }

    @SuppressWarnings("incomplete-switch")
    private void rawBuildSQL(final OldFindStreamCriteria criteria, final SqlBuilder sql) {
        sql.append("SELECT S.*");
        sql.append(" FROM ");
        sql.append(StreamEntity.TABLE_NAME);
        sql.append(" S");

        appendJoin(criteria, sql);

        sql.append(" WHERE 1=1");

        appendStreamCriteria(criteria, sql);

        // Append order by criteria.
        sql.appendOrderBy(FIELD_MAP.getSqlFieldMap(), criteria, "S");
        sql.applyRestrictionCriteria(criteria);
    }

    private void appendJoin(final OldFindStreamCriteria criteria, final SqlBuilder sql) {
        String indexToUse = null;

        // Here we try and better second guess a index to use for MYSQL
        boolean chooseIndex = true;
        if (!stroomDatabaseInfo.isMysql()) {
            chooseIndex = false;
        }

        // Any Key by stream id MySQL will pick the stream id index
        if (criteria.getStreamIdSet() != null && criteria.getStreamIdSet().isConstrained()) {
            chooseIndex = false;
        }
        if (criteria.getStreamIdRange() != null && criteria.getStreamIdRange().isConstrained()) {
            chooseIndex = false;
        }
        if (criteria.getParentStreamIdSet() != null && criteria.getParentStreamIdSet().isConstrained()) {
            chooseIndex = false;
        }

        if (chooseIndex && criteria.getPipelineSet() != null && criteria.getPipelineSet().size() == 1) {
            chooseIndex = false;
            indexToUse = MYSQL_INDEX_STRM_FK_STRM_PROC_ID_CRT_MS_IDX;
        }

        if (chooseIndex && criteria.getFeeds() != null && criteria.getFeeds().getInclude() != null
                && criteria.getFeeds().getInclude().getSet().size() == 1) {
            chooseIndex = false;
            indexToUse = MYSQL_INDEX_STRM_FK_FD_ID_CRT_MS_IDX;
        }

        if (chooseIndex && criteria.getFeeds() != null && criteria.getFeeds().getExclude() != null
                && criteria.getFeeds().getExclude().getSet().size() == 1) {
            chooseIndex = false;
            indexToUse = MYSQL_INDEX_STRM_FK_FD_ID_CRT_MS_IDX;
        }

        if (chooseIndex) {
            chooseIndex = false;
            indexToUse = MYSQL_INDEX_STRM_CRT_MS_IDX;
        }

        if (indexToUse != null) {
            sql.append(" USE INDEX (");
            sql.append(indexToUse);
            sql.append(")");
        }

        if (criteria.getAttributeConditionList() != null) {
            for (int i = 0; i < criteria.getAttributeConditionList().size(); i++) {
                final StreamAttributeCondition streamAttributeCondition = criteria.getAttributeConditionList().get(i);
                final StreamAttributeKey streamAttributeKey = streamAttributeCondition.getStreamAttributeKey();

                sql.append(" JOIN ");
                sql.append(StreamAttributeValue.TABLE_NAME);
                sql.append(" SAV");
                sql.append(i, false);
                sql.append(" ON (S.");
                sql.append(StreamEntity.ID);
                sql.append(" = SAV");
                sql.append(i, false);
                sql.append(".");
                sql.append(StreamAttributeValue.STREAM_ID);
                sql.append(" AND SAV");
                sql.append(i, false);
                sql.append(".");
                sql.append(StreamAttributeValue.STREAM_ATTRIBUTE_KEY_ID);
                sql.append(" = ");
                sql.arg(streamAttributeKey.getId());
                sql.append(")");
            }
        }

        appendStreamProcessorJoin(criteria, sql);
    }

    private void appendStreamProcessorJoin(final OldFindStreamCriteria queryCriteria, final SqlBuilder sql) {
        if (queryCriteria.getPipelineSet() != null && queryCriteria.getPipelineSet().isConstrained()) {
            sql.append(" JOIN ");
            sql.append(StreamProcessor.TABLE_NAME);
            sql.append(" SP ON (SP.");
            sql.append(StreamProcessor.ID);
            sql.append(" = S.");
            sql.append(StreamProcessor.FOREIGN_KEY);
            sql.append(")");
        }
    }

    private void appendStreamCriteria(final OldFindStreamCriteria criteria, final SqlBuilder sql) {
        if (criteria.getAttributeConditionList() != null) {
            for (int i = 0; i < criteria.getAttributeConditionList().size(); i++) {
                final StreamAttributeCondition condition = criteria.getAttributeConditionList().get(i);
                final StreamAttributeFieldUse use = StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP
                        .get(condition.getStreamAttributeKey().getName());
                if (use != null) {
                    final Object[] values = getValues(use, condition);

                    if (values != null && values.length > 0) {
                        final boolean toLong = use.isNumeric();
                        String field;
                        if (toLong) {
                            field = "SAV" + i + "." + StreamAttributeValue.VALUE_NUMBER;
                        } else {
                            field = "SAV" + i + "." + StreamAttributeValue.VALUE_STRING;
                        }

                        sql.append(" AND ");
                        switch (condition.getCondition()) {
                            case CONTAINS:
                                sql.append(field);
                                sql.append(" LIKE ");
                                sql.arg(values[0]);
                                break;
                            case EQUALS:
                                sql.append(field);
                                sql.append(" = ");
                                sql.arg(values[0]);
                                break;
                            case GREATER_THAN:
                                sql.append(field);
                                sql.append(" > ");
                                sql.arg(values[0]);
                                break;
                            case GREATER_THAN_OR_EQUAL_TO:
                                sql.append(field);
                                sql.append(" >= ");
                                sql.arg(values[0]);
                                break;

                            case LESS_THAN:
                                sql.append(field);
                                sql.append(" < ");
                                sql.arg(values[0]);
                                break;

                            case LESS_THAN_OR_EQUAL_TO:
                                sql.append(field);
                                sql.append(" <= ");
                                sql.arg(values[0]);
                                break;

                            case BETWEEN:
                                sql.append(field);
                                sql.append(" >= ");
                                sql.arg(values[0]);

                                if (values.length > 1) {
                                    sql.append(" AND ");
                                    sql.append(field);
                                    sql.append(" <= ");
                                    sql.arg(values[1]);
                                }
                                break;
                        }
                    }
                }
            }
        }

        sql.appendRangeQuery("S." + StreamEntity.CREATE_MS, criteria.getCreatePeriod());

        sql.appendRangeQuery("S." + StreamEntity.EFFECTIVE_MS, criteria.getEffectivePeriod());

        sql.appendRangeQuery("S." + StreamEntity.STATUS_MS, criteria.getStatusPeriod());

        sql.appendRangeQuery("S." + StreamEntity.ID, criteria.getStreamIdRange());

        sql.appendEntityIdSetQuery("S." + StreamEntity.ID, criteria.getStreamIdSet());

        sql.appendPrimitiveValueSetQuery("S." + StreamEntity.STATUS, StreamStatusId.convertStatusSet(criteria.getStatusSet()));

        sql.appendEntityIdSetQuery("S." + StreamEntity.PARENT_STREAM_ID, criteria.getParentStreamIdSet());
        sql.appendEntityIdSetQuery("S." + StreamTypeEntity.FOREIGN_KEY, criteria.getStreamTypeIdSet());
        sql.appendIncludeExcludeSetQuery("S." + FeedEntity.FOREIGN_KEY, criteria.getFeeds());

        sql.appendDocRefSetQuery("SP." + StreamProcessor.PIPELINE_UUID, criteria.getPipelineSet());
        sql.appendEntityIdSetQuery("S." + StreamProcessor.FOREIGN_KEY, criteria.getStreamProcessorIdSet());
    }

    private Object[] getValues(final StreamAttributeFieldUse use, final StreamAttributeCondition condition) {
        Object[] values = null;

        final boolean toLong = use.isNumeric();
        if (condition.getFieldValue() != null) {
            final String[] parts = condition.getFieldValue().split(",");
            values = new Object[parts.length];
            for (int i = 0; i < parts.length; i++) {
                if (toLong) {
                    try {
                        values[i] = Long.parseLong(parts[i]);
                    } catch (final NumberFormatException e) {
                        // Ignore
                    }
                } else {
                    values[i] = parts[i];
                }
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<Stream> findChildren(final OldFindStreamCriteria fetchCriteria, final List<Stream> streamList) {
        final CriteriaSet<Long> criteriaSet = new CriteriaSet<>();
        for (final Stream stream : streamList) {
            criteriaSet.add(stream.getId());
        }
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT s FROM ");
        sql.append(StreamEntity.class.getName());
        sql.append(" s");
        sql.append(" WHERE 1=1");

        sql.appendCriteriaSetQuery("s.parentStreamId", criteriaSet);
        // Only pick up unlocked streams if set are filtering with status
        // (normal mode in GUI)
        sql.appendPrimitiveValueSetQuery("s.pstatus", StreamStatusId.convertStatusSet(fetchCriteria.getStatusSet()));

        return entityManager.executeQueryResultList(sql);
    }

    @SuppressWarnings("unchecked")
    private Stream findParent(final Stream stream) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT s FROM ");
        sql.append(StreamEntity.class.getName());
        sql.append(" s");
        sql.append(" WHERE s.id = ");
        sql.arg(stream.getParentStreamId());

        final List<StreamEntity> parentList = entityManager.executeQueryResultList(sql);
        if (parentList != null && parentList.size() > 0) {
            return parentList.get(0);
        }
        return StreamEntity.createStub(stream.getParentStreamId());
    }

    /**
     * <p>
     * Helper method to find matches within the range
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<StreamEntity> findStreamSource(final EffectiveMetaDataCriteria criteria) {
        final StreamTypeEntity streamType = getStreamType(criteria.getStreamType());
        final FeedEntity feed = getFeed(criteria.getFeed());

        // Build up the HQL
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT S FROM ");
        sql.append(StreamEntity.class.getName());
        sql.append(" S WHERE");
        sql.append(" S.streamType.id = ");
        sql.arg(streamType.getId());
        sql.append(" AND S.pstatus = ");
        // Only find stuff that has been written
        sql.arg(StreamStatusId.UNLOCKED);
        sql.appendRangeQuery("S.effectiveMs", criteria.getEffectivePeriod());
        sql.appendValueQuery("S.feed", feed);

        // Create the query
        // Get the results
        return entityManager.executeQueryResultList(sql);
    }

    /**
     * <p>
     * We do 3 queries to find this:<br/>
     * 1) Find matches within the range 2) Find the date of the best match
     * outside the range 3) Find the matches based on the best date match.
     * </p>
     *
     * @return the list of good matches
     */
    @Override
    @SuppressWarnings("unchecked")
    // @Transactional
    public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
        final StreamTypeEntity streamType = getStreamType(criteria.getStreamType());

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // Find meta data within effective period.
        final ArrayList<Stream> rtnList = new ArrayList<>(findStreamSource(criteria));

        // Find the greatest effective stream time that we can that is less than
        // the from time of the effective period.
        final Map<Long, Long> maxMatch = getMaxEffective(criteria);

        // Found any 'best' matches.
        if (maxMatch.size() > 0) {
            // Sort the returned feed matches by id.
            final List<Long> feedList = new ArrayList<>(maxMatch.keySet());
            Collections.sort(feedList);

            // Now load just the 'best' matches up.
            final HqlBuilder sql = new HqlBuilder();
            sql.append("SELECT S FROM ");
            sql.append(StreamEntity.class.getName());
            sql.append(" S WHERE");
            sql.append(" S.streamType.id = ");
            sql.arg(streamType.getId());
            sql.append(" AND S.pstatus = ");
            sql.arg(StreamStatusId.UNLOCKED);
            sql.append(" AND (");

            for (final Long feed : feedList) {
                sql.append("(S.effectiveMs = ");
                sql.arg(maxMatch.get(feed));
                sql.append(" AND S.feed.id = ");
                sql.arg(feed);
                sql.append(") OR ");
            }

            // remove last OR
            sql.setLength(sql.length() - " OR ".length());
            sql.append(")");

            final LogExecutionTime logExecutionTime2 = new LogExecutionTime();
            final List<StreamEntity> results = entityManager.executeQueryResultList(sql);
            EntityServiceLogUtil.logQuery(LOGGER, "findEffectiveStreamSource()", logExecutionTime2, results, sql);

            rtnList.addAll(results);
        }

        EntityServiceLogUtil.logQuery(LOGGER, "findEffectiveStream()", logExecutionTime, rtnList, null);
        return rtnList;
    }

    @Override
    public Period getCreatePeriod() {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT MIN(");
        sql.append(StreamEntity.CREATE_MS);
        sql.append("), MAX(");
        sql.append(StreamEntity.CREATE_MS);
        sql.append(") FROM ");
        sql.append(StreamEntity.TABLE_NAME);

        Period period = null;

        @SuppressWarnings("unchecked") final List<Object[]> rows = entityManager.executeNativeQueryResultList(sql);

        if (rows != null && rows.size() > 0) {
            period = new Period(((Number) rows.get(0)[0]).longValue(), ((Number) rows.get(0)[1]).longValue());
        }

        return period;
    }

    private Map<Long, Long> getMaxEffective(final EffectiveMetaDataCriteria criteria) {
        final StreamTypeEntity streamType = getStreamType(criteria.getStreamType());
        final FeedEntity feed = getFeed(criteria.getFeed());

        final Map<Long, Long> rtnMap = new HashMap<>();

        // Find best match otherwise.
        final SqlBuilder sql = new SqlBuilder();
//        if (!stroomDatabaseInfo.isMysql() && criteria.getFeed() != null) {
//            final EntityIdSet<Feed> originalFeedSet = new EntityIdSet<>();
//            originalFeedSet.copyFrom(criteria.getFeedIdSet());
//
//            for (final Long feedId : originalFeedSet) {
//                criteria.getFeedIdSet().clear();
//                criteria.getFeedIdSet().add(feedId);
//                rtnMap.putAll(getMaxEffective(criteria));
//            }
//            criteria.getFeedIdSet().clear();
//            criteria.getFeedIdSet().copyFrom(originalFeedSet);
//            return rtnMap;
//
//        }

//        boolean doneOne = false;
//        for (final Long feedId : criteria.getFeedIdSet()) {
//            if (doneOne) {
//                sql.append(" UNION");
//            }
        sql.append(" (SELECT ");
        sql.append(feed.getId());
        sql.append(", ");
        sql.append("MAX(");
        sql.append(StreamEntity.EFFECTIVE_MS);
        sql.append(") FROM ");
        sql.append(StreamEntity.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(StreamEntity.EFFECTIVE_MS);
        sql.append(" < ");
        sql.arg(criteria.getEffectivePeriod().getFrom());
        sql.append(" AND ");
        sql.append(StreamEntity.STATUS);
        sql.append(" = ");
        sql.arg(StreamStatusId.UNLOCKED);
        sql.append(" AND ");
        sql.append(StreamTypeEntity.FOREIGN_KEY);
        sql.append(" = ");
        sql.arg(streamType.getId());
        sql.append(" AND ");
        sql.append(FeedEntity.FOREIGN_KEY);
        sql.append(" = ");
        sql.arg(feed.getId());
        sql.append(")");
//
//            doneOne = true;
//        }

        @SuppressWarnings("unchecked") final List<Object[]> resultSet = entityManager.executeNativeQueryResultList(sql);

        for (final Object[] row : resultSet) {
            final Long feedId = SqlUtil.getLong(row, 0);
            final Long effectiveMs = SqlUtil.getLong(row, 1);

            if (feedId != null && effectiveMs != null) {
                rtnMap.put(feedId, effectiveMs);
            }
        }

        return rtnMap;
    }

    @Override
    // @Transactional
    public Long findDelete(final FindStreamCriteria criteria) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            final Context context = new Context(null, System.currentTimeMillis());
            final OldFindStreamCriteria oldFindStreamCriteria = expressionToFindCriteria.convert(criteria, context);
            return findDelete(oldFindStreamCriteria);
        });
    }

    private Long findDelete(final OldFindStreamCriteria criteria) {
        // Ensure that included feeds are restricted to ones the user can delete.
        restrictCriteriaByFeedPermissions(criteria, DocumentPermissionNames.DELETE);

        // If the current user is not an admin and no feeds are readable that have been requested then return an empty array.
        if (!securityContext.isAdmin() && criteria.obtainFeeds().obtainInclude().size() == 0) {
            return 0L;
        }

        byte newStatus = StreamStatusId.DELETED;
        if (criteria.obtainStatusSet().isSingleItemMatch(StreamStatus.DELETED)) {
            newStatus = StreamStatusId.UNLOCKED;
        }

        final SqlBuilder sql = new SqlBuilder();

        if (stroomDatabaseInfo.isMysql()) {
            // UPDATE
            sql.append("UPDATE ");
            sql.append(StreamEntity.TABLE_NAME);
            sql.append(" S");

            // JOIN
            appendJoin(criteria, sql);

            // SET
            incrementVersion(sql, "S.");

            sql.append(StreamEntity.STATUS);
            sql.append(" = ");
            sql.arg(newStatus);
            sql.append(", ");
            sql.append(StreamEntity.STATUS_MS);
            sql.append(" = ");
            sql.arg(System.currentTimeMillis());

            // WHERE
            sql.append(" WHERE");
            sql.append(" S.");
            sql.append(StreamEntity.STATUS);
            sql.append(" <> ");
            sql.arg(newStatus);

            appendStreamCriteria(criteria, sql);

            // Append order by criteria.
//            sql.appendOrderBy(sql, false, criteria, "S");
            sql.applyRestrictionCriteria(criteria);

        } else {
            // UPDATE
            sql.append("UPDATE ");
            sql.append(StreamEntity.TABLE_NAME);
            sql.append(" US");

            // SET
            incrementVersion(sql, "US.");

            sql.append(StreamEntity.STATUS);
            sql.append(" = ");
            sql.arg(newStatus);
            sql.append(", US.");
            sql.append(StreamEntity.STATUS_MS);
            sql.append(" = ");
            sql.arg(System.currentTimeMillis());

            // WHERE
            sql.append(" WHERE US.ID IN (");

            // SUB SELECT
            sql.append("SELECT S.");
            sql.append(StreamEntity.ID);
            sql.append(" FROM ");
            sql.append(StreamEntity.TABLE_NAME);
            sql.append(" S");

            // JOIN
            appendJoin(criteria, sql);

            // WHERE
            sql.append(" WHERE");
            sql.append(" S.");
            sql.append(StreamEntity.STATUS);
            sql.append(" <> ");
            sql.arg(newStatus);

            appendStreamCriteria(criteria, sql);

            // Append order by criteria.
//            sql.appendOrderBy(sql, false, criteria, "S");
            sql.applyRestrictionCriteria(criteria);

            sql.append(")");
        }

        return entityManager.executeNativeUpdate(sql);
    }

    private void incrementVersion(final SqlBuilder sql, final String prefix) {
        sql.append(" SET ");
        sql.append(prefix);
        sql.append(BaseEntity.VERSION);
        sql.append(" = ");
        sql.append(prefix);
        sql.append(BaseEntity.VERSION);
        sql.append(" + 1, ");
        sql.append(prefix);
    }

    @Override
    public FindStreamCriteria createCriteria() {
        return new FindStreamCriteria();
    }

    private FeedEntity getFeed(final String name) {
        if (name == null) {
            throw new NullPointerException("No name specified for feed");
        }
        final FeedEntity feed = feedService.getOrCreate(name);
        if (feed == null) {
            throw new EntityServiceException("Unable to find feed '" + name + "'");
        }
        return feed;
    }

    private String getFeedUuid(final String name) {
        if (name == null) {
            return null;
        }
        return feedDocCache.get(name).map(Doc::getUuid).orElse(null);
    }

    private StreamTypeEntity getStreamType(final String name) {
        if (name == null) {
            throw new NullPointerException("No name specified for steam type");
        }
        final StreamTypeEntity streamType = streamTypeService.getOrCreate(name);
        if (streamType == null) {
            throw new EntityServiceException("Unable to find streamType '" + name + "'");
        }
        return streamType;
    }

    @Override
    public List<String> getFeeds() {
        final List<FeedEntity> feeds = feedService.find(new FindFeedCriteria());
        if (feeds == null) {
            return Collections.emptyList();
        }
        return feeds.stream()
                .map(NamedEntity::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getStreamTypes() {
        final List<StreamTypeEntity> streamTypes = streamTypeService.find(new FindStreamTypeCriteria());
        if (streamTypes == null) {
            return Collections.emptyList();
        }
        return streamTypes.stream()
                .map(NamedEntity::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public long getLockCount() {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(StreamEntity.TABLE_NAME);
        sql.append(" S WHERE S.");
        sql.append(StreamEntity.STATUS);
        sql.append(" = ");
        sql.arg(StreamStatusId.LOCKED);
        return entityManager.executeNativeQueryLongResult(sql);
    }
}
