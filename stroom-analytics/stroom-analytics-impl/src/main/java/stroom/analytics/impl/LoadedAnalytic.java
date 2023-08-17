package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TimeFilter;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.shared.ViewDoc;

import java.time.Instant;
import java.util.Optional;

public record LoadedAnalytic(String ruleIdentity,
                             AnalyticRuleDoc analyticRuleDoc,
                             AnalyticProcessorFilter analyticProcessorFilter,
                             AnalyticProcessorFilterTracker.Builder trackerBuilder,
                             SearchRequest searchRequest,
                             ViewDoc viewDoc) {

    private static final Instant BEGINNING_OF_TIME = Instant.ofEpochMilli(0);

    public Optional<TimeFilter> createTimeFilter(final SimpleDuration timeToWaitForData,
                                                 final Long lastTimeFilterTo,
                                                 final boolean upToDate,
                                                 final Instant startTime,
                                                 final Optional<Instant> optionalLastEventTime) {
        Instant from = null;
        Instant to;

        if (lastTimeFilterTo == null) {
            if (analyticProcessorFilter() != null) {
                if (analyticProcessorFilter().getMinMetaCreateTimeMs() != null) {
                    from = Instant.ofEpochMilli(analyticProcessorFilter().getMinMetaCreateTimeMs());
                }
            }

            if (from == null) {
                from = BEGINNING_OF_TIME;
            }
        } else {
            // Get the last time we executed plus one millisecond as this will be the start of the new window.
            from = Instant.ofEpochMilli(lastTimeFilterTo);
        }

        // If the current processing is up-to-date then use the processing start time as the basis for the window end.
        if (upToDate) {
            to = startTime;
        } else {
            to = optionalLastEventTime.orElse(from);
        }

        // Subtract the waiting period from the window end.
        to = SimpleDurationUtil.minus(to, timeToWaitForData);

        // Limit max time.
        if (analyticProcessorFilter() != null) {
            if (analyticProcessorFilter().getMaxMetaCreateTimeMs() != null) {
                final Instant maxTime = Instant.ofEpochMilli(analyticProcessorFilter().getMaxMetaCreateTimeMs());
                if (to.isAfter(maxTime)) {
                    to = maxTime;
                }
            }
        }

        if (to.isAfter(from)) {
            final TimeFilter timeFilter = new TimeFilter(from.toEpochMilli(), to.toEpochMilli());
            return Optional.of(timeFilter);
        }

        return Optional.empty();
    }
}
