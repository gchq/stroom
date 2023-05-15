package stroom.proxy.repo.queue;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

@Singleton
public class QueueMonitors {

    private final Map<Key, QueueMonitorImpl> queueMonitorMap = new ConcurrentHashMap<>();

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
        queueMonitorMap.put(new Key(order, name), queueMonitor);
        return queueMonitor;
    }

    private record Key(int order, String name) {

    }

    public class QueueMonitorImpl implements QueueMonitor {

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
    }
}
