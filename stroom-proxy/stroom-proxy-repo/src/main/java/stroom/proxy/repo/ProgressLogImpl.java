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

package stroom.proxy.repo;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class ProgressLogImpl implements ProgressLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressLogImpl.class);

    private final Map<String, AtomicLong> map = new ConcurrentHashMap<>();
    private final AtomicLong startTime = new AtomicLong(0);
    private long autoLogCount = -1;
    private long lastLogTimeMs;

    @Override
    public void increment(final String name) {
        add(name, 1);
    }

    @Override
    public void add(final String name, final long delta) {
        if (LOGGER.isDebugEnabled() || autoLogCount > 0) {

//            String className = null;
//            String methodName = null;
//            boolean next = false;
//            final StackTraceElement[] elements = Thread.currentThread().getStackTrace();
//            for (int i = 0; i < elements.length; i++) {
//                final StackTraceElement element = elements[i];
//                if (element.getClassName().equals(this.getClass().getName())) {
//                    next = true;
//                } else if (next) {
//                    className = element.getClassName();
//                    methodName = element.getMethodName();
//                    break;
//                }
//            }
//
//            final String name = className + " - " + methodName;

            ensureStart();

            final long count = map.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
            if (autoLogCount > 0 && count >= autoLogCount) {
                report();
            } else {
                periodicReport();
            }
        }
    }

    private void ensureStart() {
        if (startTime.get() == 0) {
            startTime.compareAndSet(0, System.currentTimeMillis());
        }
    }

    private synchronized void periodicReport() {
        final long now = System.currentTimeMillis();
        if (lastLogTimeMs < now - 10000) {
            lastLogTimeMs = now;
            report();
        }
    }

    @Override
    public String report() {
        ensureStart();

        final StringBuilder sb = new StringBuilder();
        sb.append("\n");
        map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    sb.append(entry.getKey());
                    sb.append(" = ");
                    sb.append(entry.getValue().get());
                    sb.append("\n");
                });
        sb.append("Elapsed Time = ");
        sb.append(Duration.ofMillis(System.currentTimeMillis() - startTime.get()).toString());
        final String report = sb.toString();
        LOGGER.info(report);
        return report;
    }

    @Override
    public void selAutoLogCount(final long count) {
        this.autoLogCount = count;
    }
}
