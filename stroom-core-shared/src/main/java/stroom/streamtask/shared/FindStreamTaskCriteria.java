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

package stroom.streamtask.shared;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.HasIsConstrained;
import stroom.entity.shared.OrderBy;
import stroom.entity.shared.SQLNameConstants;
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

/**
 * <p>
 * API to find streams that have not yet been processed.
 * </p>
 */
public final class FindStreamTaskCriteria extends BaseCriteria implements HasIsConstrained {
    private static final long serialVersionUID = 5031936548305751911L;

    public static final int SUMMARY_POS_PIPELINE = 0;
    public static final int SUMMARY_POS_FEED = 1;
    public static final int SUMMARY_POS_PRIORITY = 2;
    public static final int SUMMARY_POS_STATUS = 3;

    public static final String TABLE_PREFIX_STREAM_TASK = "ST.";
    public static final String TABLE_PREFIX_FEED = "F.";
    public static final String TABLE_PREFIX_STREAM = "S.";
    public static final String TABLE_PREFIX_PIPELINE = "P.";

    public static final OrderBy ORDER_BY_STREAM_CREATE_DATE = new OrderBy("Created", "createMs",
            TABLE_PREFIX_STREAM + Stream.CREATE_MS);

    public static final OrderBy ORDER_BY_CREATE_TIME = new OrderBy("Created", "createMs",
            TABLE_PREFIX_STREAM_TASK + StreamTask.CREATE_MS);

    public static final OrderBy ORDER_BY_START_TIME = new OrderBy("Start Time", "startTimeMs",
            TABLE_PREFIX_STREAM_TASK + StreamTask.START_TIME_MS);

    public static final OrderBy ORDER_BY_END_TIME_DATE = new OrderBy("End Time", "endTimeMs",
            TABLE_PREFIX_STREAM_TASK + StreamTask.END_TIME_MS);

    public static final OrderBy ORDER_BY_FEED_NAME = new OrderBy("Feed", "stream.feed.name", "F_NAME");

    public static final OrderBy ORDER_BY_PRIORITY = new OrderBy("Priority", "streamProcessorFilter.priority", "PRIORITY_1");

    public static final OrderBy ORDER_BY_PIPELINE_NAME = new OrderBy("Pipeline", "streamProcessor.pipeline.name",
            "P_NAME");

    public static final OrderBy ORDER_BY_STATUS = new OrderBy("Status", "pstatus", "STAT_ID1");

    public static final OrderBy ORDER_BY_COUNT = new OrderBy("Count", "NA", SQLNameConstants.COUNT);

    public static final OrderBy ORDER_BY_NODE = new OrderBy("Node", "node.name", null);

    /**
     * Look for stream tasks with a certain status.
     */
    private CriteriaSet<TaskStatus> streamTaskStatusSet;

    /**
     * Find with a key
     */
    private EntityIdSet<Node> nodeIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<StreamTask> streamTaskIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<StreamProcessorFilter> streamProcessorFilterIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<PipelineEntity> pipelineIdSet = null;

    /**
     * Create at a particular time
     */
    private Long createMs = null;

    /**
     * Sub Filter
     */
    private FindStreamCriteria findStreamCriteria = null;

    public static final FindStreamTaskCriteria createWithStream(final Stream stream) {
        final FindStreamTaskCriteria criteria = new FindStreamTaskCriteria();
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(stream.getId());
        return criteria;
    }

    @Override
    public boolean isConstrained() {
        if (streamTaskStatusSet != null && streamTaskStatusSet.isConstrained()) {
            return true;
        }
        if (nodeIdSet != null && nodeIdSet.isConstrained()) {
            return true;
        }
        if (streamTaskIdSet != null && streamTaskIdSet.isConstrained()) {
            return true;
        }
        if (streamProcessorFilterIdSet != null && streamProcessorFilterIdSet.isConstrained()) {
            return true;
        }

        return findStreamCriteria != null && findStreamCriteria.isConstrained();
    }

    public CriteriaSet<TaskStatus> getStreamTaskStatusSet() {
        return streamTaskStatusSet;
    }

