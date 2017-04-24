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

package stroom.pipeline.processor.server;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.pipeline.processor.client.presenter.StreamProcessorFilterRow;
import stroom.pipeline.processor.client.presenter.StreamProcessorRow;
import stroom.pipeline.processor.shared.FetchProcessorAction;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.Expander;
import stroom.util.shared.SharedObject;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = FetchProcessorAction.class)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class FetchProcessorHandler extends AbstractTaskHandler<FetchProcessorAction, ResultList<SharedObject>> {
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private StreamProcessorService streamProcessorService;
    @Resource
    private SecurityContext securityContext;

    @Override
    public ResultList<SharedObject> exec(final FetchProcessorAction action) {
        final List<SharedObject> values = new ArrayList<>();

        final FindStreamProcessorFilterCriteria criteria = new FindStreamProcessorFilterCriteria();
        final FindStreamProcessorCriteria criteriaRoot = new FindStreamProcessorCriteria();
        if (action.getPipelineId() != null) {
            criteria.obtainPipelineIdSet().add(action.getPipelineId());
            criteriaRoot.obtainPipelineIdSet().add(action.getPipelineId());
        }
        if (action.getFolderId() != null) {
            criteria.obtainFolderIdSet().add(action.getFolderId());
            criteriaRoot.obtainFolderIdSet().add(action.getFolderId());
        }

        // If the user is not an admin then only show them filters that were created by them.
        if (!securityContext.isAdmin()) {
            criteria.setCreateUser(securityContext.getUserId());
        }

        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
        criteriaRoot.getFetchSet().add(PipelineEntity.ENTITY_TYPE);

        final BaseResultList<StreamProcessor> streamProcessors = streamProcessorService.find(criteriaRoot);

        final BaseResultList<StreamProcessorFilter> streamProcessorFilters = streamProcessorFilterService
                .find(criteria);

        // Get unique processors.
        final Set<StreamProcessor> processors = new HashSet<>();
        processors.addAll(streamProcessors);

        final List<StreamProcessor> sorted = new ArrayList<>(processors);
        Collections.sort(sorted, (o1, o2) -> {
            if (o1.getPipeline() != null && o2.getPipeline() != null) {
                return o1.getPipeline().getName().compareTo(o2.getPipeline().getName());
            }
            if (o1.getPipeline() != null) {
                return -1;
            }
            if (o2.getPipeline() != null) {
                return 1;
            }
            return o1.compareTo(o2);
        });

        for (final StreamProcessor streamProcessor : sorted) {
            final Expander processorExpander = new Expander(0, false, false);
            final StreamProcessorRow streamProcessorRow = new StreamProcessorRow(processorExpander,
                    streamProcessor);
            values.add(streamProcessorRow);

            // If the job row is open then add child rows.
            if (action.getExpandedRows() == null || action.isRowExpanded(streamProcessorRow)) {
                processorExpander.setExpanded(true);

                // Add filters.
                for (final StreamProcessorFilter streamProcessorFilter : streamProcessorFilters) {
                    if (streamProcessor.equals(streamProcessorFilter.getStreamProcessor())) {
                        final StreamProcessorFilterRow streamProcessorFilterRow = new StreamProcessorFilterRow(
                                streamProcessorFilter);
                        values.add(streamProcessorFilterRow);
                    }
                }
            }
        }

        return BaseResultList.createUnboundedList(values);
    }
}
