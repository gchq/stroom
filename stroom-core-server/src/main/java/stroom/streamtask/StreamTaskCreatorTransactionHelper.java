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

package stroom.streamtask;


import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.Sort.Direction;
import stroom.entity.util.ConnectionUtil;
import stroom.entity.util.SqlBuilder;
import stroom.jobsystem.ClusterLockService;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.persist.EntityManagerSupport;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataStatus;
import stroom.data.meta.api.MetaDataSource;
import stroom.streamtask.InclusiveRanges.InclusiveRange;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.streamtask.shared.ProcessorFilterTracker;
import stroom.streamtask.shared.TaskStatus;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class used to do the transactional aspects of stream task creation
 */
class StreamTaskCreatorTransactionHelper {
    private static final int RECENT_STREAM_ID_LIMIT = 10000;
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTaskCreatorTransactionHelper.class);

    private static final String LOCK_NAME = "StreamTaskCreator";
//    private static final SqlBuilder MAX_STREAM_ID_SQL;
//
//    static {
//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT MAX(");
//        sql.append(StreamEntity.ID);
//        sql.append(") FROM ");
//        sql.append(StreamEntity.TABLE_NAME);
//        MAX_STREAM_ID_SQL = sql;
//    }

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final NodeCache nodeCache;
    private final ClusterLockService clusterLockService;
    private final StreamTaskService streamTaskService;
    private final DataMetaService streamMetaService;
    private final StroomEntityManager stroomEntityManager;
    private final EntityManagerSupport entityManagerSupport;

    @Inject
    StreamTaskCreatorTransactionHelper(final NodeCache nodeCache,
                                       final ClusterLockService clusterLockService,
                                       final StreamTaskService streamTaskService,
                                       final DataMetaService streamMetaService,
                                       final StroomEntityManager stroomEntityManager,
                                       final EntityManagerSupport entityManagerSupport) {
        this.nodeCache = nodeCache;
        this.clusterLockService = clusterLockService;
        this.streamTaskService = streamTaskService;
        this.streamMetaService = streamMetaService;
        this.stroomEntityManager = stroomEntityManager;
        this.entityManagerSupport = entityManagerSupport;
    }

    /**
     * Anything that we owned release
     */
    public void releaseOwnedTasks() {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("UPDATE ");
        sql.append(ProcessorFilterTask.TABLE_NAME);
        sql.append(" SET ");
        sql.append(ProcessorFilterTask.STATUS);
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
        sql.appendPrimitiveValueSetQuery(ProcessorFilterTask.STATUS, criteriaSet);

        final long results = stroomEntityManager.executeNativeUpdate(sql);

        LOGGER.info(
                "doStartup() - Set {} Tasks back to UNPROCESSED (Reprocess), NULL that were UNPROCESSED, ASSIGNED, PROCESSING for node {}",
                results, nodeCache.getDefaultNode().getName());
    }



