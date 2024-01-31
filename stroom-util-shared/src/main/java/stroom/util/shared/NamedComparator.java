package stroom.util.shared;

import java.util.Comparator;
import java.util.Objects;

/**
 * A comparator that you can name.  This is useful for debugging/logging if you are dealing with
 * lots of different comparators.
 * @param name The name for the comparator
 * @param comparator The comparator that compare delegates to.
 */
public record NamedComparator<T>(String name, Comparator<T> comparator) implements Comparator<T> {

    public NamedComparator {
        Objects.requireNonNull(name);
        Objects.requireNonNull(comparator);
    }

    public static <T> NamedComparator<T> create(final String name, final Comparator<T> comparator) {
        return new NamedComparator<>(name, comparator);
    }

    @Override
    public int compare(final T o1, final T o2) {
        return comparator.compare(o1, o2);
    }

    @Override
    public String toString() {
        return "NamedComparator{" +
                "name='" + name + '\'' +
                ", comparator=" + comparator +
                '}';
    }
}
