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

import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.BaseCriteria;

public class FindProcessorFilterCriteria extends BaseCriteria {
    private static final long serialVersionUID = 1L;

    private ExpressionOperator expression;

    public FindProcessorFilterCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindProcessorFilterCriteria(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

//
//    /**
//     * The range you care about
//     */
//    private Range<Integer> priorityRange = null;
//    private Period lastPollPeriod = null;
//    private CriteriaSet<Integer> processorIdSet = null;
//    private StringCriteria pipelineUuidCriteria = null;
//    private Boolean processorEnabled = null;
//    private Boolean processorFilterEnabled = null;
//    private String createUser;
////    private String pipelineNameFilter;
////    private String status;
//
//    public FindProcessorFilterCriteria() {
//        // Default constructor necessary for GWT serialisation.
//    }
//
//    public FindProcessorFilterCriteria(final DocRef pipeline) {
//        obtainPipelineUuidCriteria().setString(pipeline.getUuid());
//    }
//
//    public FindProcessorFilterCriteria(final Processor processor) {
//        obtainStreamProcessorIdSet().add(processor.getId());
//    }
//
//    public Range<Integer> getPriorityRange() {
//        return priorityRange;
//    }
//
//    public void setPriorityRange(Range<Integer> priorityRange) {
//        this.priorityRange = priorityRange;
//    }
//
//    public Range<Integer> obtainPriorityRange() {
//        if (priorityRange == null) {
//            priorityRange = new Range<>();
//        }
//        return priorityRange;
//    }
//
//    public Period getLastPollPeriod() {
//        return lastPollPeriod;
//    }
//
//    public void setLastPollPeriod(Period lastPollPeriod) {
//        this.lastPollPeriod = lastPollPeriod;
//    }
//
//    public Period obtainLastPollPeriod() {
//        if (lastPollPeriod == null) {
//            lastPollPeriod = new Period();
//        }
//        return lastPollPeriod;
//    }
//
//    public StringCriteria getPipelineUuidCriteria() {
//        return pipelineUuidCriteria;
//    }
//
//    public void setPipelineUuidCriteria(final StringCriteria pipelineUuidCriteria) {
//        this.pipelineUuidCriteria = pipelineUuidCriteria;
//    }
//
//    public StringCriteria obtainPipelineUuidCriteria() {
//        if (pipelineUuidCriteria == null) {
//            pipelineUuidCriteria = new StringCriteria();
//        }
//        return pipelineUuidCriteria;
//    }
//
//    public CriteriaSet<Integer> getProcessorIdSet() {
//        return processorIdSet;
//    }
//
//    public void setProcessorIdSet(CriteriaSet<Integer> processorIdSet) {
//        this.processorIdSet = processorIdSet;
//    }
//
//    public CriteriaSet<Integer> obtainStreamProcessorIdSet() {
//        if (processorIdSet == null) {
//            processorIdSet = new CriteriaSet<>();
//        }
//        return processorIdSet;
//    }
//
//    public Boolean getProcessorEnabled() {
//        return processorEnabled;
//    }
//
//    public void setProcessorEnabled(Boolean processorEnabled) {
//        this.processorEnabled = processorEnabled;
//    }
//
//    public Boolean getProcessorFilterEnabled() {
//        return processorFilterEnabled;
//    }
//
//    public void setProcessorFilterEnabled(Boolean processorFilterEnabled) {
//        this.processorFilterEnabled = processorFilterEnabled;
//    }
//
//    public String getCreateUser() {
//        return createUser;
//    }
//
//    public void setCreateUser(String createUser) {
//        this.createUser = createUser;
//    }
//
////    public String getPipelineNameFilter() {
////        return pipelineNameFilter;
////    }
////
////    public void setPipelineNameFilter(String pipelineNameFilter) {
////        this.pipelineNameFilter = pipelineNameFilter;
////    }
////
////    public void setStatus(String status) {
////        this.status = status;
////    }
////
////    public String getStatus() {
////        return status;
////    }

}