    public void setStreamTaskStatusSet(final CriteriaSet<TaskStatus> streamTaskStatusSet) {
        this.streamTaskStatusSet = streamTaskStatusSet;
    }

    public CriteriaSet<TaskStatus> obtainStreamTaskStatusSet() {
        if (streamTaskStatusSet == null) {
            streamTaskStatusSet = new CriteriaSet<>();
        }
        return streamTaskStatusSet;
    }

    public EntityIdSet<Node> getNodeIdSet() {
        return nodeIdSet;
    }

    public EntityIdSet<Node> obtainNodeIdSet() {
        if (nodeIdSet == null) {
            nodeIdSet = new EntityIdSet<>();
        }
        return nodeIdSet;
    }

    public EntityIdSet<PipelineEntity> getPipelineIdSet() {
        return pipelineIdSet;
    }

    public EntityIdSet<PipelineEntity> obtainPipelineIdSet() {
        if (pipelineIdSet == null) {
            pipelineIdSet = new EntityIdSet<>();
        }
        return pipelineIdSet;
    }

    public EntityIdSet<StreamTask> getStreamTaskIdSet() {
        return streamTaskIdSet;
    }

    public EntityIdSet<StreamProcessorFilter> obtainStreamProcessorFilterIdSet() {
        if (streamProcessorFilterIdSet == null) {
            streamProcessorFilterIdSet = new EntityIdSet<>();
        }
        return streamProcessorFilterIdSet;
    }

    public EntityIdSet<StreamProcessorFilter> getStreamProcessorFilterIdSet() {
        return streamProcessorFilterIdSet;
    }

    public EntityIdSet<StreamTask> obtainStreamTaskIdSet() {
        if (streamTaskIdSet == null) {
            streamTaskIdSet = new EntityIdSet<>();
        }
        return streamTaskIdSet;
    }

    public FindStreamCriteria getFindStreamCriteria() {
        return findStreamCriteria;
    }

    public FindStreamCriteria obtainFindStreamCriteria() {
        if (findStreamCriteria == null) {
            findStreamCriteria = new FindStreamCriteria();
        }
        return findStreamCriteria;
    }

    public void copyFrom(final FindStreamTaskCriteria other) {
        if (other == null) {
            return;
        }
        super.copyFrom(other);

        this.obtainStreamTaskStatusSet().copyFrom(other.obtainStreamTaskStatusSet());
        this.obtainNodeIdSet().copyFrom(other.obtainNodeIdSet());
        this.obtainStreamTaskIdSet().copyFrom(other.obtainStreamTaskIdSet());
        this.obtainFindStreamCriteria().copyFrom(other.obtainFindStreamCriteria());

    }

    public Long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final Long createMs) {
        this.createMs = createMs;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.appendSuper(super.hashCode());
        builder.append(getOrderBy());
        builder.append(streamTaskStatusSet);
        builder.append(nodeIdSet);
        builder.append(streamTaskIdSet);
        builder.append(streamProcessorFilterIdSet);
        builder.append(findStreamCriteria);
        builder.append(createMs);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof FindStreamTaskCriteria)) {
            return false;
        }

        final FindStreamTaskCriteria other = (FindStreamTaskCriteria) obj;

        final EqualsBuilder builder = new EqualsBuilder();
        builder.appendSuper(super.equals(obj));
        builder.append(this.getOrderBy(), other.getOrderBy());
        builder.append(this.obtainFindStreamCriteria(), other.obtainFindStreamCriteria());
        builder.append(this.obtainNodeIdSet(), other.obtainNodeIdSet());
        builder.append(this.obtainStreamTaskIdSet(), other.obtainStreamTaskIdSet());
        builder.append(this.obtainStreamProcessorFilterIdSet(), other.obtainStreamProcessorFilterIdSet());
        builder.append(this.obtainFindStreamCriteria(), other.obtainFindStreamCriteria());
        builder.append(this.createMs, other.createMs);

        return builder.isEquals();
    }
}
