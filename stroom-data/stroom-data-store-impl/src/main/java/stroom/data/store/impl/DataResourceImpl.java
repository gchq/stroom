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
import stroom.data.shared.StreamTypeNames;
import stroom.data.shared.UploadDataRequest;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.Count;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.ReadWithLongId;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import event.logging.ComplexLoggedOutcome;
import event.logging.ExportEventAction;
import event.logging.ImportEventAction;
import event.logging.MultiObject;
import event.logging.ViewEventAction;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Set;

@AutoLogged
class DataResourceImpl implements DataResource, ReadWithLongId<List<DataInfoSection>> {

    private final Provider<DataService> dataServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    DataResourceImpl(final Provider<DataService> dataServiceProvider,
                     final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.dataServiceProvider = dataServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {

        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

        final ExportEventAction exportEventAction = ExportEventAction.builder()
                .withSource(MultiObject.builder()
                        .addCriteria(stroomEventLoggingService.convertExpressionCriteria(
                                "Meta",
                                criteria))
                        .build())
                .build();

        final ResourceGeneration resourceGeneration = stroomEventLoggingServiceProvider.get()
                .loggedResult(
                        StroomEventLoggingUtil.buildTypeId(this, "download"),
                        "Downloading stream data",
                        exportEventAction,
                        () -> {
                            try {
                                return dataServiceProvider.get().download(criteria);
                            } catch (final RuntimeException e) {
                                throw EntityServiceExceptionUtil.create(e);
                            }
                        });

        return resourceGeneration;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceKey upload(final UploadDataRequest request) {

        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

        final ResourceKey resourceKey = stroomEventLoggingService.loggedResult(
                StroomEventLoggingUtil.buildTypeId(this, "upload"),
                "Uploading stream data",
                ImportEventAction.builder()
                        .withSource(stroomEventLoggingService.convertToMulti(request))
                        .build(),
                () -> {
                    try {
                        return dataServiceProvider.get().upload(request);
                    } catch (final RuntimeException e) {
                        throw EntityServiceExceptionUtil.create(e);
                    }
                });

        return resourceKey;
    }

    @Override
    public List<DataInfoSection> viewInfo(final long id) {
        final List<DataInfoSection> result;
        try {
            result = dataServiceProvider.get().info(id);
        } catch (final RuntimeException e) {
            throw EntityServiceExceptionUtil.create(e);
        }

        return result;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AbstractFetchDataResult fetch(final FetchDataRequest request) {

        final String idStr = request.getSourceLocation() != null
                ? request.getSourceLocation().getIdentifierString()
                : "?";

        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

        return stroomEventLoggingService.loggedResult(
                StroomEventLoggingUtil.buildTypeId(this, "fetch"),
                "Viewing stream " + idStr,
                ViewEventAction.builder()
                        .build(),
                eventAction -> {
                    ComplexLoggedOutcome<AbstractFetchDataResult, ViewEventAction> outcome;
                    try {
                        // Do the fetch
                        final AbstractFetchDataResult fetchDataResult = dataServiceProvider.get()
                                .fetch(request);

                        outcome = ComplexLoggedOutcome.success(
                                fetchDataResult,
                                ViewEventAction.builder()
                                        .withObjects(stroomEventLoggingService.convert(fetchDataResult))
                                        .build());

                    } catch (ViewDataException vde) {
                        // Convert an ex into a fetch result
                        final AbstractFetchDataResult fetchDataResult = createErrorResult(vde);
                        outcome = ComplexLoggedOutcome.failure(
                                fetchDataResult,
                                ViewEventAction.builder()
                                        .withObjects(stroomEventLoggingService.convert(fetchDataResult))
                                        .build(),
                                vde.getMessage());
                    }
                    return outcome;
                },
                null);
    }


    @AutoLogged(OperationType.UNLOGGED) // Not an explicit user action
    @Override
    public Set<String> getChildStreamTypes(final long id, final long partNo) {

        final Set<String> childStreamTypes = dataServiceProvider.get()
                .getChildStreamTypes(id, partNo);

        return childStreamTypes;
    }

    private FetchDataResult createErrorResult(final ViewDataException viewDataException) {
        return new FetchDataResult(
                null,
                StreamTypeNames.RAW_EVENTS,
                null,
                viewDataException.getSourceLocation(),
                OffsetRange.zero(),
                Count.of(0L, true),
                Count.of(0L, true),
                0L,
                null,
                viewDataException.getMessage(),
                false,
                null);
    }

    @Override
    public List<DataInfoSection> read(final Long id) {
        // Provide the info when failing to read the info
        return viewInfo(id);
    }
}