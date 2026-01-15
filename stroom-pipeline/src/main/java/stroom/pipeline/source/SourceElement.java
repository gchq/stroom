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

package stroom.pipeline.source;

import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.Element;
import stroom.pipeline.reader.AbstractInputElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.svg.shared.SvgImage;

@ConfigurableElement(
        type = "Source",
        description = """
                The source is the input to the pipeline.""",
        roles = {
                PipelineElementType.ROLE_SOURCE,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE
        },
        icon = SvgImage.PIPELINE_STREAM)
public class SourceElement extends AbstractInputElement implements Element {
}
