package stroom.receive.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DataReceiptMetrics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataReceiptMetrics.class);

    private final Timer requestTimer;
    private final Histogram contentLengthHistogram;

    @Inject
    public DataReceiptMetrics(final Metrics metrics) {
        this.requestTimer = metrics.registrationBuilder(getClass())
                .addNamePart("request")
                .addNamePart("time")
                .timer()
                .createAndRegister();
        this.contentLengthHistogram = metrics.registrationBuilder(getClass())
                .addNamePart("contentLength")
                .addNamePart(Metrics.SIZE_IN_BYTES)
                .histogram()
                .createAndRegister();
    }

    public void timeRequest(final Runnable runnable) {
        requestTimer.time(runnable);
    }

    public Timer getRequestTimer() {
        return requestTimer;
    }

    public void recordContentLength(final String contentLength) {
        if (NullSafe.isNonEmptyString(contentLength)) {
            try {
                final long len = Long.parseLong(contentLength);
                contentLengthHistogram.update(len);
            } catch (final NumberFormatException e) {
                LOGGER.debug("Unable to parse '{}' to a long", contentLength);
            }
        }
    }
}
