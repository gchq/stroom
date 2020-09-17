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

import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.ViewDataResource;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

class ViewDataResourceImpl implements ViewDataResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewDataResourceImpl.class);

    private final SecurityContext securityContext;
    private final DataFetcher dataFetcher;

    @Inject
    ViewDataResourceImpl(final Store streamStore,
                         final FeedProperties feedProperties,
                         final Provider<FeedHolder> feedHolderProvider,
                         final Provider<MetaDataHolder> metaDataHolderProvider,
                         final Provider<PipelineHolder> pipelineHolderProvider,
                         final Provider<MetaHolder> metaHolderProvider,
                         final PipelineStore pipelineStore,
                         final Provider<PipelineFactory> pipelineFactoryProvider,
                         final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                         final PipelineDataCache pipelineDataCache,
                         final StreamEventLog streamEventLog,
                         final SecurityContext securityContext,
                         final PipelineScopeRunnable pipelineScopeRunnable) {

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

        this.securityContext = securityContext;
    }


    @Override
    public AbstractFetchDataResult fetch(final FetchDataRequest request) {
        try {
            final String permissionName = request.getPipeline() != null
                    ? PermissionNames.VIEW_DATA_WITH_PIPELINE_PERMISSION
                    : PermissionNames.VIEW_DATA_PERMISSION;

            return securityContext.secureResult(permissionName, () ->
                    dataFetcher.getData(request));
        } catch (Exception e) {
            LOGGER.error(LogUtil.message("Error fetching data {}", request), e);
            throw e;
        }
    }

//    public Set<String> getChildStreamTypes(final long id, final long partNo) {
//        try {
//            final String permissionName = PermissionNames.VIEW_DATA_PERMISSION;
//
//            return securityContext.secureResult(permissionName, () -> {
//
//                Set<String> childTypes = dataFetcher.getAvailableChildStreamTypes(id, partNo);
//                LOGGER.info("childTypes {}", childTypes.stream().sorted().collect(Collectors.joining(",")));
//                return childTypes;
//            });
//        } catch (Exception e) {
//            LOGGER.error(LogUtil.message("Error fetching child stream types for id {}, part number {}",
//                    id, partNo), e);
//            throw e;
//        }
//    }

//    @Override
//    public AbstractFetchDataResult fetchData( final long streamId,
//                                              final Long streamsOffset,
//                                              final Long streamsLength,
//                                              final Long pageOffset,
//                                              final Long pageSize) {
//
//        final OffsetRange<Long> pageRange = new OffsetRange<>(pageOffset, pageSize);
//        final OffsetRange<Long> streamRange = new OffsetRange<>(streamsOffset, streamsLength);
//
//        final boolean isMarkerMode = true; // Used for organising errors but only relevant when the data is in fact errors
//        final boolean showAsHtml = false; // Used for dashboards so false here.
//        final Severity[] expandedSeverities = new Severity[]{Severity.INFO, Severity.WARNING, Severity.ERROR, Severity.FATAL_ERROR};
//
//        //TODO Used for child streams. Needs implementing.
//        String childStreamTypeName = null;
//
//        return securityContext.secureResult(PermissionNames.VIEW_DATA_PERMISSION, () -> {
//            dataFetcher.reset();
//            return dataFetcher.getData(
//                    streamId,
//                    childStreamTypeName,
//                    streamRange,
//                    pageRange,
//                    isMarkerMode,
//                    null,
//                    showAsHtml,
//                    expandedSeverities);
//        });
//    }
}