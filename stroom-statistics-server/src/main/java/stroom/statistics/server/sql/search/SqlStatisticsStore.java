package stroom.statistics.server.sql.search;

import stroom.mapreduce.UnsafePairQueue;
import stroom.query.Coprocessor;
import stroom.query.CoprocessorSettingsMap;
import stroom.query.Data;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ItemsArrayList;
import stroom.query.Key;
import stroom.query.Payload;
import stroom.query.Store;
import stroom.query.TablePayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlStatisticsStore implements Store {

    private CoprocessorSettingsMap coprocessorSettingsMap;
    private Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap;
    private Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap;

    @Override
    public void destroy() {

    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public Data getData(String componentId) {
        final CoprocessorSettingsMap.CoprocessorKey coprocessorKey = coprocessorSettingsMap.getCoprocessorKey(componentId);
        if (coprocessorKey == null) {
            return null;
        }

        Payload payload = payloadMap.get(coprocessorKey);
        TablePayload tablePayload = (TablePayload) payload;
        UnsafePairQueue<Key, Item> queue = tablePayload.getQueue();

        Map<Key, Items<Item>> childMap = new HashMap<>();
        // We should now have a reduction in the reducedQueue.
        queue.forEach(pair -> {
            final Item item = pair.getValue();

            if (item.getKey() != null) {
                childMap.computeIfAbsent(item.getKey().getParent(), k -> new ItemsArrayList<>()).add(item);
            } else {
                childMap.computeIfAbsent(null, k -> new ItemsArrayList<>()).add(item);
            }
        });

        return new Data(childMap, queue.size(), queue.size());
    }

    @Override
    public List<String> getErrors() {
        return null;
    }

    @Override
    public List<String> getHighlights() {
        return null;
    }

    public void process(CoprocessorSettingsMap coprocessorSettingsMap) {
        this.coprocessorSettingsMap = coprocessorSettingsMap;
    }

    public void coprocessorMap(Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap) {
        this.coprocessorMap = coprocessorMap;
    }

    public void payloadMap(Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap) {
        this.payloadMap = payloadMap;
    }
}
