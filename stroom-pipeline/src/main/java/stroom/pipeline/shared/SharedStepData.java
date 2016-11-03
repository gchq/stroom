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

package stroom.pipeline.shared;

import java.util.List;
import java.util.Map;

import stroom.util.shared.Highlight;
import stroom.util.shared.SharedObject;

public class SharedStepData implements SharedObject {
    private static final long serialVersionUID = 8988595868257312081L;

    private List<Highlight> sourceHighlights;
    private Map<String, SharedElementData> elementMap;

    public SharedStepData() {
        // Default constructor necessary for GWT serialisation.
    }

    public SharedStepData(final List<Highlight> sourceHighlights, final Map<String, SharedElementData> elementMap) {
        this.sourceHighlights = sourceHighlights;
        this.elementMap = elementMap;
    }

    public List<Highlight> getSourceHighlights() {
        return sourceHighlights;
    }

    public SharedElementData getElementData(final String elementId) {
        return elementMap.get(elementId);
    }

    // For testing.
    public Map<String, SharedElementData> getElementMap() {
        return elementMap;
    }
}
