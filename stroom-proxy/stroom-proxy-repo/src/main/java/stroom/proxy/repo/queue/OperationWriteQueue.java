package stroom.proxy.repo.queue;

import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OperationWriteQueue implements WriteQueue {

    private final List<Consumer<DSLContext>> queue = new ArrayList<>();

    @Override
    public void flush(final DSLContext context) {
        if (queue.size() > 0) {
            for (final Consumer<DSLContext> consumer : queue) {
                consumer.accept(context);
            }
        }
    }

    public void add(Consumer<DSLContext> operation) {
        queue.add(operation);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
