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

package stroom.processor.shared;

import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.meta.shared.Status;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.HasIsConstrained;
import stroom.util.shared.Period;
import stroom.util.shared.StringCriteria;

/**
 * <p>
 * API to find streams that have not yet been processed.
 * </p>
 */
public final class FindProcessorFilterTaskCriteria extends BaseCriteria implements HasIsConstrained {
    public static final int SUMMARY_POS_PIPELINE = 0;
    public static final int SUMMARY_POS_FEED = 1;
    public static final int SUMMARY_POS_PRIORITY = 2;
    public static final int SUMMARY_POS_STATUS = 3;
    public static final String FIELD_CREATE_TIME = "Created";
    public static final String FIELD_START_TIME = "Start Time";
    public static final String FIELD_END_TIME_DATE = "End Time";
    public static final String FIELD_FEED_NAME = MetaFieldNames.FEED_NAME;
    public static final String FIELD_PRIORITY = "Priority";
    public static final String FIELD_PIPELINE_UUID = MetaFieldNames.PIPELINE_UUID;
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_COUNT = "Count";
    public static final String FIELD_NODE = "Node";
    public static final String FIELD_POLL_AGE = "Poll Age";
    private static final long serialVersionUID = 5031936548305751911L;
    /**
     * Look for stream tasks with a certain status.
     */
    private CriteriaSet<TaskStatus> taskStatusSet;

    /**
     * Find with a key
     */
    private CriteriaSet<Long> metaIdSet = null;

    /**
     * Find with a key
     */
    private StringCriteria nodeNameCriteria = null;

    /**
     * Find with a key
     */
    private CriteriaSet<Long> processorFilterTaskIdSet = null;

    /**
     * Find with a key
     */
    private CriteriaSet<Integer> processorFilterIdSet = null;

//    /**
//     * Find with a key
//     */
//    private CriteriaSet<String> feedNameSet = null;
//
//    /**
//     * Find with a key
//     */
//    private CriteriaSet<String> streamTypeNameSet = null;

    /**
     * Find with a key
     */
    private StringCriteria pipelineUuidCriteria = null;

    private CriteriaSet<Status> statusSet;

    private Period createPeriod;
//    private Period effectivePeriod;

    /**
     * Create at a particular time
     */
    private Long createMs = null;

    public static FindProcessorFilterTaskCriteria createWithStream(final Meta meta) {
        final FindProcessorFilterTaskCriteria criteria = new FindProcessorFilterTaskCriteria();
        criteria.obtainMetaIdSet().add(meta.getId());
        return criteria;
    }

    @Override
    public boolean isConstrained() {
        if (taskStatusSet != null && taskStatusSet.isConstrained()) {
            return true;
        }
        if (metaIdSet != null && metaIdSet.isConstrained()) {
            return true;
        }
        if (nodeNameCriteria != null && nodeNameCriteria.isConstrained()) {
            return true;
        }
        if (processorFilterTaskIdSet != null && processorFilterTaskIdSet.isConstrained()) {
            return true;
        }
        if (processorFilterIdSet != null && processorFilterIdSet.isConstrained()) {
            return true;
        }
//        if (feedNameSet != null && feedNameSet.isConstrained()) {
//            return true;
//        }
//        if (streamTypeNameSet != null && streamTypeNameSet.isConstrained()) {
//            return true;
//        }
        if (pipelineUuidCriteria != null && pipelineUuidCriteria.isConstrained()) {
            return true;
        }
        if (statusSet != null && statusSet.isConstrained()) {
            return true;
        }
        if (createPeriod != null && createPeriod.isConstrained()) {
            return true;
        }
        return false;
    }

    public CriteriaSet<TaskStatus> getTaskStatusSet() {
        return taskStatusSet;
    }

    public void setTaskStatusSet(final CriteriaSet<TaskStatus> taskStatusSet) {
        this.taskStatusSet = taskStatusSet;
    }

    public CriteriaSet<TaskStatus> obtainTaskStatusSet() {
        if (taskStatusSet == null) {
            taskStatusSet = new CriteriaSet<>();
        }
        return taskStatusSet;
    }

    public CriteriaSet<Long> getMetaIdSet() {
        return metaIdSet;
    }

    public CriteriaSet<Long> obtainMetaIdSet() {
        if (metaIdSet == null) {
            metaIdSet = new CriteriaSet<>();
        }
        return metaIdSet;
    }

    public StringCriteria getNodeNameCriteria() {
        return nodeNameCriteria;
    }

    public StringCriteria obtainNodeNameCriteria() {
        if (nodeNameCriteria == null) {
            nodeNameCriteria = new StringCriteria();
        }
        return nodeNameCriteria;
    }

    public StringCriteria getPipelineUuidCriteria() {
        return pipelineUuidCriteria;
    }

    public StringCriteria obtainPipelineUuidCriteria() {
        if (pipelineUuidCriteria == null) {
            pipelineUuidCriteria = new StringCriteria();
        }
        return pipelineUuidCriteria;
    }

//    public CriteriaSet<String> getFeedNameSet() {
//        return feedNameSet;
//    }
//
//    public CriteriaSet<String> obtainFeedNameSet() {
//        if (feedNameSet == null) {
//            feedNameSet = new CriteriaSet<>();
//        }
//        return feedNameSet;
//    }
//
//    public CriteriaSet<String> getStreamTypeNameSet() {
//        return streamTypeNameSet;
//    }
//
//    public CriteriaSet<String> obtainStreamTypeNameSet() {
//        if (streamTypeNameSet == null) {
//            streamTypeNameSet = new CriteriaSet<>();
//        }
//        return streamTypeNameSet;
//    }

