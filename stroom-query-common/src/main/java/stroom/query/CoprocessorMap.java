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

import stroom.query.api.TableSettings;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CoprocessorMap {
    private final Map<String, CoprocessorKey> componentIdToCoprocessorKeyMap = new HashMap<>();
    private final Map<CoprocessorKey, CoprocessorSettings> map = new HashMap<>();

    public CoprocessorMap(final Map<String, TableSettings> settingsMap) {
        // Group common settings.
        final Map<TableSettings, Set<String>> groupMap = new HashMap<>();
        for (final Entry<String, TableSettings> entry : settingsMap.entrySet()) {
            final String componentId = entry.getKey();
            final TableSettings tableSettings = entry.getValue();
            if (tableSettings != null) {
                Set<String> set = groupMap.computeIfAbsent(tableSettings, k -> new HashSet<>());
                set.add(componentId);
            }
        }

        int i = 0;
        for (final Entry<TableSettings, Set<String>> entry : groupMap.entrySet()) {
            final TableSettings tableSettings = entry.getKey();
            final Set<String> componentIds = entry.getValue();
            final CoprocessorKey key = new CoprocessorKey(i++, componentIds.toArray(new String[componentIds.size()]));
            map.put(key, new TableCoprocessorSettings(tableSettings));
            for (String componentId : componentIds) {
                componentIdToCoprocessorKeyMap.put(componentId, key);
            }
        }
    }

    public CoprocessorKey getCoprocessorKey(final String componentId) {
        return componentIdToCoprocessorKeyMap.get(componentId);
    }

    public Map<CoprocessorKey, CoprocessorSettings> getMap() {
        return map;
    }

    public static class CoprocessorKey implements Serializable {
        private int id;
        private String[] componentIds;

        public CoprocessorKey(final int id, final String[] componentIds) {
            this.id = id;
            this.componentIds = componentIds;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final CoprocessorKey that = (CoprocessorKey) o;

            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }

        @Override
        public String toString() {
            return Arrays.toString(componentIds);
        }
    }
}
