package stroom.query.common.v2;

import stroom.dashboard.expression.v1.GroupKey;
import stroom.mapreduce.v2.Pair;
import stroom.mapreduce.v2.Reader;
import stroom.mapreduce.v2.Source;

import java.util.HashMap;
import java.util.Map;

public class ResultStoreCreator implements Reader<GroupKey, Item> {
    private final CompiledSorter sorter;
    private final Map<GroupKey, Items<Item>> childMap;

    public ResultStoreCreator(final CompiledSorter sorter) {
        this.sorter = sorter;
        childMap = new HashMap<>();
    }

    public Data create(final long size, final long totalSize) {
        return new Data(childMap, size, totalSize);
    }

    public Map<GroupKey, Items<Item>> getChildMap() {
        return childMap;
    }

    @Override
    public void read(final Source<GroupKey, Item> source) {
        // We should now have a reduction in the reducedQueue.
        for (final Pair<GroupKey, Item> pair : source) {
            final Item item = pair.getValue();

            if (item.key != null) {
                childMap.computeIfAbsent(item.key.getParent(), k -> new ItemsArrayList<>()).add(item);
            } else {
                childMap.computeIfAbsent(null, k -> new ItemsArrayList<>()).add(item);
            }
        }
    }

    public void sortAndTrim(final Sizes storeSize) {
        sortAndTrim(storeSize, null, 0);
    }

    private void sortAndTrim(final Sizes storeSize, final GroupKey parentKey, final int depth) {
        final Items<Item> parentItems = childMap.get(parentKey);
        if (parentItems != null) {
            if (storeSize == null) {
                // no store limits so just sort
                parentItems.sort(sorter);
            } else {
                // sort then trim
                parentItems.sortAndTrim(storeSize.size(depth), sorter, item -> {
                    // If there is a group key then cascade removal.
                    if (item.key != null) {
                        remove(item.key);
                    }
                });
            }

            // Ensure remaining items children are also trimmed by cascading
            // trim operation.

            // Lower levels of results should be reduced by increasing
            // amounts so that we don't get an exponential number of
            // results.
            // int sz = size / 10;
            // if (sz < 1) {
            // sz = 1;
            // }
            for (final Item item : parentItems) {
                if (item.key != null) {
                    sortAndTrim(storeSize, item.key, depth + 1);
                }
            }
        }
    }

    private void remove(final GroupKey parentKey) {
        final Items<Item> items = childMap.get(parentKey);
        if (items != null) {
            childMap.remove(parentKey);

            // Cascade delete.
            for (final Item item : items) {
                if (item.key != null) {
                    remove(item.key);
                }
            }
        }
    }
}
