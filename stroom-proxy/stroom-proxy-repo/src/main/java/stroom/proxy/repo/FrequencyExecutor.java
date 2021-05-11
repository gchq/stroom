package stroom.proxy.repo;

import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class FrequencyExecutor implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrequencyExecutor.class);

    private final ScheduledExecutorService executorService;
    private final Runnable command;
    private final long frequency;

    private volatile boolean stop = false;
    private volatile Thread thread;

    public FrequencyExecutor(final String name,
                             final Runnable command,
                             final long frequency) {
        this.command = command;
        this.frequency = frequency;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                name,
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Override
    public void start() {
        // Start.
        executorService.schedule(this::run, 0, TimeUnit.MILLISECONDS);
    }

    private void run() {

        thread = Thread.currentThread();
        final long start = System.currentTimeMillis();

        try {
            if (!stop) {
                command.run();
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // How long were we running for?
        final long duration = System.currentTimeMillis() - start;
        final long delay = Math.max(0, frequency - duration);
        thread = null;

        if (!stop) {
            executorService.schedule(this::run, delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        stop = true;
        final Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
    }
}
