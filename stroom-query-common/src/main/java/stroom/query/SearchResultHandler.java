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
import stroom.query.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.api.TableSettings;
import stroom.util.shared.HasTerminate;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchResultHandler implements ResultHandler {
    private final CoprocessorSettingsMap coprocessorSettingsMap;
    private final Map<CoprocessorKey, TablePayloadHandler> handlerMap = new HashMap<>();
    private final AtomicBoolean complete = new AtomicBoolean();

    public SearchResultHandler(final CoprocessorSettingsMap coprocessorSettingsMap, final Integer[] defaultStoreTrimSizes) {
        this.coprocessorSettingsMap = coprocessorSettingsMap;

        for (final Entry<CoprocessorKey, CoprocessorSettings> entry : coprocessorSettingsMap.getMap().entrySet()) {
            final CoprocessorKey coprocessorKey = entry.getKey();
            final CoprocessorSettings coprocessorSettings = entry.getValue();
            if (coprocessorSettings instanceof TableCoprocessorSettings) {
                final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) coprocessorSettings;
                final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
                final TrimSettings trimSettings = new TrimSettings(tableSettings.getMaxResults(), defaultStoreTrimSizes);
                handlerMap.put(coprocessorKey, new TablePayloadHandler(tableSettings.getFields(),
                        tableSettings.showDetail(), trimSettings));
            }
        }
    }

    @Override
    public void handle(final Map<CoprocessorKey, Payload> payloadMap, final HasTerminate hasTerminate) {
        if (payloadMap != null) {
            for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
                final Payload payload = entry.getValue();
                if (payload instanceof TablePayload) {
                    final TablePayload tablePayload = (TablePayload) payload;

                    final TablePayloadHandler payloadHandler = handlerMap.get(entry.getKey());
                    final UnsafePairQueue<Key, Item> newQueue = tablePayload.getQueue();
                    if (newQueue != null) {
                        payloadHandler.addQueue(newQueue, hasTerminate);
                    }
                }
            }
        }
    }

    public TablePayloadHandler getPayloadHandler(final String componentId) {
        final CoprocessorKey coprocessorKey = coprocessorSettingsMap.getCoprocessorKey(componentId);
        if (coprocessorKey == null) {
            return null;
        }

        return handlerMap.get(coprocessorKey);
    }

    @Override
    public boolean shouldTerminateSearch() {
        boolean terminate = false;
        if (handlerMap.size() == coprocessorSettingsMap.getMap().size()) {
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
    public Data getResultStore(final String componentId) {
        final TablePayloadHandler tablePayloadHandler = getPayloadHandler(componentId);
        if (tablePayloadHandler != null) {
            return tablePayloadHandler.getData();
        }
        return null;
    }
}
