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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.inject.Singleton;

public class PipelineElementBoxFactory {

    @Inject
    public PipelineElementBoxFactory() {
    }

    public PipelineElementBox create(final PipelineModel pipelineModel,
                                     final PipelineElement pipelineElement) {
        final SvgImage icon = pipelineElement.getElementType().getIcon();
        if (icon != null) {
            return new PipelineElementBox(pipelineModel, pipelineElement, icon);
        } else {
            return new PipelineElementBox(pipelineModel, pipelineElement, null);
        }
    }
}
