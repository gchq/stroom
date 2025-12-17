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

package stroom.pipeline.reader;

import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;

import java.io.InputStream;

@ConfigurableElement(
        type = "BOMRemovalFilterInput",
        description = """
                Removes the Byte Order Mark (if present) from the stream.""",
        category = Category.READER,
        roles = {
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_READER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class BOMRemovalFilterInputElement extends AbstractInputElement {

    private BOMRemovalInputStream bomRemovalInputStream;

    @Override
    protected InputStream insertFilter(final InputStream inputStream, final String encoding) {
        bomRemovalInputStream = new BOMRemovalInputStream(inputStream, encoding);
        return bomRemovalInputStream;
    }
}
