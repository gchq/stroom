/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.meta.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SelectionSummaryRequest;
import stroom.meta.shared.Status;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.DocumentPermission;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import event.logging.BaseObject;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.Data.Builder;
import event.logging.DeleteEventAction;
import event.logging.EventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.SimpleQuery;
import event.logging.UpdateEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AutoLogged
class MetaResourceImpl implements MetaResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaResourceImpl.class);
    private static final Pattern IN_LIST_DELIMITER_PATTERN = Pattern.compile(
            Pattern.quote(Condition.IN_CONDITION_DELIMITER));

    private final Provider<MetaService> metaServiceProvider;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    MetaResourceImpl(final Provider<MetaService> metaServiceProvider,
                     final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.metaServiceProvider = metaServiceProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @Override
    public Meta fetch(final long id) {
        return metaServiceProvider.get().getMeta(id);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Integer updateStatus(final UpdateStatusRequest request) {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final ExpressionOperator expression = Objects.requireNonNull(request.getCriteria().getExpression());
        final boolean isDelete = request.getNewStatus() == Status.DELETED;
        final DocumentPermission permission = isDelete
                ? DocumentPermission.DELETE
                : DocumentPermission.EDIT;
        String currentStatus = NullSafe.getOrElse(
                request.getCurrentStatus(), Status::getDisplayValue, "unspecified/unknown");
        final String newStatus = NullSafe.getOrElse(
                request.getNewStatus(), Status::getDisplayValue, "unspecified/unknown");

        final BaseObject baseObjectBefore;
        final BaseObject baseObjectAfter;
        final String description;
        final List<Data> dataItems = new ArrayList<>();

        if (expression.getChildren().size() == 1
            && expression.getChildren().getFirst() instanceof final ExpressionTerm term
            && term.hasCondition(Condition.EQUALS)) {
            final String metaIdStr = term.getValue();

            try {
                final long metaId = Long.parseLong(metaIdStr);
                final Meta meta = metaServiceProvider.get().getMeta(metaId, true);
                dataItems.add(Data.builder()
                        .withName("Feed")
                        .withValue(meta.getFeedName())
                        .build());
                dataItems.add(Data.builder()
                        .withName("Type")
                        .withValue(meta.getTypeName())
                        .build());
                currentStatus = meta.getStatus().getDisplayValue();
            } catch (final Exception e) {
                LOGGER.error(LogUtil.message("Error fetching meta for id '{}' for audit logging: {}",
                        metaIdStr, LogUtil.exceptionMessage(e), e));
            }

            baseObjectBefore = OtherObject.builder()
                    .withId(term.getValue())
                    .withState(currentStatus)
                    .build();
            baseObjectAfter = OtherObject.builder()
                    .withId(term.getValue())
                    .withState(newStatus)
                    .build();
            description = isDelete
                    ? "Delete stream with ID " + metaIdStr
                    : "Update the status of stream with ID " + metaIdStr
                      + " from " + currentStatus + " to " + newStatus;
        } else if (expression.getChildren().size() == 1
                   && expression.getChildren().getFirst() instanceof final ExpressionTerm term
                   && term.hasCondition(Condition.IN)) {
            final String streamIdsStr = term.getValue();
            final String[] idArr = NullSafe.getOrElse(
                    streamIdsStr,
                    IN_LIST_DELIMITER_PATTERN::split,
                    new String[0]);
            final int count = idArr.length;
            baseObjectBefore = null;
            baseObjectAfter = Criteria.builder()
                    .withQuery(Query.builder()
                            .withSimple(SimpleQuery.builder()
                                    .withInclude(streamIdsStr)
                                    .build())
                            .build())
                    .build();
            description = isDelete
                    ? "Delete " + count + " streams"
                    : "Update the status of " + count + " streams from " + currentStatus + " to " + newStatus;
            addSelectionSummaryDataItems(dataItems, request.getCriteria(), permission);
        } else {
            baseObjectBefore = null;
            baseObjectAfter = Criteria.builder()
                    .withQuery(StroomEventLoggingUtil.convertExpression(expression))
                    .build();
            description = isDelete
                    ? "Delete streams matching a criteria"
                    : "Update the status of streams matching a criteria from " + currentStatus + " to " + newStatus;
            addSelectionSummaryDataItems(dataItems, request.getCriteria(), permission);
        }

        final EventAction eventAction;
        if (isDelete) {
            final DeleteEventAction.Builder<Void> deleteBuilder = DeleteEventAction.builder()
                    .addData(dataItems);
            NullSafe.firstNonNull(baseObjectBefore, baseObjectAfter)
                    .ifPresent(deleteBuilder::withObjects);
            eventAction = deleteBuilder.build();
        } else {
            final UpdateEventAction.Builder<Void> updateBuilder = UpdateEventAction.builder()
                    .withAfter(MultiObject.builder()
                            .withObjects(baseObjectAfter)
                            .build())
                    .addData(dataItems);
            if (baseObjectBefore != null) {
                updateBuilder.withBefore(MultiObject.builder()
                        .withObjects(baseObjectBefore)
                        .build());
            }
            eventAction = updateBuilder.build();
        }

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateStatus"))
                .withDescription(description)
                .withDefaultEventAction(eventAction)
                .withSimpleLoggedResult(() -> metaServiceProvider.get().updateStatus(
                        request.getCriteria(),
                        request.getCurrentStatus(),
                        request.getNewStatus()))
                .getResultAndLog();
    }

    private void addSelectionSummaryDataItems(
            final List<Data> dataItems,
            final FindMetaCriteria criteria,
            final DocumentPermission permission) {
        try {
            final SelectionSummary selectionSummary = metaServiceProvider.get()
                    .getSelectionSummary(criteria, permission);
            if (selectionSummary != null) {
                addData(dataItems, "Count", selectionSummary.getItemCount());
                addData(dataItems, "FeedCount", selectionSummary.getFeedCount());
                addData(dataItems, "TypeCount", selectionSummary.getTypeCount());
                addData(dataItems, "ProcessorCount", selectionSummary.getProcessorCount());
                addData(dataItems, "PipelineCount", selectionSummary.getProcessorCount());
                addData(dataItems, "StatusCount", selectionSummary.getStatusCount());
                addDataValues(dataItems,
                        "Feeds",
                        "Feed",
                        selectionSummary.getFeedCount(),
                        selectionSummary.getDistinctFeeds());
                addDataValues(dataItems,
                        "Types",
                        "Type",
                        selectionSummary.getTypeCount(),
                        selectionSummary.getDistinctTypes());
                addDataValues(dataItems,
                        "Statuses",
                        "Status",
                        selectionSummary.getStatusCount(),
                        selectionSummary.getDistinctStatuses());

                final String minCreateTime = NullSafe.get(
                        selectionSummary.getAgeRange(),
                        Range::getFrom,
                        DateUtil::createNormalDateTimeString);
                addData(dataItems, "MinCreateTime", minCreateTime);

                final String maxCreateTime = NullSafe.get(
                        selectionSummary.getAgeRange(),
                        Range::getTo,
                        DateUtil::createNormalDateTimeString);
                addData(dataItems, "MaxCreateTime", maxCreateTime);
            }
        } catch (final Exception e) {
            LOGGER.error(LogUtil.message("Error building selection summary for criteria {}: {}",
                    criteria, LogUtil.exceptionMessage(e), e));
        }
    }

    private void addDataValues(final List<Data> list,
                               final String parentName,
                               final String itemName,
                               final long count,
                               final Set<String> values) {
        if (NullSafe.hasItems(values)) {
            final Builder<Void> builder = Data.builder()
                    .withName(parentName);
            values.stream()
                    .sorted()
                    .forEach(val ->
                            builder.addData(Data.builder()
                                    .withName(itemName)
                                    .withValue(val)
                                    .build()));
            if (values.size() < count) {
                builder.addData(Data.builder()
                        .withName("IsListTruncated")
                        .withValue("true")
                        .build());
            }
            list.add(builder.build());
        }
    }

    private void addData(final List<Data> list, final String name, final String value) {
        if (value != null) {
            Objects.requireNonNull(list).add(Data.builder()
                    .withName(name)
                    .withValue(value)
                    .build());
        }
    }

    private void addData(final List<Data> list, final String name, final Long value) {
        if (value != null) {
            Objects.requireNonNull(list).add(Data.builder()
                    .withName(name)
                    .withValue(String.valueOf(value))
                    .build());
        }
    }

    @AutoLogged(OperationType.SEARCH)
    @Override
    public ResultPage<MetaRow> findMetaRow(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().findDecoratedRows(criteria);
    }

    @AutoLogged(OperationType.SEARCH)
    @Override
    public SelectionSummary getSelectionSummary(final SelectionSummaryRequest request) {
        Objects.requireNonNull(request);
        return metaServiceProvider.get().getSelectionSummary(
                request.getFindMetaCriteria(),
                Objects.requireNonNullElse(request.getRequiredPermission(), DocumentPermission.VIEW));
    }

    @AutoLogged(OperationType.SEARCH)
    @Override
    public SelectionSummary getReprocessSelectionSummary(final SelectionSummaryRequest request) {
        Objects.requireNonNull(request);
        return metaServiceProvider.get().getReprocessSelectionSummary(
                request.getFindMetaCriteria(),
                Objects.requireNonNullElse(request.getRequiredPermission(), DocumentPermission.VIEW));
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED) // See no point in logging this as it is not really an explicit user action
    public List<String> getTypes() {
        return metaServiceProvider
                .get()
                .getTypes()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
