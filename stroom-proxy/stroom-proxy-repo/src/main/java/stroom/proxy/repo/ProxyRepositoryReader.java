package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.shared.ThreadPool;
import stroom.task.shared.ThreadPoolImpl;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.date.DateUtil;
import stroom.util.io.BufferFactory;
import stroom.util.io.FileUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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

    private final TaskContext taskContext;
    private final BufferFactory bufferFactory;
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

    private final ExecutorProvider executorProvider;
    private final ThreadPool threadPool;
    private final Map<ThreadPool, ExecutorService> executorServiceMap = new ConcurrentHashMap<>();
    private final AtomicBoolean finish = new AtomicBoolean();

    @Inject
    ProxyRepositoryReader(final TaskContext taskContext,
                          final BufferFactory bufferFactory,
                          final ProxyRepositoryManager proxyRepositoryManager,
                          final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
                          final StreamHandlerFactory handlerFactory) {
        this.taskContext = taskContext;
        this.bufferFactory = bufferFactory;
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.handlerFactory = handlerFactory;
        this.proxyRepositoryManager = proxyRepositoryManager;
        this.scheduler = createScheduler(proxyRepositoryReaderConfig.getReadCron());

        threadPool = new ThreadPoolImpl("Proxy Repository Reader", 5, 0, proxyRepositoryReaderConfig.getForwardThreadCount(), proxyRepositoryReaderConfig.getForwardThreadCount());

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
            terminate();

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
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                    waiting = false;

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                } catch (final ExecutionException | RuntimeException e) {
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
            while (!isTerminated()) {
                try {
                    condition.await(1, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }

                if (!isTerminated()) {
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
                    } catch (final RuntimeException e) {
                        LOGGER.error("Unhandled exception coming out of doRunWork()", e);
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
            if (!isTerminated()) {
                return;
            }

            // Only process the thing if we have some outgoing handlers.
            final List<StreamHandler> handlers = handlerFactory.addSendHandlers(new ArrayList<>());
            if (handlers.size() > 0) {
                final Provider<FileSetProcessor> fileSetProcessorProvider = () -> new ProxyForwardingFileSetProcessor(handlerFactory, bufferFactory);
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

            // If the root of this repo is also our configured rootRepoDir then we don't want to delete the
            // repo's root on clean as it causes problems in docker containers. Deleting a configured directory
            // may also cause confusion for admins.
            final boolean deleteRootDirectory = !readyToProcess.getRootDir()
                    .equals(proxyRepositoryManager.getRootRepoDir());
            readyToProcess.clean(deleteRootDirectory);
        }
    }

    public void stop() {
        terminate();
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

    private boolean isTerminated() {
        if (finish.get() || Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            return true;
        }
        return false;
    }

    private void terminate() {
        finish.set(true);
        Thread.currentThread().interrupt();
    }
}
