package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.proxy.handler.HandlerFactory;
import stroom.proxy.handler.RequestHandler;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.Monitor;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomStartup;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that reads repositories.
 */
public class ProxyRepositoryReader extends StroomZipRepositorySimpleExecutorProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryReader.class);

    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final ProxyRepositoryManager proxyRepositoryManager;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private final HandlerFactory handlerFactory;

    /**
     * Our worker thread
     */
    private volatile CompletableFuture<Void> readerThread;

    /**
     * CRON trigger - can be null
     */
    private final Scheduler scheduler;
    private final AtomicLong proxyForwardId = new AtomicLong(0);

    /**
     * Flag set to stop things
     */
    private final AtomicBoolean finish = new AtomicBoolean(false);

    private volatile String hostName = null;

    ProxyRepositoryReader(final Monitor monitor, final ProxyRepositoryManager proxyRepositoryManager, final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig, final HandlerFactory handlerFactory) {
        super(monitor, proxyRepositoryReaderConfig.getForwardThreadCount());
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.handlerFactory = handlerFactory;
        this.proxyRepositoryManager = proxyRepositoryManager;
        this.scheduler = createScheduler(proxyRepositoryReaderConfig.getReadCron());
    }

    private static Scheduler createScheduler(final String simpleCron) {
        if (simpleCron != null && simpleCron.length() > 0) {
            return SimpleCron.compile(simpleCron).createScheduler();
        }

        return null;
    }

    private String getHostName() {
        if (hostName == null) {
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (final Exception ex) {
                hostName = "Unknown";
            }
        }
        return hostName;
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
            final List<RequestHandler> handlers = handlerFactory.create();
            if (handlers.size() > 0) {
                process(readyToProcess);
            }
            // Otherwise just clean.
            readyToProcess.clean();
        }
    }

//    public List<RequestHandler> createOutgoingRequestHandlerList() {
//        return new ArrayList<>();
//    }

    /**
     * Send a load of files for the same feed
     */
    @Override
    public void processFeedFiles(final StroomZipRepository stroomZipRepository, final String feed,
                                 final List<Path> fileList) {
        final long thisPostId = proxyForwardId.incrementAndGet();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("processFeedFiles() - proxyForwardId " + thisPostId + " " + feed + " file count "
                    + fileList.size());
        }

        final MetaMap metaMap = new MetaMap();
        metaMap.put(StroomHeaderArguments.FEED, feed);
        metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);
        metaMap.put(StroomHeaderArguments.RECEIVED_PATH, getHostName());
        if (LOGGER.isDebugEnabled()) {
            metaMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final List<RequestHandler> handlers = handlerFactory.create();

        try {
            // Start the post
            for (final RequestHandler requestHandler : handlers) {
                requestHandler.setMetaMap(metaMap);
                requestHandler.handleHeader();
            }

            long sequenceId = 1;
            long batch = 1;

            final StreamProgressMonitor streamProgress = new StreamProgressMonitor("ProxyRepositoryReader " + feed);
            final List<Path> deleteList = new ArrayList<>();

            Long nextBatchBreak = proxyRepositoryReaderConfig.getMaxStreamSize();

            for (final Path file : fileList) {
                // Send no more if told to finish
                if (finish.get()) {
                    LOGGER.info("processFeedFiles() - Quitting early as we have been told to stop");
                    break;
                }
                if (sequenceId > proxyRepositoryReaderConfig.getMaxAggregation()
                        || (streamProgress.getTotalBytes() > nextBatchBreak)) {
                    batch++;
                    LOGGER.info("processFeedFiles() - Starting new batch %s as sequence %s > %s or size %s > %s", batch,
                            sequenceId, proxyRepositoryReaderConfig.getMaxAggregation(), streamProgress.getTotalBytes(), nextBatchBreak);

                    sequenceId = 1;
                    nextBatchBreak = streamProgress.getTotalBytes() + proxyRepositoryReaderConfig.getMaxStreamSize();

                    // Start a new batch
                    for (final RequestHandler requestHandler : handlers) {
                        requestHandler.handleFooter();
                    }
                    deleteFiles(stroomZipRepository, deleteList);
                    deleteList.clear();

                    // Start the post
                    for (final RequestHandler requestHandler : handlers) {
                        requestHandler.setMetaMap(metaMap);
                        requestHandler.handleHeader();
                    }
                }

                sequenceId = processFeedFile(handlers, stroomZipRepository, file, streamProgress, sequenceId);

                deleteList.add(file);

            }
            for (final RequestHandler requestHandler : handlers) {
                requestHandler.handleFooter();
            }

            deleteFiles(stroomZipRepository, deleteList);

        } catch (final IOException ex) {
            LOGGER.warn("processFeedFiles() - Failed to send to feed " + feed + " ( " + String.valueOf(ex) + ")");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("processFeedFiles() - Debug trace " + feed, ex);
            }
            for (final RequestHandler requestHandler : handlers) {
                try {
                    requestHandler.handleError();
                } catch (final IOException ioEx) {
                    LOGGER.error("fileSend()", ioEx);
                }
            }
        }
    }

    @StroomShutdown
    public void stop() {
        finish.set(true);
        LOGGER.info("stop() - Stopping Executor");
        stopExecutor(true);
        LOGGER.info("stop() - Stopped  Executor");
        LOGGER.info("stop() - Stopping Reader Thread");
        stopReading();
        LOGGER.info("stop() - Stopped  Reader Thread");
    }

    @StroomStartup
    public void start() {
        startReading();
    }
}
