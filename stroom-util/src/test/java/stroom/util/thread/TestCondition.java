/*
 * Copyright 2016-2025 Crown Copyright
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


import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

class TestCondition {

    @Test
    void test() {
        final ReentrantLock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final AtomicLong sleepTime = new AtomicLong();

        final Thread waker = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException e) {
                    System.out.println("interrupted thread");
                }

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
                final boolean didWait = condition.await(10, TimeUnit.SECONDS);
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

        assertThat(sleepTime.get() < 2000).isTrue();
    }
}
