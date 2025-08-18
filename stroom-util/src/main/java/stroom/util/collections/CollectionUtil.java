package stroom.util.collections;

import stroom.util.shared.NullSafe;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionUtil {

    private CollectionUtil() {
        // Static util stuff only
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the order in items array. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledOrderedSet(final T item) {
        return item != null
                ? Collections.singleton(item)
                : Collections.emptySet();
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the order in items array. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final Set<T> items) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptySet();
        } else {
            // Use a LinkedHashSet to ensure iteration order is consistent
            final LinkedHashSet<T> cleanedSet = NullSafe.stream(items)
                    .sorted() // Sort the items for consistent insert order
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Collections.unmodifiableSet(cleanedSet);
        }
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the order in items array. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final List<T> items) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptySet();
        } else {
            // Use a LinkedHashSet to ensure iteration order is consistent
            final LinkedHashSet<T> cleanedSet = NullSafe.stream(items)
                    .sorted() // Sort the items for consistent insert order
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Collections.unmodifiableSet(cleanedSet);
        }
    }

    /**
     * Removes null items, applies formatter on each item, then removes any empty items.
     */
    public static Set<String> cleanItems(final Set<String> items) {
        return cleanItems(items, null);
    }

    /**
     * Removes null items then applies formatter on each item, then removes any items that are
     * an empty String.
     *
     * @return An unmodifiable Set of the cleaned items which may be empty if all items
     * have been removed.
     */
    public static Set<String> cleanItems(final Set<String> items,
                                         final Function<String, String> formatter) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptySet();
        } else {
            Stream<String> stringStream = NullSafe.stream(items)
                    .filter(Objects::nonNull);
            if (formatter != null) {
                stringStream = stringStream.map(formatter);
            }
            return stringStream
                    .filter(NullSafe::isNonEmptyString)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
