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

package stroom.pipeline.stepping.client.presenter;

import java.util.ArrayList;
import java.util.List;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.SimplePipelineTreeBuilder;

public class SteppingPipelineTreeBuilder extends SimplePipelineTreeBuilder {
    public SteppingPipelineTreeBuilder() {
        super(PipelineElementType.VISABILITY_STEPPING);
    }

    public static List<PipelineProperty> getEditableProperties(final PipelineElement element,
            final PipelineModel pipelineModel) {
        final List<PipelineProperty> editable = new ArrayList<>();

        final List<PipelineProperty> properties = pipelineModel.getProperties(element);
        if (properties != null && properties.size() > 0) {
            for (final PipelineProperty property : properties) {
                final PipelinePropertyType propertyType = property.getPropertyType();
                if (propertyType.isDataEntity()) {
                    editable.add(property);
                }
            }
        }
        return editable;
    }
}
