package stroom.search.extraction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CompletionStatusImpl implements CompletionStatus {
    private final AtomicBoolean complete = new AtomicBoolean();
    private final AtomicLong count = new AtomicLong();
    private final Map<String, CompletionStatus> childMap = new ConcurrentHashMap<>();

    @Override
    public boolean isComplete() {
        if (complete.get()) {
            for (final CompletionStatus child : childMap.values()) {
                if (!child.isComplete()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void setComplete() {
        this.complete.set(true);
    }

    @Override
    public long getCount() {
        return this.count.get();
    }

    public void setCount(final long count) {
        this.count.set(count);
    }

    public void increment() {
        this.count.incrementAndGet();
    }

    @Override
    public void addChild(final String name, final CompletionStatus completionStatus) {
        this.childMap.put(name, completionStatus);
    }

    @Override
    public String toString() {
        return "CompletionStatusImpl{" +
                "complete=" + complete +
                ", count=" + count +
                ", childMap=" + childMap +
                '}';
    }
}
