package stroom.statistics.server.sql.search;

import stroom.query.common.v2.CompletionListener;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class SqlStatisticsStore implements Store {

    private final ResultHandler resultHandler;

    private final List<Integer> defaultMaxResultsSizes;
    private final StoreSize storeSize;
    //results are currently assembled synchronously in getData so the store is always complete
    private final AtomicBoolean isComplete;
    private final List<CompletionListener> completionListeners = Collections.synchronizedList(new ArrayList<>());

    public SqlStatisticsStore(final List<Integer> defaultMaxResultsSizes,
                              final StoreSize storeSize,
                              final ResultHandler resultHandler) {
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.resultHandler = resultHandler;
        this.isComplete = new AtomicBoolean(true);
    }

    @Override
    public void destroy() {
        //nothing to do as this store doesn't hold any query state
    }

    @Override
    public boolean isComplete() {
        return isComplete.get();
    }

    @Override
    public Data getData(String componentId) {
//        final CoprocessorSettingsMap.CoprocessorKey coprocessorKey = coprocessorSettingsMap.getCoprocessorKey(componentId);
//        if (coprocessorKey == null) {
//            return null;
//        }
//
//        TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) coprocessorSettingsMap.getMap()
//                .get(coprocessorKey);
//        TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
//
//        Payload payload = payloadMap.get(coprocessorKey);
//        TablePayload tablePayload = (TablePayload) payload;
//        UnsafePairQueue<Key, Item> queue = tablePayload.getQueue();
//
//        CompiledSorter compiledSorter = new CompiledSorter(tableSettings.getFields());
//        final ResultStoreCreator resultStoreCreator = new ResultStoreCreator(compiledSorter);
//        resultStoreCreator.read(queue);
//
//
//        // Trim the number of results in the store.
//        resultStoreCreator.trim(storeSize);
//
//        return resultStoreCreator.create(queue.size(), queue.size());

        return resultHandler.getResultStore(componentId);
    }

    @Override
    public List<String> getErrors() {
        return null;
    }

    @Override
    public List<String> getHighlights() {
        return null;
    }

    @Override
    public List<Integer> getDefaultMaxResultsSizes() {
        return defaultMaxResultsSizes;
    }

    @Override
    public StoreSize getStoreSize() {
        return storeSize;
    }

    @Override
    public void registerCompletionListener(final CompletionListener completionListener) {
        if (isComplete.get()) {
            //immediate notification
            completionListener.onCompletion();
        } else {
            //TODO this is currently of no use but when incremental queries are implemented it will be needed
            completionListeners.add(Objects.requireNonNull(completionListener));
        }
    }

}
