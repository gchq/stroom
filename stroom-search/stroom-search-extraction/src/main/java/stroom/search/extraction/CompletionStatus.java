package stroom.search.extraction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CompletionStatus {
    private final String name;
    private final AtomicBoolean complete = new AtomicBoolean();
    private final Queue<CompletionStatus> children = new LinkedBlockingQueue<>();
    private final Map<String, AtomicLong> statistics = Collections.synchronizedMap(new LinkedHashMap<>());

    public CompletionStatus(final String name) {
        this.name = name;
    }

    public boolean isComplete() {
        if (complete.get()) {
            for (final CompletionStatus child : children) {
                if (!child.isComplete()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean complete() {
        return this.complete.compareAndSet(false, true);
    }

    public AtomicLong getStatistic(final String name) {
        return statistics.computeIfAbsent(name, k -> new AtomicLong());
    }

    public void addChild(final CompletionStatus completionStatus) {
        this.children.add(completionStatus);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendString(sb, 0);
        return sb.toString();
    }

    private void appendString(final StringBuilder sb, final int depth) {
        addDepth(sb, depth);
        sb.append("complete=");
        sb.append(complete);
        addStats(sb);
        sb.append(" - ");
        sb.append(name);
        children.forEach(child -> {
            sb.append("\n");
            child.appendString(sb, depth + 1);
        });
    }

    private void addDepth(final StringBuilder sb, final int depth) {
        for (int i = 0; i < 5; i++) {
            if (i == depth) {
                sb.append(">");
            } else {
                sb.append(" ");
            }
        }
    }

    private void addStats(final StringBuilder sb) {
        statistics.forEach((k, v) -> {
            sb.append(", ");
            sb.append(k);
            sb.append("=");
            sb.append(v.get());
        });
    }
}
