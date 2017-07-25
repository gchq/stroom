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
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;

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

    public static final String FIELD_CREATE_TIME = "Created";
    public static final String FIELD_START_TIME = "Start Time";
    public static final String FIELD_END_TIME_DATE = "End Time";
    public static final String FIELD_FEED_NAME = "Feed";
    public static final String FIELD_PRIORITY = "Priority";
    public static final String FIELD_PIPELINE_NAME = "Pipeline";
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_COUNT = "Count";
    public static final String FIELD_NODE = "Node";

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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof FindStreamTaskCriteria)) return false;
        if (!super.equals(o)) return false;

        final FindStreamTaskCriteria that = (FindStreamTaskCriteria) o;

        if (streamTaskStatusSet != null ? !streamTaskStatusSet.equals(that.streamTaskStatusSet) : that.streamTaskStatusSet != null)
            return false;
        if (nodeIdSet != null ? !nodeIdSet.equals(that.nodeIdSet) : that.nodeIdSet != null) return false;
        if (streamTaskIdSet != null ? !streamTaskIdSet.equals(that.streamTaskIdSet) : that.streamTaskIdSet != null)
            return false;
        if (streamProcessorFilterIdSet != null ? !streamProcessorFilterIdSet.equals(that.streamProcessorFilterIdSet) : that.streamProcessorFilterIdSet != null)
            return false;
        if (pipelineIdSet != null ? !pipelineIdSet.equals(that.pipelineIdSet) : that.pipelineIdSet != null)
            return false;
        if (createMs != null ? !createMs.equals(that.createMs) : that.createMs != null) return false;
        return findStreamCriteria != null ? findStreamCriteria.equals(that.findStreamCriteria) : that.findStreamCriteria == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (streamTaskStatusSet != null ? streamTaskStatusSet.hashCode() : 0);
        result = 31 * result + (nodeIdSet != null ? nodeIdSet.hashCode() : 0);
        result = 31 * result + (streamTaskIdSet != null ? streamTaskIdSet.hashCode() : 0);
        result = 31 * result + (streamProcessorFilterIdSet != null ? streamProcessorFilterIdSet.hashCode() : 0);
        result = 31 * result + (pipelineIdSet != null ? pipelineIdSet.hashCode() : 0);
        result = 31 * result + (createMs != null ? createMs.hashCode() : 0);
        result = 31 * result + (findStreamCriteria != null ? findStreamCriteria.hashCode() : 0);
        return result;
    }
}
