/*
 * Copyright 2016 Crown Copyright
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

package stroom.query;

import stroom.mapreduce.UnsafePairQueue;
import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.TableSettings;
import stroom.util.shared.HasTerminate;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchResultHandler implements ResultHandler {
    private final CoprocessorMap coprocessorMap;
    private final Map<Integer, TablePayloadHandler> handlerMap = new HashMap<>();
    private final AtomicBoolean complete = new AtomicBoolean();

    public SearchResultHandler(final CoprocessorMap coprocessorMap) {
        this.coprocessorMap = coprocessorMap;

        for (final Entry<Integer, CoprocessorSettings> entry : coprocessorMap.getMap().entrySet()) {
            final CoprocessorSettings coprocessorSettings = entry.getValue();
            if (coprocessorSettings instanceof TableCoprocessorSettings) {
                final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) entry.getValue();
                final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
                handlerMap.put(entry.getKey(), new TablePayloadHandler(tableSettings.getFields(),
                        tableSettings.showDetail(), tableSettings.getMaxResults()));
            }
        }
    }

    @Override
    public void handle(final Map<Integer, Payload> payloadMap, final HasTerminate hasTerminate) {
        if (payloadMap != null) {
            for (final Entry<Integer, Payload> entry : payloadMap.entrySet()) {
                final Payload payload = entry.getValue();
                if (payload instanceof TablePayload) {
                    final TablePayload tablePayload = (TablePayload) payload;

                    final TablePayloadHandler payloadHandler = handlerMap.get(entry.getKey());
                    final UnsafePairQueue<String, Item> newQueue = tablePayload.getQueue();
                    if (newQueue != null) {
                        payloadHandler.addQueue(newQueue, hasTerminate);
                    }
                }
            }
        }
    }

    public TablePayloadHandler getPayloadHandler(final String componentId) {
        final Integer coprocessorId = coprocessorMap.getCoprocessorId(componentId);
        if (coprocessorId == null) {
            return null;
        }

        return handlerMap.get(coprocessorId);
    }

    @Override
    public boolean shouldTerminateSearch() {
        boolean terminate = false;
        if (handlerMap.size() == coprocessorMap.getMap().size()) {
            terminate = true;
            for (final PayloadHandler payloadHandler : handlerMap.values()) {
                if (!payloadHandler.shouldTerminateSearch()) {
                    terminate = false;
                    break;
                }
            }
        }

        return terminate;
    }

    @Override
    public boolean isComplete() {
        return complete.get();
    }

    @Override
    public void setComplete(final boolean complete) {
        this.complete.set(complete);
    }

    @Override
    public ResultStore getResultStore(final String componentId) {
        final TablePayloadHandler tablePayloadHandler = getPayloadHandler(componentId);
        if (tablePayloadHandler != null) {
            return tablePayloadHandler.getResultStore();
        }
        return null;
    }
}
