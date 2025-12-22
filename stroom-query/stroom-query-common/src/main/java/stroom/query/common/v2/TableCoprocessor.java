/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.query.api.TableSettings;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class TableCoprocessor implements Coprocessor, HasCompletionState {

    private final TableSettings tableSettings;
    private final DataStore dataStore;

    private final ErrorConsumer errorConsumer;

    public TableCoprocessor(final TableSettings tableSettings,
                            final DataStore dataStore,
                            final ErrorConsumer errorConsumer) {
        this.tableSettings = tableSettings;
        this.dataStore = dataStore;
        this.errorConsumer = errorConsumer;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    @Override
    public void accept(final Val[] values) {
        dataStore.accept(values);
    }

    @Override
    public ErrorConsumer getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public void readPayload(final Input input) {
        dataStore.readPayload(input);
    }

    @Override
    public void writePayload(final Output output) {
        dataStore.writePayload(output);
    }

    @Override
    public CompletionState getCompletionState() {
        return dataStore.getCompletionState();
    }

    public DataStore getData() {
        return dataStore;
    }

    @Override
    public void clear() {
        dataStore.clear();
    }

    @Override
    public long getByteSize() {
        return dataStore.getByteSize();
    }

    @Override
    public String toString() {
        return tableSettings.toString();
    }
}
