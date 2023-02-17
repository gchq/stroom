package stroom.processor.impl;

import stroom.util.concurrent.UncheckedInterruptedException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WaitHelper {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicBoolean wait = new AtomicBoolean(true);

    public void waitForSignal() {
        if (wait.get()) {
            try {
                lock.lockInterruptibly();
                while (wait.get()) {
                    condition.await();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            } finally {
                lock.unlock();
            }
            wait.set(false);
        }
    }

    public void signal() {
        wait.set(false);
        try {
            lock.lockInterruptibly();
            condition.signalAll();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        } finally {
            lock.unlock();
        }
    }
}
