/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

public class PipelineElementBoxFactory {

    private final PipelineModel pipelineModel;

    public PipelineElementBoxFactory(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    public PipelineElementBox create(final PipelineElement pipelineElement) {
        final SvgImage icon = NullSafe.get(pipelineModel.getElementType(pipelineElement), PipelineElementType::getIcon);
        return new PipelineElementBox(pipelineModel, pipelineElement, icon);
    }
}
