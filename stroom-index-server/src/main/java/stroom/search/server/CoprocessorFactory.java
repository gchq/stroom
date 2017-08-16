/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.server;

import org.springframework.stereotype.Component;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.query.Coprocessor;
import stroom.query.CoprocessorSettings;
import stroom.query.TableCoprocessor;
import stroom.query.TableCoprocessorSettings;
import stroom.util.task.TaskMonitor;

import java.util.Map;

@Component
public class CoprocessorFactory {
    public Coprocessor create(final CoprocessorSettings settings,
                              final FieldIndexMap fieldIndexMap, final Map<String, String> paramMap, final TaskMonitor taskMonitor) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableCoprocessor tableCoprocessor = new TableCoprocessor(tableCoprocessorSettings,
                    fieldIndexMap, taskMonitor, paramMap);
            return tableCoprocessor;
        } else if (settings instanceof EventCoprocessorSettings) {
            final EventCoprocessorSettings eventCoprocessorSettings = (EventCoprocessorSettings) settings;
            final EventCoprocessor eventCoprocessor = new EventCoprocessor(eventCoprocessorSettings,
                    fieldIndexMap);
            return eventCoprocessor;
        }

        return null;
    }
}
