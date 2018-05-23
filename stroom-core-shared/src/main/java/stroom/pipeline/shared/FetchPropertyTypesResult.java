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

import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.docref.SharedObject;

import java.util.Map;

public class FetchPropertyTypesResult implements SharedObject {
    private static final long serialVersionUID = -456643944015316403L;

    private Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes;

    public FetchPropertyTypesResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchPropertyTypesResult(final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes) {
        this.propertyTypes = propertyTypes;
    }

    public Map<PipelineElementType, Map<String, PipelinePropertyType>> getPropertyTypes() {
        return propertyTypes;
    }
}
