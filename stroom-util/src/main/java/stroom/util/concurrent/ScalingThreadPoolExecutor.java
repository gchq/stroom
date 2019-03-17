package stroom.util.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScalingThreadPoolExecutor extends ThreadPoolExecutor {
    /**
     * number of threads that are actively executing tasks
     */
    private final AtomicInteger activeCount = new AtomicInteger();

    private ScalingThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue,
                                      final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    private ScalingThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public static ScalingThreadPoolExecutor newScalingThreadPool(final int corePoolSize,
                                                                 final int maximumPoolSize,
                                                                 final int maxQueueSize,
                                                                 final long keepAliveTime,
                                                                 final TimeUnit unit) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }

        final ScalingQueue<Runnable> queue = new ScalingQueue<>(maxQueueSize);
        final ScalingThreadPoolExecutor executor = new ScalingThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue);
        executor.setRejectedExecutionHandler(new ForceQueuePolicy());
        queue.setThreadPoolExecutor(executor);
        return executor;
    }

    public static ScalingThreadPoolExecutor newScalingThreadPool(final int corePoolSize,
                                                                 final int maximumPoolSize,
                                                                 final int maxQueueSize,
                                                                 final long keepAliveTime,
                                                                 final TimeUnit unit,
                                                                 final ThreadFactory threadFactory) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }

        final ScalingQueue<Runnable> queue = new ScalingQueue<>(maxQueueSize);
        final ScalingThreadPoolExecutor executor = new ScalingThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, threadFactory);
        executor.setRejectedExecutionHandler(new ForceQueuePolicy());
        queue.setThreadPoolExecutor(executor);
        return executor;
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }

    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        activeCount.incrementAndGet();
    }

    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        activeCount.decrementAndGet();
    }

    private static class ScalingQueue<E> extends LinkedBlockingQueue<E> {
        /**
         * The executor this Queue belongs to
         */
        private ThreadPoolExecutor executor;

        /**
         * Creates a TaskQueue with the given (fixed) capacity.
         *
         * @param capacity the capacity of this queue.
         */
        ScalingQueue(final int capacity) {
            super(capacity);
        }

        /**
         * Sets the executor this queue belongs to.
         */
        void setThreadPoolExecutor(final ThreadPoolExecutor executor) {
            this.executor = executor;
        }

        /**
         * Inserts the specified element at the tail of this queue if there is at
         * least one available thread to run the current task. If all pool threads
         * are actively busy, it rejects the offer.
         *
         * @param o the element to add.
         * @return true if it was possible to add the element to this
         * queue, else false
         * @see ThreadPoolExecutor#execute(Runnable)
         */
        @Override
        public boolean offer(final E o) {
            int allWorkingThreads = executor.getActiveCount() + super.size();
            return allWorkingThreads < executor.getPoolSize() && super.offer(o);
        }
    }

    private static class ForceQueuePolicy implements RejectedExecutionHandler {
        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
            try {
                executor.getQueue().put(r);
            } catch (final InterruptedException e) {
                // Should never happen since we never wait
                throw new RejectedExecutionException(e);
            }
        }
    }
}