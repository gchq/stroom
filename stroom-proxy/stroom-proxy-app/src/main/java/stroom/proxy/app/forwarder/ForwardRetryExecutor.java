package stroom.proxy.app.forwarder;

import stroom.proxy.repo.ChangeListenerExecutor;
import stroom.proxy.repo.Forwarder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;
import stroom.util.time.StroomDuration;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ForwardRetryExecutor implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardRetryExecutor.class);

    private final StroomDuration retryFrequency;
    private final Forwarder forwarder;
    private final ChangeListenerExecutor forwarderExecutor;

    private ScheduledExecutorService executorService;

    public ForwardRetryExecutor(final StroomDuration retryFrequency,
                                final Forwarder forwarder,
                                final ChangeListenerExecutor forwarderExecutor) {
        this.retryFrequency = retryFrequency;
        this.forwarder = forwarder;
        this.forwarderExecutor = forwarderExecutor;
    }

    @Override
    public void start() {
        // Setup forwarding retries.
        if (retryFrequency != null &&
                retryFrequency.toMillis() > 0) {
            final ThreadFactory threadFactory = new CustomThreadFactory(
                    "Forwarder Retry",
                    StroomThreadGroup.instance(),
                    Thread.NORM_PRIORITY - 1);
            executorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            final Runnable runnable = () -> {
                final int count = forwarder.retryFailures();
                if (count > 0) {
                    LOGGER.info("Retrying failed forward tasks");
                    forwarderExecutor.onChange();
                }
            };
            executorService.scheduleAtFixedRate(
                    runnable,
                    0,
                    retryFrequency.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
