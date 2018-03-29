/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.thread;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TestCondition {
    @Test
    public void test() {
        final ReentrantLock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final AtomicLong sleepTime = new AtomicLong();

        final Thread waker = new Thread() {
            @Override
            public void run() {
                ThreadUtil.sleep(500);
                System.out.println("wake");
                lock.lock();
                try {
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        };

        waker.start();

        try {
            final long time = System.currentTimeMillis();
            System.out.println("sleep");
            lock.lock();
            try {
                boolean didWait = condition.await(10, TimeUnit.SECONDS);
                System.out.println("didWait=" + didWait);
            } finally {
                lock.unlock();
            }
            System.out.println("awake");
            sleepTime.set(System.currentTimeMillis() - time);
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }

        Assert.assertTrue(sleepTime.get() < 2000);
    }
}
