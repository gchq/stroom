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

package stroom.proxy.repo.queue;

import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Singleton
public class QueueMonitors {

    private final Map<Key, QueueMonitorImpl> queueMonitorMap = new ConcurrentHashMap<>();
    private final Metrics metrics;

    @Inject
    public QueueMonitors(final Metrics metrics) {
        this.metrics = metrics;
    }

//    private final Queue<NamedMetric> unregisteredMetrics = new ConcurrentLinkedQueue<>();
//    private volatile Consumer<NamedMetric> metricConsumer;

    public synchronized String log() {
        final StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr>");
        sb.append("<th>Queue Name</th>");
        sb.append("<th>Write</th>");
        sb.append("<th>Read</th>");
        sb.append("<th>Diff</th>");
        sb.append("<th>Buffer</th>");
        sb.append("<th>Buffer Size</th>");
        sb.append("</tr>");
        queueMonitorMap.entrySet()
                .stream()
                .sorted(Comparator
                        .comparing((Entry<Key, QueueMonitorImpl> e) -> e.getKey().order())
                        .thenComparing(e -> e.getKey().name()))
                .forEach(e -> {
                    final String name = e.getKey().name;
                    final QueueMonitorImpl monitor = e.getValue();

                    sb.append("<tr>");
                    sb.append("<td>");
                    sb.append(name);
                    sb.append("</td>");
                    sb.append("<td>");
                    final long write = monitor.writePos;
                    sb.append(write);
                    sb.append("</td>");
                    sb.append("<td>");
                    final long read = monitor.readPos;
                    sb.append(read);
                    sb.append("</td>");
                    sb.append("<td>");
                    sb.append(write - read);
                    sb.append("</td>");
                    sb.append("<td>");
                    final long buffer = monitor.bufferPos;
                    if (buffer > 0) {
                        sb.append(buffer);
                    }
                    sb.append("</td>");
                    sb.append("<td>");
                    if (buffer > 0) {
                        sb.append(buffer - read);
                    }
                    sb.append("</td>");
                    sb.append("</tr>");
                });
        sb.append("</table>");

        return sb.toString();
    }

    public QueueMonitor create(final int order, final String name) {
        final QueueMonitorImpl queueMonitor = new QueueMonitorImpl();
        final Key key = new Key(order, name);
        final QueueMonitorImpl prevVal = queueMonitorMap.put(key, queueMonitor);
        if (prevVal == null) {
            registerMetrics(key);
        }
        return queueMonitor;
    }

    private void registerMetrics(final Key key) {
        NullSafe.consume(key, queueMonitorMap::get, queueMonitor -> {
            registerMetric(
                    List.of(key.name(), Metrics.WRITE, Metrics.POSITION),
                    queueMonitor,
                    QueueMonitorImpl::getWritePos);
            registerMetric(
                    List.of(key.name(), Metrics.READ, Metrics.POSITION),
                    queueMonitor,
                    QueueMonitorImpl::getReadPos);
            registerMetric(
                    List.of(key.name(), Metrics.DELTA),
                    queueMonitor,
                    QueueMonitorImpl::getReadWriteDelta);
            registerMetric(
                    List.of(key.name(), "buffer", Metrics.POSITION),
                    queueMonitor,
                    QueueMonitorImpl::getBufferPos);
            registerMetric(
                    List.of(key.name(), "buffer", Metrics.SIZE),
                    queueMonitor,
                    QueueMonitorImpl::getBufferSize);
        });
    }

    private void registerMetric(final List<String> nameParts,
                                final QueueMonitorImpl queueMonitor,
                                final Function<QueueMonitorImpl, Long> valueFunc) {
        metrics.registrationBuilder(getClass())
                .withNameParts(nameParts)
                .gauge(() ->
                        valueFunc.apply(queueMonitor))
                .register();
    }

    // --------------------------------------------------------------------------------


    private record Key(int order, String name) {

    }


    // --------------------------------------------------------------------------------


    public static class QueueMonitorImpl implements QueueMonitor {

        private volatile long writePos;
        private volatile long readPos;
        private volatile long bufferPos;

        @Override
        public void setWritePos(final long writePos) {
            this.writePos = writePos;
        }

        @Override
        public void setReadPos(final long readPos) {
            this.readPos = readPos;
        }

        @Override
        public void setBufferPos(final long bufferPos) {
            this.bufferPos = bufferPos;
        }

        public long getWritePos() {
            return writePos;
        }

        public long getReadPos() {
            return readPos;
        }

        public long getReadWriteDelta() {
            return writePos - readPos;
        }

        public long getBufferPos() {
            return bufferPos;
        }

        public long getBufferSize() {
            return bufferPos > 0
                    ? bufferPos - readPos
                    : 0;
        }

        @Override
        public String toString() {
            return "QueueMonitorImpl{" +
                   "writePos=" + writePos +
                   ", readPos=" + readPos +
                   ", bufferPos=" + bufferPos +
                   '}';
        }
    }
}
