package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.task.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that reads repositories.
 */
public final class ProxyRepositoryReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryReader.class);


    private final ProxyRepositoryManager proxyRepositoryManager;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private final StreamHandlerFactory handlerFactory;

    /**
     * Our worker thread
     */
    private volatile CompletableFuture<Void> readerThread;

    /**
     * CRON trigger - can be null
     */
    private final Scheduler scheduler;

    /**
     * Flag set to stop things
     */
    private final AtomicBoolean finish = new AtomicBoolean(false);

    private final ExecutorService executorService;
    private final RepositoryProcessor repositoryProcessor;

    @Inject
    ProxyRepositoryReader(final TaskContext taskContext,
                          final ProxyRepositoryManager proxyRepositoryManager,
                          final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
                          final StreamHandlerFactory handlerFactory) {
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.handlerFactory = handlerFactory;
        this.proxyRepositoryManager = proxyRepositoryManager;
        this.scheduler = createScheduler(proxyRepositoryReaderConfig.getReadCron());

        this.executorService = Executors.newFixedThreadPool(proxyRepositoryReaderConfig.getForwardThreadCount());
        final ProxyFileProcessor feedFileProcessor = new ProxyFileProcessorImpl(proxyRepositoryReaderConfig, handlerFactory, finish);
        repositoryProcessor = new RepositoryProcessor(feedFileProcessor, executorService, taskContext);
    }

    private static Scheduler createScheduler(final String simpleCron) {
        if (simpleCron != null && !simpleCron.isEmpty()) {
            return SimpleCron.compile(simpleCron).createScheduler();
        }

        return null;
    }

    private synchronized void startReading() {
        if (readerThread == null) {
            finish.set(false);

            readerThread = CompletableFuture.runAsync(this::process);
        }
    }

    private synchronized void stopReading() {
        if (readerThread != null) {
            finish.set(true);

            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }

            boolean waiting = true;
            while (waiting) {
                try {
                    LOGGER.info("stopReading() - Waiting for read thread to stop");
                    readerThread.get(1, TimeUnit.SECONDS);
                    waiting = false;
                } catch (final TimeoutException e) {
                    // Ignore.
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                    waiting = false;
                }
            }
        }
    }

    /**
     * Main Working Thread - Keep looping until we have been told to finish
     */
    private void process() {
        lock.lock();
        try {
            while (!finish.get()) {
                try {
                    condition.await(1, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                if (!finish.get()) {
                    // Only do the work if we are not on a timer or our timer
                    // says we should fire.
                    if (scheduler != null && !scheduler.execute()) {
                        continue;
                    }

                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("run() - Cron Match at " + DateUtil.createNormalDateTimeString());
                    }

                    try {
                        doRunWork();
                    } catch (final Throwable ex) {
                        LOGGER.error("run() - Unhandled exception coming out of doRunWork()", ex);
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        LOGGER.info("run() - Completed ... Thread Exit");
    }

    void doRunWork() {
        if (proxyRepositoryManager == null) {
            return;
        }
        final List<StroomZipRepository> readyToProcessList = proxyRepositoryManager.getReadableRepository();

        for (final StroomZipRepository readyToProcess : readyToProcessList) {
            if (finish.get()) {
                return;
            }

            // Only process the thing if we have some outgoing handlers.
            final List<StreamHandler> handlers = handlerFactory.addSendHandlers(new ArrayList<>());
            if (handlers.size() > 0) {
                repositoryProcessor.process(readyToProcess);
            }
            // Otherwise just clean.
            readyToProcess.clean();
        }
    }

    public void stop() {
        finish.set(true);
        LOGGER.info("stop() - Stopping Executor");
        executorService.shutdownNow();
        LOGGER.info("stop() - Stopped  Executor");
        LOGGER.info("stop() - Stopping Reader Thread");
        stopReading();
        LOGGER.info("stop() - Stopped  Reader Thread");
    }

    public void start() {
        startReading();
    }
}
