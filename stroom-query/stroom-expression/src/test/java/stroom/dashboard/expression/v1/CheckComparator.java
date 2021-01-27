package stroom.dashboard.expression.v1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class CheckComparator {
    /**
     * @param dailyReports
     * @param comparator
     */
    static <T> void checkConsitency(final List<T> dailyReports, final Comparator<T> comparator) {
        final Map<T, List<T>> objectMapSmallerOnes = new HashMap<>();

        iterateDistinctPairs(dailyReports.iterator(), new IPairIteratorCallback<T>() {
            /**
             * @param o1
             * @param o2
             */
            @Override
            public void pair(T o1, T o2) {
                final int diff = comparator.compare(o1, o2);
                if (diff < 0) {
                    checkConsistency(objectMapSmallerOnes, o1, o2);
                    getListSafely(objectMapSmallerOnes, o2).add(o1);
                } else if (0 < diff) {
                    checkConsistency(objectMapSmallerOnes, o2, o1);
                    getListSafely(objectMapSmallerOnes, o1).add(o2);
                } else {
                    throw new IllegalStateException("Equals not expected?");
                }
            }
        });
    }

    /**
     * @param objectMapSmallerOnes
     * @param o1
     * @param o2
     */
    private static <T> void checkConsistency(final Map<T, List<T>> objectMapSmallerOnes, T o1, T o2) {
        final List<T> smallerThan = objectMapSmallerOnes.get(o1);

        if (smallerThan != null) {
            for (final T o : smallerThan) {
                if (o == o2) {
                    throw new IllegalStateException(o2 + "  cannot be smaller than " + o1 + " if it's supposed to be vice versa.");
                }
                checkConsistency(objectMapSmallerOnes, o, o2);
            }
        }
    }

    /**
     * @param keyMapValues
     * @param key
     * @param <Key>
     * @param <Value>
     * @return List<Value>
     */
    private static <Key, Value> List<Value> getListSafely(Map<Key, List<Value>> keyMapValues, Key key) {
        List<Value> values = keyMapValues.get(key);

        if (values == null) {
            keyMapValues.put(key, values = new LinkedList<>());
        }

        return values;
    }

    /**
     * @author Oku
     *
     * @param <T>
     */
    private interface IPairIteratorCallback<T> {
        /**
         * @param o1
         * @param o2
         */
        void pair(T o1, T o2);
    }

    /**
     *
     * Iterates through each distinct unordered pair formed by the elements of a given iterator
     *
     * @param it
     * @param callback
     */
    private static <T> void iterateDistinctPairs(final Iterator<T> it, IPairIteratorCallback<T> callback) {
        List<T> list = new ArrayList<>();
        while (it.hasNext()) {
            list.add(it.next());
        }


//        List<T> list = Convert.toMinimumArrayList(new Iterable<T>() {
//
//            @Override
//            public Iterator<T> iterator() {
//                return it;
//            }
//
//        });

        for (int outerIndex = 0; outerIndex < list.size() - 1; outerIndex++) {
            for (int innerIndex = outerIndex + 1; innerIndex < list.size(); innerIndex++) {
                callback.pair(list.get(outerIndex), list.get(innerIndex));
            }
        }
    }
}
