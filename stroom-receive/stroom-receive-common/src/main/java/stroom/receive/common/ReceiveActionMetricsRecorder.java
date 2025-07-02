package stroom.receive.common;

import stroom.feed.shared.FeedDoc;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.Meter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumMap;

/**
 * Wraps one metrics {@link Meter} for each of the {@link ReceiveAction} values.
 */
@Singleton
public class ReceiveActionMetricsRecorder {

    private final EnumMap<ReceiveAction, Meter> metersMap = new EnumMap<>(ReceiveAction.class);

    @Inject
    public ReceiveActionMetricsRecorder(final Metrics metrics) {
        for (final ReceiveAction receiveAction : ReceiveAction.values()) {
            final Meter meter = metrics.registrationBuilder(getClass())
                    .addNamePart("receiveAction")
                    .addNamePart(receiveAction.name())
                    .meter()
                    .createAndRegister();
            metersMap.put(receiveAction, meter);
        }
    }

    public void record(final ReceiveAction receiveAction) {
        NullSafe.consume(receiveAction, metersMap::get, Meter::mark);
    }

    public void record(final FeedStatus feedStatus) {
        record(convertFeedStatus(feedStatus));
    }

    public void record(final FeedDoc.FeedStatus feedStatus) {
        record(convertFeedStatus(feedStatus));
    }

    private ReceiveAction convertFeedStatus(final FeedStatus feedStatus) {
        return switch (feedStatus) {
            case null -> null;
            case Receive -> ReceiveAction.RECEIVE;
            case Reject -> ReceiveAction.REJECT;
            case Drop -> ReceiveAction.DROP;
        };
    }

    private ReceiveAction convertFeedStatus(final FeedDoc.FeedStatus feedStatus) {
        return switch (feedStatus) {
            case null -> null;
            case RECEIVE -> ReceiveAction.RECEIVE;
            case REJECT -> ReceiveAction.REJECT;
            case DROP -> ReceiveAction.DROP;
        };
    }
}
