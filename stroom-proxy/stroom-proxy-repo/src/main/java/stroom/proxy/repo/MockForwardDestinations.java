package stroom.proxy.repo;

import stroom.receive.common.StreamHandlers;
import stroom.util.concurrent.UncheckedInterruptedException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class MockForwardDestinations implements ForwarderDestinations {

    private final ProgressLog progressLog;
    private final AtomicInteger forwardCount = new AtomicInteger();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    @Inject
    public MockForwardDestinations(final ProgressLog progressLog) {
        this.progressLog = progressLog;
    }

    @Override
    public List<String> getDestinationNames() {
        return Collections.singletonList("test");
    }

    @Override
    public StreamHandlers getProvider(final String name) {
        return (feeName, typeName, attributeMap, consumer) -> {
            progressLog.increment("MockForwardDestinations - handle");
            try {
                lock.lockInterruptibly();
                try {
                    forwardCount.incrementAndGet();
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
            consumer.accept((entry, inputStream, progressHandler) -> 0);
        };
    }

    public long awaitNew(final long lastAddedStoreId) {
        long currentStoreId = lastAddedStoreId;
        try {
            lock.lockInterruptibly();
            try {
                currentStoreId = forwardCount.get();
                while (currentStoreId <= lastAddedStoreId) {
                    condition.await();
                    currentStoreId = forwardCount.get();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return currentStoreId;
    }

    public void clear() {
        try {
            lock.lockInterruptibly();
            try {
                forwardCount.set(0);
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }
}
