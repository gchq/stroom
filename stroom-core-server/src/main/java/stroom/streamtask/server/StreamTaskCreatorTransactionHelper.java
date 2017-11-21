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

package stroom.streamtask.server;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.Sort.Direction;
import stroom.feed.shared.Feed;
import stroom.jobsystem.server.ClusterLockService;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamtask.server.InclusiveRanges.InclusiveRange;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.StreamTaskService;
import stroom.streamtask.shared.TaskStatus;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class used to do the transactional aspects of stream task creation
 */
@Component
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class StreamTaskCreatorTransactionHelper {
    public static class CreatedTasks {
        private final List<StreamTask> availableTaskList;
        private final int availableTasksCreated;
        private final int totalTasksCreated;
        private final long eventCount;

        public CreatedTasks(final List<StreamTask> availableTaskList, final int availableTasksCreated,
                            final int totalTasksCreated, final long eventCount) {
            this.availableTaskList = availableTaskList;
            this.availableTasksCreated = availableTasksCreated;
            this.totalTasksCreated = totalTasksCreated;
            this.eventCount = eventCount;
        }

        public List<StreamTask> getAvailableTaskList() {
            return availableTaskList;
        }

        public int getAvailableTasksCreated() {
            return availableTasksCreated;
        }

        public int getTotalTasksCreated() {
            return totalTasksCreated;
        }

        public long getEventCount() {
            return eventCount;
        }
    }

    protected static final StroomLogger LOGGER = StroomLogger.getLogger(StreamTaskCreator.class);

    private static final String LOCK_NAME = "StreamTaskCreator";
    public static final int RECENT_STREAM_ID_LIMIT = 10000;

    @Resource
    private NodeCache nodeCache;
    @Resource
    private ClusterLockService clusterLockService;
    @Resource
    private StreamTaskService streamTaskService;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StroomEntityManager stroomEntityManager;
    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource(name = "dataSource")
    DataSource dataSource;


    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    /**
     * Anything that we owned release
     */
    public void releaseOwnedTasks() {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("UPDATE ");
        sql.append(StreamTask.TABLE_NAME);
        sql.append(" SET ");
        sql.append(StreamTask.STATUS);
        sql.append(" = ");
        sql.arg(TaskStatus.UNPROCESSED.getPrimitiveValue());
        sql.append(", ");
        sql.append(Node.FOREIGN_KEY);
        sql.append(" = NULL WHERE ");
        sql.append(Node.FOREIGN_KEY);
        sql.append(" = ");
        sql.arg(nodeCache.getDefaultNode().getId());
        final CriteriaSet<TaskStatus> criteriaSet = new CriteriaSet<>();
        criteriaSet.addAll(Arrays.asList(TaskStatus.UNPROCESSED, TaskStatus.ASSIGNED, TaskStatus.PROCESSING));
        sql.appendPrimitiveValueSetQuery(StreamTask.STATUS, criteriaSet);

        final long results = stroomEntityManager.executeNativeUpdate(sql);

        LOGGER.info(
                "doStartup() - Set %s Tasks back to UNPROCESSED (Reprocess), NULL that were UNPROCESSED, ASSIGNED, PROCESSING for node %s",
                results, nodeCache.getDefaultNode().getName());
    }

    /**
     * @return streams that have not yet got a stream task for a particular
     * stream processor
     */
    public List<Stream> runSelectStreamQuery(final StreamProcessor streamProcessor, final FindStreamCriteria criteria,
                                             final long minStreamId, final int max) {
        // Copy the filter
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.copyFrom(criteria);
        findStreamCriteria.setSort(FindStreamCriteria.FIELD_ID, Direction.ASCENDING, false);
        findStreamCriteria.setStreamIdRange(new IdRange(minStreamId, null));
        // Don't care about status
        findStreamCriteria.obtainStatusSet().add(StreamStatus.LOCKED);
        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        findStreamCriteria.obtainPageRequest().setLength(max);

        return streamStore.find(findStreamCriteria);
    }

    /**
     * Create new tasks for the specified filter and add them to the queue.
     *
     * @param filter           The fitter to create tasks for
     * @param tracker          The tracker that tracks the task creation progress for the
     *                         filter.
     * @param streamQueryTime  The time that we queried for streams that match the stream
     *                         processor filter.
     * @param streams          The map of streams and optional event ranges to create stream
     *                         tasks for.
     * @param thisNode         This node, the node that will own the created tasks.
     * @param recentStreamInfo Information we have about streams that have been recently
     *                         created.
     * @param reachedLimit     For search based stream task creation this indicates if we
     *                         have reached the limit of stream tasks created for a single
     *                         search. This limit is imposed to stop search based task
     *                         creation running forever.
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated
     * streams are not locked).
     */
    public CreatedTasks createNewTasks(final StreamProcessorFilter filter,
                                       final StreamProcessorFilterTracker tracker,
                                       final long streamQueryTime,
                                       final Map<Stream, InclusiveRanges> streams,
                                       final Node thisNode,
                                       final StreamTaskCreatorRecentStreamDetails recentStreamInfo,
                                       final boolean reachedLimit) {
        List<StreamTask> availableTaskList = Collections.emptyList();
        int availableTasksCreated = 0;
        int totalTasksCreated = 0;
        long eventCount = 0;

        try {
            // Lock the cluster so that only this node can create tasks for this
            // filter at this time.
            lockCluster();

            // Get the current time.
            final long streamTaskCreateMs = System.currentTimeMillis();

            InclusiveRange streamIdRange = null;
            InclusiveRange streamMsRange = null;
            InclusiveRange eventIdRange = null;

            if (streams.size() > 0) {

                final List<String> columnNames = Arrays.asList(
                        BaseEntity.VERSION,
                        StreamTask.CREATE_MS,
                        StreamTask.STATUS,
                        StreamTask.STATUS_MS,
                        Node.FOREIGN_KEY,
                        Stream.FOREIGN_KEY,
                        StreamTask.DATA,
                        StreamProcessorFilter.FOREIGN_KEY);

                final List<List<Object>> allArgs = new ArrayList<>();

                for (final Entry<Stream, InclusiveRanges> entry : streams.entrySet()) {
                    final Stream stream = entry.getKey();
                    final InclusiveRanges eventRanges = entry.getValue();

                    String eventRangeData = null;
                    if (eventRanges != null) {
                        eventRangeData = eventRanges.rangesToString();
                        eventCount += eventRanges.count();
                    }

                    // Update the max event id if this stream id is greater than
                    // any we have seen before.
                    if (streamIdRange == null || stream.getId() > streamIdRange.getMax()) {
                        if (eventRanges != null) {
                            eventIdRange = eventRanges.getOuterRange();
                        } else {
                            eventIdRange = null;
                        }
                    }

                    streamIdRange = InclusiveRange.extend(streamIdRange, stream.getId());
                    streamMsRange = InclusiveRange.extend(streamMsRange, stream.getCreateMs());

                    final List<Object> rowArgs = new ArrayList<>(columnNames.size());

                    rowArgs.add(1); //version
                    rowArgs.add(streamTaskCreateMs); //create_ms
                    rowArgs.add(TaskStatus.UNPROCESSED.getPrimitiveValue()); //stat
                    rowArgs.add(streamTaskCreateMs); //stat_ms

                    if (StreamStatus.UNLOCKED.equals(stream.getStatus())) {
                        // If the stream is unlocked then take ownership of the
                        // task, i.e. set the node to this node.
                        rowArgs.add(thisNode.getId()); //fk_node_id
                        availableTasksCreated++;
                    } else {
                        // If the stream is locked then don't take ownership of
                        // the task at this time, i.e. set the node to null.
                        rowArgs.add(null); //fk_node_id
                    }
                    rowArgs.add(stream.getId()); //fk_strm_id
                    if (eventRangeData != null && eventRangeData.length() > 0) {
                        rowArgs.add(eventRangeData); //dat
                    } else {
                        rowArgs.add(null); //dat
                    }
                    rowArgs.add(filter.getId()); //fk_strm_proc_filt_id

                    allArgs.add(rowArgs);
                }


                // Save them using the existing transaction
                final Connection connection = DataSourceUtils.getConnection(dataSource);
                ConnectionUtil.executeMultiInsert(
                        connection,
                        StreamTask.TABLE_NAME,
                        columnNames,
                        allArgs);

                totalTasksCreated = allArgs.size();

                // Select them back
                final FindStreamTaskCriteria findStreamTaskCriteria = new FindStreamTaskCriteria();
                findStreamTaskCriteria.obtainNodeIdSet().add(thisNode);
                findStreamTaskCriteria.setCreateMs(streamTaskCreateMs);
                findStreamTaskCriteria.obtainStreamTaskStatusSet().add(TaskStatus.UNPROCESSED);
                findStreamTaskCriteria.obtainStreamProcessorFilterIdSet().add(filter.getId());
                availableTaskList = streamTaskService.find(findStreamTaskCriteria);

                taskStatusTraceLog.createdTasks(StreamTaskCreatorTransactionHelper.class, availableTaskList);

                // Ensure that the select has got back the stream tasks that we
                // have just inserted. If it hasn't this would be very bad.
                if (availableTaskList.size() != availableTasksCreated) {
                    throw new RuntimeException("Unexpected number of stream tasks selected back after insertion.");
                }
            }

            // Anything created?
            if (totalTasksCreated > 0) {
                LOGGER.debug("processStreamProcessorFilter() - Created %s tasks (%s available) in the range %s",
                        totalTasksCreated, availableTasksCreated, streamIdRange);

                // If we have never created tasks before or the last poll gave
                // us no tasks then start to report a new creation range.
                if (tracker.getMinStreamCreateMs() == null || (tracker.getLastPollTaskCount() != null
                        && tracker.getLastPollTaskCount().longValue() == 0L)) {
                    tracker.setMinStreamCreateMs(streamMsRange.getMin());
                }
                // Report where we have got to.
                tracker.setStreamCreateMs(streamMsRange.getMax());

                // Only create tasks for streams with an id 1 or more greater
                // than the greatest stream id we have created tasks for this
                // time round in future.
                if (eventIdRange != null) {
                    tracker.setMinStreamId(streamIdRange.getMax());
                    tracker.setMinEventId(eventIdRange.getMax() + 1);
                } else {
                    tracker.setMinStreamId(streamIdRange.getMax() + 1);
                    tracker.setMinEventId(0L);
                }

            } else {
                // We have completed all tasks so update the window to be from
                // now
                tracker.setMinStreamCreateMs(streamTaskCreateMs);

                // Report where we have got to.
                tracker.setStreamCreateMs(streamQueryTime);

                // Only create tasks for streams with an id 1 or more greater
                // than the current max stream id in future as we didn't manage
                // to create any tasks.
                tracker.setMinStreamId(recentStreamInfo.getMaxStreamId() + 1);
                tracker.setMinEventId(0L);
            }

            if (tracker.getStreamCount() != null) {
                if (totalTasksCreated > 0) {
                    tracker.setStreamCount(tracker.getStreamCount() + totalTasksCreated);
                }
            } else {
                tracker.setStreamCount((long) totalTasksCreated);
            }
            if (eventCount > 0) {
                if (tracker.getEventCount() != null) {
                    tracker.setEventCount(tracker.getEventCount() + eventCount);
                } else {
                    tracker.setEventCount(eventCount);
                }
            }

            tracker.setLastPollMs(streamTaskCreateMs);
            tracker.setLastPollTaskCount(totalTasksCreated);
            tracker.setStatus(null);

            // Has this filter finished creating tasks for good, i.e. is there
            // any possibility of getting more tasks in future?
            if (tracker.getMaxStreamCreateMs() != null && tracker.getStreamCreateMs() != null
                    && tracker.getStreamCreateMs().longValue() > tracker.getMaxStreamCreateMs()) {
                LOGGER.info("processStreamProcessorFilter() - Finished task creation for bounded filter %s", filter);
                tracker.setStatus(StreamProcessorFilterTracker.COMPLETE);
            }

            // Save the tracker state.
            saveTracker(tracker);

        } catch (final Exception ex) {
            LOGGER.error("createNewTasks", ex);
        }

        return new CreatedTasks(availableTaskList, availableTasksCreated, totalTasksCreated, eventCount);
    }

    public StreamProcessorFilterTracker saveTracker(final StreamProcessorFilterTracker tracker) {
        return stroomEntityManager.saveEntity(tracker);
    }

    private void lockCluster() {
        clusterLockService.lock(LOCK_NAME);
    }

    public StreamTaskCreatorRecentStreamDetails getRecentStreamInfo(
            final StreamTaskCreatorRecentStreamDetails lastRecent) {
        StreamTaskCreatorRecentStreamDetails recentStreamInfo = new StreamTaskCreatorRecentStreamDetails(lastRecent,
                getMaxStreamId());
        if (recentStreamInfo.hasRecentDetail()) {
            // Only do this check if not that many streams have come in.
            if (recentStreamInfo.getRecentStreamCount() > RECENT_STREAM_ID_LIMIT) {
                // Forget the history and start again.
                recentStreamInfo = new StreamTaskCreatorRecentStreamDetails(null, recentStreamInfo.getMaxStreamId());
            } else {
                final SqlBuilder sql = new SqlBuilder();
                sql.append("SELECT DISTINCT(");
                sql.append(Feed.FOREIGN_KEY);
                sql.append("), 'A' FROM ");
                sql.append(Stream.TABLE_NAME);
                sql.append(" WHERE ");
                sql.append(Stream.ID);
                sql.append(" > ");
                sql.arg(recentStreamInfo.getRecentStreamId());
                sql.append(" AND ");
                sql.append(Stream.ID);
                sql.append(" <= ");
                sql.arg(recentStreamInfo.getMaxStreamId());

                // Find out about feeds that have come in recently
                @SuppressWarnings("unchecked") final List<Object[]> resultSet = stroomEntityManager.executeNativeQueryResultList(sql);

                for (final Object[] row : resultSet) {
                    recentStreamInfo.addRecentFeedId(((Number) row[0]).longValue());
                }
            }
        }
        return recentStreamInfo;
    }

    private static final SqlBuilder MAX_STREAM_ID_SQL;

    static {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT MAX(");
        sql.append(Stream.ID);
        sql.append(") FROM ");
        sql.append(Stream.TABLE_NAME);
        MAX_STREAM_ID_SQL = sql;
    }

    private long getMaxStreamId() {
        return stroomEntityManager.executeNativeQueryLongResult(MAX_STREAM_ID_SQL);
    }
}
