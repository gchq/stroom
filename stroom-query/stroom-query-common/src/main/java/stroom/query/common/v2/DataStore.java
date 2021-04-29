package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.concurrent.TimeUnit;

public interface DataStore {

    /**
     * Add some values to the data store.
     *
     * @param values The values to add to the store.
     */
    void add(Val[] values);

    /**
     * Get root items from the data store.
     *
     * @return Root items.
     */
    Items get();

    /**
     * Get child items from the data store for the provided parent key.
     *
     * @param parentKey The parent key to get child items for.
     * @return The child items for the parent key.
     */
    Items get(final Key key);

    /**
     * Clear the data store.
     */
    void clear();

    /**
     * Get the completion state associated with receiving all search results and having added them to the store
     * successfully.
     *
     * @return The search completion state for the data store.
     */
    CompletionState getCompletionState();

    /**
     * Read items from the supplied input and transfer them to the data store.
     *
     * @param input The input to read.
     * @return True if we still happy to keep on receiving data, false otherwise.
     */
    boolean readPayload(Input input);

    /**
     * Write data from the data store to an output removing them from the datastore as we go as they will be transferred
     * to another store.
     *
     * @param output The output to write to.
     */
    void writePayload(Output output);

    /**
     * Wait for all current items that might be queued for adding to be added.
     *
     * @param timeout How long to wait for items to be added.
     * @param unit    The time unit for the wait period.
     * @return True if we didn't timeout and all items are now added.
     * @throws InterruptedException Thrown if the thread is interrupted while waiting.
     */
    boolean awaitTransfer(long timeout, TimeUnit unit) throws InterruptedException;
}