    public CriteriaSet<Long> getProcessorFilterTaskIdSet() {
        return processorFilterTaskIdSet;
    }

    public CriteriaSet<Integer> obtainProcessorFilterIdSet() {
        if (processorFilterIdSet == null) {
            processorFilterIdSet = new CriteriaSet<>();
        }
        return processorFilterIdSet;
    }

    public CriteriaSet<Integer> getProcessorFilterIdSet() {
        return processorFilterIdSet;
    }

    public CriteriaSet<Long> obtainProcessorFilterTaskIdSet() {
        if (processorFilterTaskIdSet == null) {
            processorFilterTaskIdSet = new CriteriaSet<>();
        }
        return processorFilterTaskIdSet;
    }

    public CriteriaSet<Status> getStatusSet() {
        return statusSet;
    }

    public CriteriaSet<Status> obtainStatusSet() {
        if (statusSet == null) {
            statusSet = new CriteriaSet<>();
        }
        return statusSet;
    }

    //    public FindStreamCriteria getFindStreamCriteria() {
//        return findStreamCriteria;
//    }
//
//    public FindStreamCriteria obtainFindStreamCriteria() {
//        if (findStreamCriteria == null) {
//            findStreamCriteria = new FindStreamCriteria();
//        }
//        return findStreamCriteria;
//    }

//    public void copyFrom(final FindProcessorFilterTaskCriteria other) {
//        if (other == null) {
//            return;
//        }
//        super.copyFrom(other);
//
//        this.obtainTaskStatusSet().copyFrom(other.obtainTaskStatusSet());
//        this.obtainNodeIdSet().copyFrom(other.obtainNodeIdSet());
//        this.obtainStreamTaskIdSet().copyFrom(other.obtainStreamTaskIdSet());
//        this.obtainFindStreamCriteria().copyFrom(other.obtainFindStreamCriteria());
//
//    }

    public Long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final Long createMs) {
        this.createMs = createMs;
    }

    public Period getCreatePeriod() {
        return createPeriod;
    }

    public void setCreatePeriod(final Period createPeriod) {
        this.createPeriod = createPeriod;
    }

    public Period obtainCreatePeriod() {
        if (createPeriod == null) {
            createPeriod = new Period();
        }
        return createPeriod;

    }

//    public Period getEffectivePeriod() {
//        return effectivePeriod;
//    }
//
//    public void setEffectivePeriod(final Period effectivePeriod) {
//        this.effectivePeriod = effectivePeriod;
//    }
//
//    public Period obtainEffectivePeriod() {
//        if (effectivePeriod == null) {
//            effectivePeriod = new Period();
//        }
//        return effectivePeriod;
//    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof FindProcessorFilterTaskCriteria)) return false;
        if (!super.equals(o)) return false;

        final FindProcessorFilterTaskCriteria that = (FindProcessorFilterTaskCriteria) o;

        if (taskStatusSet != null ? !taskStatusSet.equals(that.taskStatusSet) : that.taskStatusSet != null)
            return false;
        if (metaIdSet != null ? !metaIdSet.equals(that.metaIdSet) : that.metaIdSet != null) return false;
        if (nodeNameCriteria != null ? !nodeNameCriteria.equals(that.nodeNameCriteria) : that.nodeNameCriteria != null)
            return false;
        if (processorFilterTaskIdSet != null ? !processorFilterTaskIdSet.equals(that.processorFilterTaskIdSet) : that.processorFilterTaskIdSet != null)
            return false;
        if (processorFilterIdSet != null ? !processorFilterIdSet.equals(that.processorFilterIdSet) : that.processorFilterIdSet != null)
            return false;
        if (createMs != null ? !createMs.equals(that.createMs) : that.createMs != null) return false;
//        if (feedNameSet != null ? feedNameSet.equals(that.feedNameSet) : that.feedNameSet == null) return false;
//        if (streamTypeNameSet != null ? !streamTypeNameSet.equals(that.streamTypeNameSet) : that.streamTypeNameSet != null)
//            return false;
        if (pipelineUuidCriteria != null ? pipelineUuidCriteria.equals(that.pipelineUuidCriteria) : that.pipelineUuidCriteria == null) return false;
        if (statusSet != null ? statusSet.equals(that.statusSet) : that.statusSet == null) return false;
        if (createPeriod != null ? createPeriod.equals(that.createPeriod) : that.createPeriod == null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (taskStatusSet != null ? taskStatusSet.hashCode() : 0);
        result = 31 * result + (metaIdSet != null ? metaIdSet.hashCode() : 0);
        result = 31 * result + (nodeNameCriteria != null ? nodeNameCriteria.hashCode() : 0);
        result = 31 * result + (processorFilterTaskIdSet != null ? processorFilterTaskIdSet.hashCode() : 0);
        result = 31 * result + (processorFilterIdSet != null ? processorFilterIdSet.hashCode() : 0);
        result = 31 * result + (createMs != null ? createMs.hashCode() : 0);
//        result = 31 * result + (feedNameSet != null ? feedNameSet.hashCode() : 0);
//        result = 31 * result + (streamTypeNameSet != null ? streamTypeNameSet.hashCode() : 0);
        result = 31 * result + (pipelineUuidCriteria != null ? pipelineUuidCriteria.hashCode() : 0);
        result = 31 * result + (statusSet != null ? statusSet.hashCode() : 0);
        result = 31 * result + (createPeriod != null ? createPeriod.hashCode() : 0);
        return result;
    }
}
