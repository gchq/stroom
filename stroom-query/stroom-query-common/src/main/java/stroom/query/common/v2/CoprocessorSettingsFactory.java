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

import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CoprocessorSettingsFactory {
    public static List<CoprocessorSettings> create(final SearchRequest searchRequest) {
        final Map<CoprocessorKey, CoprocessorSettings> map = new HashMap<>();

        // Group common settings.
        final Map<TableSettings, Set<String>> groupMap = new HashMap<>();
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (resultRequest.getMappings() != null && resultRequest.getMappings().size() > 0) {
                final String componentId = resultRequest.getComponentId();
                final TableSettings tableSettings = resultRequest.getMappings().get(0);
                if (tableSettings != null) {
                    Set<String> set = groupMap.computeIfAbsent(tableSettings, k -> new HashSet<>());
                    set.add(componentId);
                }
            }
        }

        final List<CoprocessorSettings> coprocessorSettings = new ArrayList<>(groupMap.size());
        int i = 0;
        for (final Entry<TableSettings, Set<String>> entry : groupMap.entrySet()) {
            final TableSettings tableSettings = entry.getKey();
            final Set<String> componentIds = entry.getValue();
            final CoprocessorKey key = new CoprocessorKey(i++, componentIds.stream().sorted().toArray(String[]::new));
            coprocessorSettings.add(new TableCoprocessorSettings(key, tableSettings));
        }

        return coprocessorSettings;
    }
}
