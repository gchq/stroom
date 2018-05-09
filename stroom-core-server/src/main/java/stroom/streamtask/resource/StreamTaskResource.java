
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
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Sort;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
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

    private final String FIELD_PROGRESS = "progress";

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
    public Response getAll(
           @NotNull @QueryParam("offset") Long offset,
           @QueryParam("pageSize") Integer pageSize,
           @NotNull @QueryParam("sortBy") String sortBy,
           @NotNull @QueryParam("sortDirection") String sortDirection) {
        // TODO: Authorisation

        final FindStreamProcessorFilterCriteria criteria = new FindStreamProcessorFilterCriteria();

        Sort.Direction direction;
        try {
             direction = Sort.Direction.valueOf(sortDirection.toUpperCase());
        }catch(IllegalArgumentException exception){
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid sortDirection field").build();
        }

        if(sortBy.equalsIgnoreCase(FindStreamTaskCriteria.FIELD_PIPELINE_NAME)
                || sortBy.equalsIgnoreCase(FindStreamTaskCriteria.FIELD_PRIORITY)){
            criteria.setSort(sortBy, direction, false);
        }
        else if(sortBy.equalsIgnoreCase(FIELD_PROGRESS)){
            // Sorting is done below -- this is here for completeness.
            // Percentage is a calculated variable so it has to be done after retrieval.
            // This poses a problem for paging and at the moment sorting by tracker % won't work correctly when paging.
        }
        else {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid sortBy field").build();
        }

        if(offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Page offset must be greater than 0").build();
        }

        if(pageSize != null && pageSize < 1){
            return Response.status(Response.Status.BAD_REQUEST).entity("Page size, if used, must be greater than 1").build();
        }

        criteria.setPageRequest(new PageRequest(offset, pageSize));

        if (!securityContext.isAdmin()) {
            criteria.setCreateUser(securityContext.getUserId());
        }

        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);

        final List<StreamTask> values = find(criteria);

        if(sortBy.equalsIgnoreCase(FIELD_PROGRESS)) {
            if(direction == Sort.Direction.ASCENDING) {
                values.sort(Comparator.comparingInt(StreamTask::getTrackerPercent));
            }
            else{
                values.sort(Comparator.comparingInt(StreamTask::getTrackerPercent).reversed());
            }
        }

        return Response.ok(values).build();
    }

    @Override
    public HealthCheck.Result getHealth() {
        throw new NotImplementedException("public HealthCheck.Result getHealth()");
    }

    @Override
    public HealthCheck getHealthCheck() {
        throw new NotImplementedException("public HealthCheck getHealthCheck()");
    }

    private List<StreamTask> find(final FindStreamProcessorFilterCriteria criteria){
        final BaseResultList<StreamProcessorFilter> streamProcessorFilters = streamProcessorFilterService
                .find(criteria);


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
                        .withMinEventId(filter.getStreamProcessorFilterTracker().getMinEventId())
                        .withStatus((filter.getStreamProcessorFilterTracker().getStatus()));
            }

            StreamTask streamTask = builder.build();
            streamTasks.add(streamTask);
        }

        return streamTasks;
    }
}
