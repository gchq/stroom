package stroom.util.concurrent;

import java.util.List;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Gets sequential items from the provided list, looping back to the beginning
 * once it hits the end.
 */
@ThreadSafe
public class AtomicLoopedItemSequence {

    private final AtomicLoopedIntegerSequence sequence = new AtomicLoopedIntegerSequence(
            Integer.MAX_VALUE);

    public static AtomicLoopedItemSequence create() {
        return new AtomicLoopedItemSequence();
    }

    /**
     * Gets sequential items from the provided list, looping back to the beginning
     * once it hits the end.
     * @param list The list to get items from in sequential order.
     * @return The next item in the list or an empty {@link Optional} if there isn't one. If the
     * passed list is null or empty and empty {@link Optional} will be returned.
     */
    public <T> Optional<T> getNextItem(final List<T> list) {
        if (list == null) {
            return Optional.empty();
        } else {
            final int size = list.size();
            if (size == 0) {
                return Optional.empty();
            } else if (size == 1) {
                return Optional.ofNullable(list.get(0));
            } else {
                final int idx = sequence.getNext() % size;
                return Optional.ofNullable(list.get(idx));
            }
        }
    }
}
