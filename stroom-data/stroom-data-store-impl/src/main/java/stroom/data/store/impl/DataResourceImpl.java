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

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataResource;
import stroom.data.shared.UploadDataRequest;
import stroom.meta.shared.FindMetaCriteria;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;

class DataResourceImpl implements DataResource {
    private final Provider<DataService> dataServiceProvider;
    private final StreamEventLog streamEventLog;

    @Inject
    DataResourceImpl(final Provider<DataService> dataServiceProvider,
                     final StreamEventLog streamEventLog) {
        this.dataServiceProvider = dataServiceProvider;
        this.streamEventLog = streamEventLog;
    }

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        ResourceGeneration resourceGeneration;
        try {
            resourceGeneration = dataServiceProvider.get().download(criteria);
            streamEventLog.exportStream(criteria, null);

        } catch (final RuntimeException e) {
            streamEventLog.exportStream(criteria, e);
            throw EntityServiceExceptionUtil.create(e);
        }

        return resourceGeneration;
    }

    @Override
    public ResourceKey upload(final UploadDataRequest request) {
        ResourceKey resourceKey;

        try {
            resourceKey = dataServiceProvider.get().upload(request);
            streamEventLog.importStream(request.getFeedName(), request.getFileName(), null);
        } catch (final RuntimeException e) {
            streamEventLog.importStream(request.getFeedName(), request.getFileName(), e);
            throw EntityServiceExceptionUtil.create(e);
        }

        return resourceKey;
    }

    @Override
    public List<DataInfoSection> info(final long id) {
        List<DataInfoSection> result;
        try {
            result = dataServiceProvider.get().info(id);
//            streamEventLog.viewStream(streamId + ":" + streamsOffsetrequest.getFeedName(), request.getFileName(), null);
        } catch (final RuntimeException e) {
//            streamEventLog.importStream(request.getFeedName(), request.getFileName(), e);
            throw EntityServiceExceptionUtil.create(e);
        }

        return result;
    }

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