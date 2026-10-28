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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

public class PipelineElementBoxFactory {

    private final PipelineModel pipelineModel;

    public PipelineElementBoxFactory(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    public PipelineElementBox create(final PipelineElement pipelineElement) {
        final PipelineElementType elementType = pipelineModel.getElementType(pipelineElement);
        final SvgImage icon = NullSafe.get(elementType, PipelineElementType::getIcon);
        return new PipelineElementBox(pipelineModel, pipelineElement, icon, getIconName(elementType));
    }

    /// Gets the text alternative for the icon of an element type, i.e. the type's display name,
    /// e.g. `XSLT Filter`, so a screen reader can say what type of element the icon represents.
    ///
    /// @param elementType The type of the element. Can be null.
    /// @return The type's display name, else its type name if it has no display name, else null
    /// if there is no type.
    public static String getIconName(final PipelineElementType elementType) {
        if (elementType == null) {
            return null;
        }
        return NullSafe.isBlankString(elementType.getDisplayValue())
                ? elementType.getType()
                : elementType.getDisplayValue();
    }
}
