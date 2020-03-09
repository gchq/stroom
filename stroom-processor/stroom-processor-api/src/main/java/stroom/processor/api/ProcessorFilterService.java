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

package stroom.processor.api;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.FindMetaCriteria;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.util.List;

public interface ProcessorFilterService extends HasIntCrud<ProcessorFilter> {
    ProcessorFilter create(final DocRef pipelineRef,
                           final QueryData queryData,
                           final int priority,
                           final boolean enabled);

    ProcessorFilter create(final Processor processor,
                           final QueryData queryData,
                           final int priority,
                           final boolean enabled);

    ResultPage<ProcessorFilter> find(ExpressionCriteria criteria);

    ProcessorListRowResultPage find(FetchProcessorRequest request);

    void setPriority(Integer id, Integer priority);

    void setEnabled(Integer id, Boolean enabled);

    List<ReprocessDataInfo> reprocess(FindMetaCriteria criteria);
}
