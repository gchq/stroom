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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CoprocessorMap {
    private final Map<String, Integer> componentIdToCoprocessorIdMap = new HashMap<String, Integer>();
    private final Map<Integer, CoprocessorSettings> map = new HashMap<Integer, CoprocessorSettings>();

    public CoprocessorMap(final Map<String, TableSettings> settingsMap) {
        int idSeq = 0;

        final Map<TableSettings, Integer> settingsIdMap = new HashMap<>();
        for (final Entry<String, TableSettings> entry : settingsMap.entrySet()) {
            final String componentId = entry.getKey();
            final TableSettings tableSettings = entry.getValue();
//            final TableSettings tableSettings = getTableSettings(componentSettings);
            if (tableSettings != null) {
                Integer id = settingsIdMap.get(tableSettings);
                if (id == null) {
                    id = idSeq;
                    settingsIdMap.put(tableSettings, id);
                    idSeq++;

                    final CoprocessorSettings tableCoprocessorSettings = new TableCoprocessorSettings(tableSettings);
                    map.put(id, tableCoprocessorSettings);
                }
                componentIdToCoprocessorIdMap.put(componentId, id);
            }
        }
    }

//    private TableSettings getTableSettings(final ComponentSettings componentSettings) {
//        TableSettings tableSettings = null;
//        if (componentSettings instanceof TableSettings) {
//            tableSettings = (TableSettings) componentSettings;
//        } else if (componentSettings instanceof VisDashboardSettings) {
//            tableSettings = ((VisDashboardSettings) componentSettings).getTableSettings();
//        }
//
//        return tableSettings;
//    }

    public Integer getCoprocessorId(final String componentId) {
        return componentIdToCoprocessorIdMap.get(componentId);
    }

    public Map<Integer, CoprocessorSettings> getMap() {
        return map;
    }
}
