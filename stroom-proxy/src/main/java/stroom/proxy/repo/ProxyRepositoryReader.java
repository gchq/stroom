package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.Monitor;
import stroom.util.shared.ThreadPool;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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
    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final ThreadPool threadPool;
    private final Map<ThreadPool, ExecutorService> executorServiceMap = new ConcurrentHashMap<>();

    @Inject
    ProxyRepositoryReader(final Monitor monitor,
                          final ProxyRepositoryManager proxyRepositoryManager,
                          final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
                          final StreamHandlerFactory handlerFactory) {
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.handlerFactory = handlerFactory;
        this.proxyRepositoryManager = proxyRepositoryManager;
        this.scheduler = createScheduler(proxyRepositoryReaderConfig.getReadCron());

        threadPool = new ThreadPoolImpl("Proxy Repository Reader", 5, 0, proxyRepositoryReaderConfig.getForwardThreadCount(), proxyRepositoryReaderConfig.getForwardThreadCount());

        taskContext = new TaskContext() {
            @Override
            public void setName(final String name) {
                monitor.setName(name);
            }

            @Override
            public void info(final Object... args) {
                monitor.info(args);
            }

            @Override
            public void terminate() {
                finish.set(Boolean.TRUE);
            }

            @Override
            public boolean isTerminated() {
                return finish.get();
            }
        };

        executorProvider = new ExecutorProvider() {
            @Override
            public Executor getExecutor() {
                return getExecutor(threadPool);
            }

            @Override
            public Executor getExecutor(final ThreadPool threadPool) {
                return executorServiceMap.computeIfAbsent(threadPool, k -> ScalingThreadPoolExecutor.newScalingThreadPool(
                        threadPool.getCorePoolSize(),
                        threadPool.getMaxPoolSize(),
                        threadPool.getMaxQueueSize(),
                        60L,
                        TimeUnit.SECONDS));
            }
        };
    }

    private static Scheduler createScheduler(final String simpleCron) {
        if (simpleCron != null && simpleCron.length() > 0) {
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
            taskContext.terminate();

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
            while (!taskContext.isTerminated()) {
                try {
                    condition.await(1, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                if (!taskContext.isTerminated()) {
                    // Only do the work if we are not on a timer or our timer
                    // says we should fire.
                    if (scheduler != null && !scheduler.execute()) {
                        continue;
                    }

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Cron Match at " + DateUtil.createNormalDateTimeString());
                    }

                    try {
                        doRunWork();
                    } catch (final Throwable ex) {
                        LOGGER.error("Unhandled exception coming out of doRunWork()", ex);
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        LOGGER.info("Completed ... Thread Exit");
    }

    void doRunWork() {
        if (proxyRepositoryManager == null) {
            return;
        }
        final List<StroomZipRepository> readyToProcessList = proxyRepositoryManager.getReadableRepository();

        for (final StroomZipRepository readyToProcess : readyToProcessList) {
            if (taskContext.isTerminated()) {
                return;
            }

            // Only process the thing if we have some outgoing handlers.
            final List<StreamHandler> handlers = handlerFactory.addSendHandlers(new ArrayList<>());
            if (handlers.size() > 0) {
                final Provider<FileSetProcessor> fileSetProcessorProvider = () -> new ProxyForwardingFileSetProcessor(handlerFactory, taskContext);
                final RepositoryProcessor repositoryProcessor = new RepositoryProcessor(
                        taskContext,
                        executorProvider,
                        fileSetProcessorProvider,
                        FileUtil.getCanonicalPath(readyToProcess.getRootDir()),
                        proxyRepositoryReaderConfig.getForwardThreadCount(),
                        proxyRepositoryReaderConfig.getMaxFileScan(),
                        proxyRepositoryReaderConfig.getMaxConcurrentMappedFiles(),
                        proxyRepositoryReaderConfig.getMaxAggregation(),
                        proxyRepositoryReaderConfig.getMaxStreamSize());

                repositoryProcessor.process();
            }
            // Otherwise just clean.
            readyToProcess.clean();
        }
    }

    public void stop() {
        taskContext.terminate();
        LOGGER.info("stop() - Stopping Reader Thread");
        stopReading();
        LOGGER.info("stop() - Stopped  Reader Thread");

        LOGGER.info("stop() - Stopping Executors");
        executorServiceMap.values().forEach(ExecutorService::shutdownNow);
        executorServiceMap.clear();
        LOGGER.info("stop() - Stopped  Executors");
    }

    public void start() {
        startReading();
    }
}
