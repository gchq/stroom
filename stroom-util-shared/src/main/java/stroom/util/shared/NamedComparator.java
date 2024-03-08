package stroom.util.shared;

import java.util.Comparator;
import java.util.Objects;

/**
 * A comparator that you can name.  This is useful for debugging/logging if you are dealing with
 * lots of different comparators.
 */
public class NamedComparator<T> implements Comparator<T> {

    private final String name;
    private final Comparator<T> comparator;

    public NamedComparator(final String name, final Comparator<T> comparator) {
        this.name = Objects.requireNonNull(name);
        this.comparator = Objects.requireNonNull(comparator);
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
