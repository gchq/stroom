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

import java.util.Map;

public class FetchPropertyTypesResult {
    private PipelineElementType pipelineElementType;
    private Map<String, PipelinePropertyType> propertyTypes;

    public FetchPropertyTypesResult() {
    }

    public FetchPropertyTypesResult(final PipelineElementType pipelineElementType, final Map<String, PipelinePropertyType> propertyTypes) {
        this.pipelineElementType = pipelineElementType;
        this.propertyTypes = propertyTypes;
    }

    public PipelineElementType getPipelineElementType() {
        return pipelineElementType;
    }

    public void setPipelineElementType(final PipelineElementType pipelineElementType) {
        this.pipelineElementType = pipelineElementType;
    }

    public Map<String, PipelinePropertyType> getPropertyTypes() {
        return propertyTypes;
    }

    public void setPropertyTypes(final Map<String, PipelinePropertyType> propertyTypes) {
        this.propertyTypes = propertyTypes;
    }
}
