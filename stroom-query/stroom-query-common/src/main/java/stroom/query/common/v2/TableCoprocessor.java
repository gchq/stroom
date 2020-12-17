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

import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.TableSettings;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class TableCoprocessor implements Coprocessor {
    private final TableSettings tableSettings;
    private final DataStore dataStore;

    private final Consumer<Throwable> errorConsumer;
    private final CountDownLatch completionState = new CountDownLatch(1);
    private final AtomicLong valuesCount = new AtomicLong();
    private final AtomicLong completionCount = new AtomicLong();

    public TableCoprocessor(final TableSettings tableSettings,
                            final DataStore dataStore,
                            final Consumer<Throwable> errorConsumer) {
        this.tableSettings = tableSettings;
        this.dataStore = dataStore;
        this.errorConsumer = errorConsumer;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    @Override
    public Consumer<Val[]> getValuesConsumer() {
        return values -> {
            valuesCount.incrementAndGet();
            dataStore.add(values);
        };
    }

    @Override
    public Consumer<Throwable> getErrorConsumer() {
        return errorConsumer;
    }

    @Override
    public Consumer<Long> getCompletionConsumer() {
        return count -> {
            completionCount.set(count);
            completionState.countDown();
        };
    }

    @Override
    public boolean readPayload(final Input input) {
        return dataStore.readPayload(input);
    }

    @Override
    public void writePayload(final Output output) {
        dataStore.writePayload(output);
    }

    public AtomicLong getValuesCount() {
        return valuesCount;
    }

    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completionState.await(timeout, unit);
    }

    public DataStore getData() {
        return dataStore;
    }

    @Override
    public String toString() {
        return tableSettings.toString();
    }
}
