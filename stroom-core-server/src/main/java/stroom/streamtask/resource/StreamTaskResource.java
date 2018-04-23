
/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.streamtask.resource;

import com.codahale.metrics.health.HealthCheck;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.NotImplementedException;
import stroom.entity.shared.BaseResultList;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.util.HasHealthCheck;
import stroom.util.shared.SharedObject;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Api(
        value = "stream task - /v1",
        description = "Stroom Stream Task API")
@Path("/streamtasks/v1")
@Produces(MediaType.APPLICATION_JSON)
public class StreamTaskResource implements HasHealthCheck {

    private final StreamProcessorFilterService streamProcessorFilterService;
    private final StreamProcessorService streamProcessorService;
    private final SecurityContext securityContext;
    private final Security security;

    @Inject
    public StreamTaskResource(
            StreamProcessorFilterService streamProcessorFilterService,
            StreamProcessorService streamProcessorService,
            SecurityContext securityContext,
            Security security) {
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.streamProcessorService = streamProcessorService;
        this.securityContext = securityContext;
        this.security = security;
    }

    @GET
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "TODO",
            response = Response.class)
    // TODO params:
    //      1. pipelineId
    //      2. ?
    public Response getAll(@QueryParam("pipelineId") Long pipelineId) {
        //TODO: needs JWT-based authentication.
//        return security.secureResult(PermissionNames.MANAGE_PROCESSORS_PERMISSION, () -> {

        final FindStreamProcessorFilterCriteria criteria = new FindStreamProcessorFilterCriteria();
//        final FindStreamProcessorCriteria criteriaRoot = new FindStreamProcessorCriteria();
        if (pipelineId != null) {
            criteria.obtainPipelineIdSet().add(pipelineId);
//            criteriaRoot.obtainPipelineIdSet().add(pipelineId);
        }

        //TODO: check this for JWT?
        // If the user is not an admin then only show them filters that were created by them.
        if (!securityContext.isAdmin()) {
            criteria.setCreateUser(securityContext.getUserId());
        }

        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
//        criteriaRoot.getFetchSet().add(PipelineEntity.ENTITY_TYPE);

//        final List<SharedObject> values = find(criteria, criteriaRoot);
        final List<StreamTask> values = find(criteria);

        return Response.ok(values).build();
//        return BaseResultList.createUnboundedList(values);
//        });
    }

    @Override
    public HealthCheck.Result getHealth() {
        throw new NotImplementedException("public HealthCheck.Result getHealth()");
    }

    @Override
    public HealthCheck getHealthCheck() {
        throw new NotImplementedException("public HealthCheck getHealthCheck()");
    }

//    private List<SharedObject> find(final FindStreamProcessorFilterCriteria criteria, final FindStreamProcessorCriteria criteriaRoot ){
    private List<StreamTask> find(final FindStreamProcessorFilterCriteria criteria){
        final List<SharedObject> values = new ArrayList<>();

        //NB: We don't care about the following because we're not organising like this any more. We might need
        // to run the query though, to get the data we need.
//        final BaseResultList<StreamProcessor> streamProcessors = streamProcessorService.find(criteriaRoot);

        final BaseResultList<StreamProcessorFilter> streamProcessorFilters = streamProcessorFilterService
                .find(criteria);

        // Get unique processors.
//        final Set<StreamProcessor> processors = new HashSet<>(streamProcessors);

//        final List<StreamProcessor> sorted = new ArrayList<>(processors);
//        sorted.sort((o1, o2) -> {
//            if (o1.getPipeline() != null && o2.getPipeline() != null) {
//                return o1.getPipeline().getName().compareTo(o2.getPipeline().getName());
//            }
//            if (o1.getPipeline() != null) {
//                return -1;
//            }
//            if (o2.getPipeline() != null) {
//                return 1;
//            }
//            return o1.compareTo(o2);
//        });

//        for (final StreamProcessor streamProcessor : sorted) {
//            final Expander processorExpander = new Expander(0, false, false);
//            final StreamProcessorRow streamProcessorRow = new StreamProcessorRow(processorExpander,
//                    streamProcessor);
//            values.add(streamProcessorRow);
//
//            Set<SharedObject> tempGetExpandedRows = new HashSet<>();
//            boolean tempIsRowExpanded = true;
//            // If the job row is open then add child rows.
////                if (action.getExpandedRows() == null || action.isRowExpanded(streamProcessorRow)) {
//            if (tempGetExpandedRows == null || tempIsRowExpanded) {
//                processorExpander.setExpanded(true);
//
//                // Add filters.
//                for (final StreamProcessorFilter streamProcessorFilter : streamProcessorFilters) {
//                    if (streamProcessor.equals(streamProcessorFilter.getStreamProcessor())) {
//                        final StreamProcessorFilterRow streamProcessorFilterRow = new StreamProcessorFilterRow(
//                                streamProcessorFilter);
//                        values.add(streamProcessorFilterRow);
//                    }
//                }
//            }
//        }

        List<StreamTask> streamTasks = new ArrayList<>();
        for (StreamProcessorFilter filter : streamProcessorFilters.getValues()){
            StreamTask.StreamTaskBuilder builder = StreamTask.StreamTaskBuilder.aStreamTask();

            // Indented to make the source easier to read

            builder
//                    .withFilterName(     filter.getStreamProcessor().get) //?
                    .withPipelineName(   filter.getStreamProcessor().getPipeline().getName())
                    .withPipelineId(     filter.getStreamProcessor().getPipeline().getId())
                    .withPriority(       filter.getPriority())
                    .withEnabled(        filter.isEnabled())
                    .withFilterId(       filter.getId())
                    .withCreateUser(     filter.getCreateUser())
                    .withCreatedOn(      filter.getCreateTime())
                    .withUpdateUser(     filter.getUpdateUser())
                    .withUpdatedOn(      filter.getUpdateTime());

            if(filter.getStreamProcessorFilterTracker() != null) {
                builder.withTrackerMs(filter.getStreamProcessorFilterTracker().getStreamCreateMs())
                        .withTrackerPercent(filter.getStreamProcessorFilterTracker().getTrackerStreamCreatePercentage())
                        .withLastPollAge(filter.getStreamProcessorFilterTracker().getLastPollAge())
                        .withTaskCount(filter.getStreamProcessorFilterTracker().getLastPollTaskCount())
                        .withMinStreamId(filter.getStreamProcessorFilterTracker().getMinStreamId())
                        .withMinEventId(filter.getStreamProcessorFilterTracker().getMinEventId());
            }

            StreamTask streamTask = builder.build();
            streamTasks.add(streamTask);
        }

        return streamTasks;
    }
}