//    private ExpressionOperator copyExpression(final ExpressionOperator expression) {
//        ExpressionOperator.Builder builder;
//
//        if (expression != null) {
//            builder = new ExpressionOperator.Builder(expression.getOp());
//
//            if (expression.enabled() && expression.getChildren() != null) {
//                addChildren(builder, expression);
//            }
//
//        } else {
//            builder = new ExpressionOperator.Builder(Op.AND);
//        }
//
//        final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR)
//                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.LOCKED.getDisplayValue())
//                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue());
//        builder.addOperator(or.build());
//        return builder.build();
//    }
//
//    private void addChildren(final ExpressionOperator.Builder builder, final ExpressionOperator parent) {
//        for (final ExpressionItem item : parent.getChildren()) {
//            if (item.enabled()) {
//                if (item instanceof ExpressionOperator) {
//                    final ExpressionOperator expressionOperator = (ExpressionOperator) item;
//                    final ExpressionOperator.Builder child = new ExpressionOperator.Builder(Op.OR);
//                    addChildren(child, expressionOperator);
//                    builder.addOperator(child.build());
//                } else if (item instanceof ExpressionTerm) {
//                    final ExpressionTerm expressionTerm = (ExpressionTerm) item;
//
//                    // Don't copy stream status terms as we are going to set them later.
//                    if (!StreamDataSource.STATUS.equals(expressionTerm.getField())) {
//                        if (Condition.IN_DICTIONARY.equals(expressionTerm.getCondition())) {
//                            builder.addDictionaryTerm(expressionTerm.getField(), expressionTerm.getCondition(), expressionTerm.getDictionary());
//                        } else {
//                            builder.addTerm(expressionTerm.getField(), expressionTerm.getCondition(), expressionTerm.getValue());
//                        }
//                    }
//                }
//            }
//        }
//    }

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
     * @param reachedLimit     For search based stream task creation this indicates if we
     *                         have reached the limit of stream tasks created for a single
     *                         search. This limit is imposed to stop search based task
     *                         creation running forever.
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated
     * streams are not locked).
     */
    public CreatedTasks createNewTasks(final ProcessorFilter filter,
                                       final ProcessorFilterTracker tracker,
                                       final long streamQueryTime,
                                       final Map<Data, InclusiveRanges> streams,
                                       final Node thisNode,
                                       final Long maxMetaId,
                                       final boolean reachedLimit) {
        return entityManagerSupport.transactionResult(em -> {
            List<ProcessorFilterTask> availableTaskList = Collections.emptyList();
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
                            ProcessorFilterTask.CREATE_MS,
                            ProcessorFilterTask.STATUS,
                            ProcessorFilterTask.STATUS_MS,
                            Node.FOREIGN_KEY,
                            ProcessorFilterTask.STREAM_ID,
                            ProcessorFilterTask.DATA,
                            ProcessorFilter.FOREIGN_KEY);

                    final List<List<Object>> allArgs = new ArrayList<>();


                    for (final Entry<Data, InclusiveRanges> entry : streams.entrySet()) {
                        final Data stream = entry.getKey();
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

                        if (DataStatus.UNLOCKED.equals(stream.getStatus())) {
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
                        if (eventRangeData != null && !eventRangeData.isEmpty()) {
                            rowArgs.add(eventRangeData); //dat
                        } else {
                            rowArgs.add(null); //dat
                        }
                        rowArgs.add(filter.getId()); //fk_strm_proc_filt_id

                        allArgs.add(rowArgs);
                    }

                    // Save the stream tasks using the existing transaction
                    final Session session = em.unwrap(Session.class);
                    session.doWork(connection -> {
                        try {
                            try (final ConnectionUtil.MultiInsertExecutor multiInsertExecutor = new ConnectionUtil.MultiInsertExecutor(
                                    connection,
                                    ProcessorFilterTask.TABLE_NAME,
                                    columnNames)) {

                                multiInsertExecutor.execute(allArgs);
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    });

                    totalTasksCreated = allArgs.size();

                    // Select them back
                    final FindStreamTaskCriteria findStreamTaskCriteria = new FindStreamTaskCriteria();
                    findStreamTaskCriteria.obtainNodeIdSet().add(thisNode.getId());
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
                    LOGGER.debug("processStreamProcessorFilter() - Created {} tasks ({} available) in the range {}", totalTasksCreated, availableTasksCreated, streamIdRange);

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
                    if (maxMetaId != null) {
                        tracker.setMinStreamId(maxMetaId + 1);
                        tracker.setMinEventId(0L);
                    }
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
                        && tracker.getStreamCreateMs() > tracker.getMaxStreamCreateMs()) {
                    LOGGER.info("processStreamProcessorFilter() - Finished task creation for bounded filter {}", filter);
                    tracker.setStatus(ProcessorFilterTracker.COMPLETE);
                }

                // Save the tracker state.
                saveTracker(tracker);

            } catch (final RuntimeException e) {
                LOGGER.error("createNewTasks", e);
            }

            return new CreatedTasks(availableTaskList, availableTasksCreated, totalTasksCreated, eventCount);
        });
    }

    ProcessorFilterTracker saveTracker(final ProcessorFilterTracker tracker) {
        return stroomEntityManager.saveEntity(tracker);
    }

    private void lockCluster() {
        clusterLockService.lock(LOCK_NAME);
    }

//    public StreamTaskCreatorRecentStreamDetails getRecentStreamInfo(
//            final StreamTaskCreatorRecentStreamDetails lastRecent) {
//        StreamTaskCreatorRecentStreamDetails recentStreamInfo = new StreamTaskCreatorRecentStreamDetails(lastRecent,
//                getMaxStreamId());
//        if (recentStreamInfo.hasRecentDetail()) {
//            // Only do this check if not that many streams have come in.
//            if (recentStreamInfo.getRecentStreamCount() > RECENT_STREAM_ID_LIMIT) {
//                // Forget the history and start again.
//                recentStreamInfo = new StreamTaskCreatorRecentStreamDetails(null, recentStreamInfo.getMaxStreamId());
//            } else {
//                final SqlBuilder sql = new SqlBuilder();
//                sql.append("SELECT DISTINCT(");
//                sql.append(FeedEntity.FOREIGN_KEY);
//                sql.append("), 'A' FROM ");
//                sql.append(StreamEntity.TABLE_NAME);
//                sql.append(" WHERE ");
//                sql.append(StreamEntity.ID);
//                sql.append(" > ");
//                sql.arg(recentStreamInfo.getRecentStreamId());
//                sql.append(" AND ");
//                sql.append(StreamEntity.ID);
//                sql.append(" <= ");
//                sql.arg(recentStreamInfo.getMaxStreamId());
//
//                // Find out about feeds that have come in recently
//                @SuppressWarnings("unchecked") final List<Object[]> resultSet = stroomEntityManager.executeNativeQueryResultList(sql);
//
//                for (final Object[] row : resultSet) {
//                    recentStreamInfo.addRecentFeedId(((Number) row[0]).longValue());
//                }
//            }
//        }
//        return recentStreamInfo;
//    }
//
//    private long getMaxStreamId() {
//        return stroomEntityManager.executeNativeQueryLongResult(MAX_STREAM_ID_SQL);
//    }

    public static class CreatedTasks {
        private final List<ProcessorFilterTask> availableTaskList;
        private final int availableTasksCreated;
        private final int totalTasksCreated;
        private final long eventCount;

        CreatedTasks(final List<ProcessorFilterTask> availableTaskList, final int availableTasksCreated,
                     final int totalTasksCreated, final long eventCount) {
            this.availableTaskList = availableTaskList;
            this.availableTasksCreated = availableTasksCreated;
            this.totalTasksCreated = totalTasksCreated;
            this.eventCount = eventCount;
        }

        List<ProcessorFilterTask> getAvailableTaskList() {
            return availableTaskList;
        }

        public int getAvailableTasksCreated() {
            return availableTasksCreated;
        }

        int getTotalTasksCreated() {
            return totalTasksCreated;
        }

        public long getEventCount() {
            return eventCount;
        }
    }
}
