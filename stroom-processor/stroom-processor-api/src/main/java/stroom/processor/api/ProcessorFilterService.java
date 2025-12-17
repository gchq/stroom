/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.api;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.util.shared.HasFetchByUuid;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface ProcessorFilterService
        extends HasIntCrud<ProcessorFilter>, HasFetchByUuid<ProcessorFilter> {

    ProcessorFilter create(CreateProcessFilterRequest request);

    ProcessorFilter create(Processor processor,
                           CreateProcessFilterRequest request);

    List<ReprocessDataInfo> reprocess(CreateProcessFilterRequest request);

    ProcessorFilter importFilter(ProcessorFilter existingProcessorFilter,
                                 Processor processor,
                                 DocRef processorFilterDocRef,
                                 CreateProcessFilterRequest request);

    ResultPage<ProcessorFilter> find(ExpressionCriteria criteria);

    ResultPage<ProcessorListRow> find(FetchProcessorRequest request);

    ResultPage<ProcessorFilter> find(DocRef pipelineDocRef);

    void setPriority(Integer id, Integer priority);

    void setMaxProcessingTasks(Integer id, Integer maxProcessingTasks);

    void setEnabled(Integer id, Boolean enabled);

    ProcessorFilterRow getRow(ProcessorFilter processorFilter);

    Optional<String> getPipelineName(ProcessorType processorType, String uuid);

    ProcessorFilter restore(DocRef processorFilterDocRef, final boolean resetTracker);
}
