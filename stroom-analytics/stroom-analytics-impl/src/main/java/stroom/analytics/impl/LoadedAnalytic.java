package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotificationState;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TimeFilter;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.shared.ViewDoc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

public record LoadedAnalytic(String ruleIdentity,
                             AnalyticRuleDoc analyticRuleDoc,
                             AnalyticProcessorFilter analyticProcessorFilter,
                             AnalyticProcessorFilterTracker.Builder trackerBuilder,
                             SearchRequest searchRequest,
                             ViewDoc viewDoc) {

    private static final LocalDateTime BEGINNING_OF_TIME =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC);

    public Optional<TimeFilter> createTimeFilter(final AnalyticNotificationStreamConfig streamConfig,
                                                 final AnalyticNotificationState notificationState,
                                                 final boolean upToDate,
                                                 final LocalDateTime startTime,
                                                 final Optional<LocalDateTime> optionalLastEventTime) {
        final SimpleDuration timeToWaitForData = streamConfig.getTimeToWaitForData();
        final Long lastTime = notificationState.getLastExecutionTime();
        LocalDateTime from = null;
        LocalDateTime to;

        if (lastTime == null) {
            if (analyticProcessorFilter() != null) {
                if (analyticProcessorFilter().getMinMetaCreateTimeMs() != null) {
                    from = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(analyticProcessorFilter().getMinMetaCreateTimeMs()),
                            ZoneOffset.UTC);
                }
            }

            if (from == null) {
                from = BEGINNING_OF_TIME;
            }
        } else {
            // Get the last time we executed plus one millisecond as this will be the start of the new window.
            from = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastTime),
                    ZoneOffset.UTC).plus(Duration.ofMillis(1));
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
                final LocalDateTime maxTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(analyticProcessorFilter().getMaxMetaCreateTimeMs()),
                        ZoneOffset.UTC);
                if (to.isAfter(maxTime)) {
                    to = maxTime;
                }
            }
        }

        if (to.isAfter(from)) {
            final TimeFilter timeFilter = new TimeFilter(
                    from.toInstant(ZoneOffset.UTC).toEpochMilli(),
                    to.toInstant(ZoneOffset.UTC).toEpochMilli());
            return Optional.of(timeFilter);
        }

        return Optional.empty();
    }
}
