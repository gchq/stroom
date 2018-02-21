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

package stroom.pipeline.server.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SharedStepData;
import stroom.util.shared.Highlight;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StepData {
    private final Map<String, ElementData> elementMap = new HashMap<>();
    private List<Highlight> sourceHighlights;

    public List<Highlight> getSourceHighlights() {
        return sourceHighlights;
    }

    public void setSourceHighlights(final List<Highlight> sourceHighlights) {
        this.sourceHighlights = sourceHighlights;
    }

    public Map<String, ElementData> getElementMap() {
        return elementMap;
    }

    public SharedStepData convertToShared() {
        final Map<String, SharedElementData> map = new HashMap<>();
        for (final Entry<String, ElementData> entry : elementMap.entrySet()) {
            map.put(entry.getKey(), entry.getValue().convertToShared());
        }

        return new SharedStepData(sourceHighlights, map);
    }
}
