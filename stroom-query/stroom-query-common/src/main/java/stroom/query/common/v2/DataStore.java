package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.ValuesConsumer;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;
import java.util.function.Consumer;

public interface DataStore extends ValuesConsumer {

    /**
     * Get the columns that this data store knows about.
     */
    List<Column> getColumns();

    /**
     * Get child items from the data for the provided parent key and time filter.
     *
     * @param key        The parent key to get child items for.
     * @param timeFilter The time filter to use to limit the data returned.
     * @return The filtered child items for the parent key.
     */
    void fetch(List<Column> columns,
                   OffsetRange range,
                   OpenGroups openGroups,
                   TimeFilter timeFilter,
                   ItemMapper mapper,
                   Consumer<Item> resultConsumer,
                   Consumer<Long> totalRowCountConsumer);

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
    void readPayload(Input input);

    /**
     * Write data from the data store to an output removing them from the datastore as we go as they will be transferred
     * to another store.
     *
     * @param output The output to write to.
     */
    void writePayload(Output output);

    long getByteSize();

    KeyFactory getKeyFactory();

    DateTimeSettings getDateTimeSettings();
}
