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

package stroom.resource;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.FeedService;
import stroom.guice.PipelineScopeRunnable;
import stroom.logging.StreamEventLog;
import stroom.pipeline.DataFetcher;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.StreamStore;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorService;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "data - /v1",
        description = "Stroom Data API")
@Path("/data/v1")
@Produces(MediaType.APPLICATION_JSON)
public class DataResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataResource.class);

    private final DataFetcher dataFetcher;
    private final Security security;

    @Inject
    public DataResource(final StreamStore streamStore,
                        final FeedService feedService,
                        final StreamProcessorService streamProcessorService,
                        final Provider<FeedHolder> feedHolderProvider,
                        final Provider<MetaDataHolder> metaDataHolderProvider,
                        final Provider<PipelineHolder> pipelineHolderProvider,
                        final Provider<StreamHolder> streamHolderProvider,
                        final PipelineStore pipelineStore,
                        final Provider<PipelineFactory> pipelineFactoryProvider,
                        final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                        final PipelineDataCache pipelineDataCache,
                        final StreamEventLog streamEventLog,
                        final Security security,
                        final PipelineScopeRunnable pipelineScopeRunnable) {
        dataFetcher = new DataFetcher(streamStore,
                feedService,
                streamProcessorService,
                feedHolderProvider,
                metaDataHolderProvider,
                pipelineHolderProvider,
                streamHolderProvider,
                pipelineStore,
                pipelineFactoryProvider,
                errorReceiverProxyProvider,
                pipelineDataCache,
                streamEventLog,
                security,
                pipelineScopeRunnable);
        this.security = security;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetchData(
            final @NotNull @QueryParam("streamId") Long streamId,
            final @QueryParam("streamsOffset") Long streamsOffset,
            final @QueryParam("streamsLength") Long streamsLength,
            final @QueryParam("pageOffset") Long pageOffset,
            final @QueryParam("pageSize") Long pageSize){

        final OffsetRange<Long> pageRange = new OffsetRange<>(pageOffset, pageSize);
        final OffsetRange<Long> streamRange = new OffsetRange<>(streamsOffset, streamsLength);

        final boolean isMarkerMode = true; // Used for organising errors but only relevant when the data is in fact errors
        final boolean showAsHtml = false; // Used for dashboards so false here.
        final Severity[] expandedSeverities = new Severity[]{Severity.INFO, Severity.WARNING, Severity.ERROR, Severity.FATAL_ERROR};

        //TODO Used for child streams. Needs implementing.
        StreamType childStreamType = null;

        return security.secureResult(PermissionNames.VIEW_DATA_PERMISSION, () -> {
            dataFetcher.reset();
            AbstractFetchDataResult data = dataFetcher.getData(
                    streamId,
                    childStreamType,
                    streamRange,
                    pageRange,
                    isMarkerMode,
                    null,
                    showAsHtml,
                    expandedSeverities);
            return Response.ok(data).build();

        });
    }

}
