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

package stroom.pipeline.refdata;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.And.Builder;
import event.logging.Criteria;
import event.logging.DeleteEventAction;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@AutoLogged
public class ReferenceDataResourceImpl implements ReferenceDataResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataResourceImpl.class);

    private final Provider<ReferenceDataService> referenceDataServiceProvider;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    public ReferenceDataResourceImpl(final Provider<ReferenceDataService> referenceDataServiceProvider,
                                     final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.referenceDataServiceProvider = referenceDataServiceProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public List<RefStoreEntry> entries(final Integer limit,
                                       final Long refStreamId,
                                       final String mapName) {
        return referenceDataServiceProvider.get()
                .entries(limit != null
                                ? limit
                                : 100,
                        refStreamId,
                        mapName);
    }

    @Override
    public List<ProcessingInfoResponse> refStreamInfo(final Integer limit,
                                                      final Long refStreamId,
                                                      final String mapName) {
        return referenceDataServiceProvider.get()
                .refStreamInfo(limit != null
                                ? limit
                                : 100,
                        refStreamId,
                        mapName);
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public String lookup(final RefDataLookupRequest refDataLookupRequest) {
        return referenceDataServiceProvider.get()
                .lookup(refDataLookupRequest);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public boolean purgeByAge(final String purgeAge, final String nodeName) {
        final StroomDuration purgeAgeDuration;
        try {
            purgeAgeDuration = StroomDuration.parse(purgeAge);
        } catch (final Exception e) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Can't parse purgeAge [{}]", purgeAge), e);
        }

        final Query query = buildEventQuery(purgeAge, nodeName);

        final String nodeStr = nodeName == null
                ? "all nodes"
                : "node " + nodeName;

        final Boolean result = eventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "purgeByAge"))
                .withDescription(LogUtil.message(
                        "Purging reference data older than {} on {}",
                        purgeAge, nodeStr))
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addCriteria(Criteria.builder()
                                .withQuery(query)
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    try {
                        referenceDataServiceProvider.get()
                                .purge(purgeAgeDuration, nodeName);
                        return true;
                    } catch (final Exception e) {
                        LOGGER.error("Failed to purgeAge " + purgeAge, e);
                        throw e;
                    }
                })
                .getResultAndLog();

        return result;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public boolean purgeByFeedByAge(final String feedName,
                                    final String purgeAge,
                                    final String nodeName) {
        Objects.requireNonNull(feedName);

        final StroomDuration purgeAgeDuration;
        try {
            purgeAgeDuration = StroomDuration.parse(purgeAge);
        } catch (final Exception e) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Can't parse purgeAge [{}]", purgeAge), e);
        }

        final Query query = buildEventQuery(feedName, purgeAge, nodeName);

        final String nodeStr = nodeName == null
                ? "all nodes"
                : "node " + nodeName;

        final Boolean result = eventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "purgeByFeedByAge"))
                .withDescription(LogUtil.message(
                        "Purging reference data older than {} on {}",
                        purgeAge, nodeStr))
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addCriteria(Criteria.builder()
                                .withQuery(query)
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    try {
                        referenceDataServiceProvider.get()
                                .purge(feedName, purgeAgeDuration, nodeName);
                        return true;
                    } catch (final Exception e) {
                        LOGGER.error("Failed to purgeAge " + purgeAge, e);
                        throw e;
                    }
                })
                .getResultAndLog();

        return result;
    }

    private Query buildEventQuery(final String purgeAge,
                                  final String nodeName) {
        return buildEventQuery(null, purgeAge, nodeName);
    }

    private Query buildEventQuery(final String feedName,
                                  final String purgeAge,
                                  final String nodeName) {
        final Builder<Void> andBuilder = And.builder();

        if (feedName != null) {
            andBuilder.addTerm(Term.builder()
                    .withName("feedName")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(feedName)
                    .build());
        }
        if (purgeAge != null) {
            andBuilder.addTerm(Term.builder()
                    .withName("purgeAge")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(purgeAge)
                    .build());
        }
        if (nodeName != null) {
            andBuilder.addTerm(Term.builder()
                    .withName("nodeName")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(nodeName)
                    .build());
        }
        return Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(andBuilder.build())
                        .build())
                .build();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public boolean purgeByStreamId(final long refStreamId, final String nodeName) {
        final Boolean result = eventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "purgeByStreamId"))
                .withDescription(LogUtil.message(
                        "Purging reference data stream {} on node {}",
                        refStreamId, nodeName))
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addCriteria(Criteria.builder()
                                .withQuery(Query.builder()
                                        .withAdvanced(AdvancedQuery.builder()
                                                .addAnd(And.builder()
                                                        .addTerm(Term.builder()
                                                                .withName("refStreamId")
                                                                .withCondition(TermCondition.EQUALS)
                                                                .withValue(Long.toString(refStreamId))
                                                                .build())
                                                        .addTerm(Term.builder()
                                                                .withName("nodeName")
                                                                .withCondition(TermCondition.EQUALS)
                                                                .withValue(nodeName)
                                                                .build())
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    try {
                        // partNo is one based, partIndex is zero based
                        referenceDataServiceProvider.get()
                                .purge(refStreamId, nodeName);
                        return true;
                    } catch (final Exception e) {
                        LOGGER.error("Failed to purge stream " + refStreamId, e);
                        throw e;
                    }
                })
                .getResultAndLog();

        return result;
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public void clearBufferPool(final String nodeName) {
        try {
            referenceDataServiceProvider.get()
                    .clearBufferPool(nodeName);
        } catch (final RuntimeException e) {
            LOGGER.error("Failed to clear buffer pool", e);
            throw e;
        }
    }
}
