package stroom.statistics.sql.search;

import stroom.query.common.v2.Data;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreSize;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SqlStatisticsStore implements Store {
    private final ResultHandler resultHandler;

    private final List<Integer> defaultMaxResultsSizes;
    private final StoreSize storeSize;

    SqlStatisticsStore(final List<Integer> defaultMaxResultsSizes,
                       final StoreSize storeSize,
                       final ResultHandler resultHandler) {
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.resultHandler = resultHandler;
    }

    @Override
    public void destroy() {
        //nothing to do as this store doesn't hold any query state
    }

    @Override
    public boolean isComplete() {
        // Results are currently assembled synchronously in getData so the store is always complete.
        return true;
    }

    @Override
    public void awaitCompletion() {
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) {
        // Results are currently assembled synchronously in getData so the store is always complete.
        return true;
    }

    @Override
    public Data getData(String componentId) {
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
}
