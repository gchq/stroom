package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Singleton;

@Singleton
public class ProgressLogImpl implements ProgressLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressLog.class);

    private final Map<String, AtomicLong> map = new ConcurrentHashMap<>();
    private final AtomicLong startTime = new AtomicLong(0);
    private long autoLogCount = -1;

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
            }
        }
    }

    private void ensureStart() {
        if (startTime.get() == 0) {
            startTime.compareAndSet(0, System.currentTimeMillis());
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
