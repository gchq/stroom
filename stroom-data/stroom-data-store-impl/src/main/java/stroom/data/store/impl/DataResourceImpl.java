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
 */

package stroom.data.store.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.data.shared.DataResource;
import stroom.data.shared.UploadDataRequest;
import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskManager;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.file.Path;
import java.util.ArrayList;

class DataResourceImpl implements DataResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataResourceImpl.class);

    private final DataFetcher dataFetcher;
    private final ResourceStore resourceStore;
    private final TaskManager taskManager;
    private final StreamEventLog streamEventLog;
    private final SecurityContext securityContext;

    @Inject
    DataResourceImpl(final Store streamStore,
                     final FeedProperties feedProperties,
                     final Provider<FeedHolder> feedHolderProvider,
                     final Provider<MetaDataHolder> metaDataHolderProvider,
                     final Provider<PipelineHolder> pipelineHolderProvider,
                     final Provider<MetaHolder> metaHolderProvider,
                     final PipelineStore pipelineStore,
                     final Provider<PipelineFactory> pipelineFactoryProvider,
                     final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                     final PipelineDataCache pipelineDataCache,
                     final PipelineScopeRunnable pipelineScopeRunnable,
                     final ResourceStore resourceStore,
                     final TaskManager taskManager,
                     final StreamEventLog streamEventLog,
                     final SecurityContext securityContext) {
        dataFetcher = new DataFetcher(streamStore,
                feedProperties,
                feedHolderProvider,
                metaDataHolderProvider,
                pipelineHolderProvider,
                metaHolderProvider,
                pipelineStore,
                pipelineFactoryProvider,
                errorReceiverProxyProvider,
                pipelineDataCache,
                streamEventLog,
                securityContext,
                pipelineScopeRunnable);
        this.resourceStore = resourceStore;
        this.taskManager = taskManager;
        this.streamEventLog = streamEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        return securityContext.secureResult(PermissionNames.EXPORT_DATA_PERMISSION, () -> {
            ResourceKey resourceKey;
            try {
                // Import file.
                resourceKey = resourceStore.createTempFile("StroomData.zip");
                final Path file = resourceStore.getTempFile(resourceKey);
                String fileName = file.getFileName().toString();
                int index = fileName.lastIndexOf(".");
                if (index != -1) {
                    fileName = fileName.substring(0, index);
                }

                final DataDownloadSettings settings = new DataDownloadSettings();
                taskManager.exec(new DataDownloadTask(criteria, file.getParent(), fileName, settings));

                streamEventLog.exportStream(criteria, null);

            } catch (final RuntimeException e) {
                streamEventLog.exportStream(criteria, e);
                throw EntityServiceExceptionUtil.create(e);
            }
            return new ResourceGeneration(resourceKey, new ArrayList<>());
        });
    }

    @Override
    public ResourceKey upload(final UploadDataRequest request) {
        return securityContext.secureResult(PermissionNames.IMPORT_DATA_PERMISSION, () -> {
            try {
                // Import file.
                final Path file = resourceStore.getTempFile(request.getKey());

                taskManager.exec(new StreamUploadTask(request.getFileName(), file, request.getFeedName(),
                        request.getStreamTypeName(), request.getEffectiveMs(), request.getMetaData()));

            } catch (final RuntimeException e) {
                throw e;//EntityServiceExceptionUtil.create(e);
            } finally {
                // Delete the import if it was successful
                resourceStore.deleteTempFile(request.getKey());
            }

            return request.getKey();
        });
    }

    @Override
    public Response fetchData(
            final @QueryParam("streamId") Long streamId,
            final @QueryParam("streamsOffset") Long streamsOffset,
            final @QueryParam("streamsLength") Long streamsLength,
            final @QueryParam("pageOffset") Long pageOffset,
            final @QueryParam("pageSize") Long pageSize) {

        final OffsetRange<Long> pageRange = new OffsetRange<>(pageOffset, pageSize);
        final OffsetRange<Long> streamRange = new OffsetRange<>(streamsOffset, streamsLength);

        final boolean isMarkerMode = true; // Used for organising errors but only relevant when the data is in fact errors
        final boolean showAsHtml = false; // Used for dashboards so false here.
        final Severity[] expandedSeverities = new Severity[]{Severity.INFO, Severity.WARNING, Severity.ERROR, Severity.FATAL_ERROR};

        //TODO Used for child streams. Needs implementing.
        String childStreamTypeName = null;

        return securityContext.secureResult(PermissionNames.VIEW_DATA_PERMISSION, () -> {
            dataFetcher.reset();
            AbstractFetchDataResult data = dataFetcher.getData(
                    streamId,
                    childStreamTypeName,
                    streamRange,
                    pageRange,
                    isMarkerMode,
                    null,
                    showAsHtml,
                    expandedSeverities);
            return Response.ok(data).build();

        });
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}