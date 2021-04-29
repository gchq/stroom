/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.concurrent.TimeUnit;

public interface Coprocessor extends Receiver {

    /**
     * Get the number of values that have been added to the coprocessor.
     *
     * @return The number of values that have been added to the coprocessor.
     */
    long getValuesCount();

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
