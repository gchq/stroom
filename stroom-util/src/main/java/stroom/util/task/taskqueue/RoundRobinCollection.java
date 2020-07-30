package stroom.util.task.taskqueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoundRobinCollection<T> {
    private final List<T> list = new ArrayList<>();
    private volatile int index;

    public synchronized void add(T item) {
        list.add(item);
    }

    public synchronized void remove(T item) {
        list.remove(item);
    }

    public synchronized Optional<T> next() {
        if (list.size() > 0) {
            index++;
            if (index >= list.size()) {
                index = 0;
            }
            return Optional.of(list.get(index));
        }
        return Optional.empty();
    }

    public synchronized List<T> list() {
        return new ArrayList<>(list);
    }
}
