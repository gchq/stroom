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

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.shared.PermissionNames;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.streamtask.shared.FetchProcessorAction;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterRow;
import stroom.streamtask.shared.StreamProcessorRow;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.Expander;
import stroom.util.shared.SharedObject;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = FetchProcessorAction.class)
class FetchProcessorHandler extends AbstractTaskHandler<FetchProcessorAction, ResultList<SharedObject>> {
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final StreamProcessorService streamProcessorService;
    private final SecurityContext securityContext;
    private final Security security;

    @Inject
    FetchProcessorHandler(final StreamProcessorFilterService streamProcessorFilterService,
                          final StreamProcessorService streamProcessorService,
                          final SecurityContext securityContext,
                          final Security security) {
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.streamProcessorService = streamProcessorService;
        this.securityContext = securityContext;
        this.security = security;
    }

    @Override
    public ResultList<SharedObject> exec(final FetchProcessorAction action) {
        return security.secureResult(PermissionNames.MANAGE_PROCESSORS_PERMISSION, () -> {
            final List<SharedObject> values = new ArrayList<>();

            final FindStreamProcessorFilterCriteria criteria = new FindStreamProcessorFilterCriteria();
            final FindStreamProcessorCriteria criteriaRoot = new FindStreamProcessorCriteria();
            if (action.getPipelineId() != null) {
                criteria.obtainPipelineIdSet().add(action.getPipelineId());
                criteriaRoot.obtainPipelineIdSet().add(action.getPipelineId());
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
            final Set<StreamProcessor> processors = new HashSet<>(streamProcessors);

            final List<StreamProcessor> sorted = new ArrayList<>(processors);
            sorted.sort((o1, o2) -> {
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
        });
    }
}
