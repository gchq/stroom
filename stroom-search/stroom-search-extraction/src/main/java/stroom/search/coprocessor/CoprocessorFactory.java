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

package stroom.search.coprocessor;

import org.springframework.stereotype.Component;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.TableCoprocessor;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.util.shared.HasTerminate;

import java.util.Map;

@Component
public class CoprocessorFactory {
    public Coprocessor create(final CoprocessorSettings settings,
                              final FieldIndexMap fieldIndexMap, final Map<String, String> paramMap, final HasTerminate hasTerminate) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            return new TableCoprocessor(tableCoprocessorSettings,
                    fieldIndexMap, hasTerminate, paramMap);
        } else if (settings instanceof EventCoprocessorSettings) {
            final EventCoprocessorSettings eventCoprocessorSettings = (EventCoprocessorSettings) settings;
            return new EventCoprocessor(eventCoprocessorSettings,
                    fieldIndexMap);
        }

        return null;
    }
}
