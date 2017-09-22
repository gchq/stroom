/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline.server.source;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.Element;
import stroom.pipeline.server.reader.AbstractInputElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;

@Component
@Scope("prototype")
@ConfigurableElement(type = "Source",
        roles = {
            PipelineElementType.ROLE_SOURCE,
            PipelineElementType.ROLE_HAS_TARGETS,
            PipelineElementType.VISABILITY_SIMPLE
        },
        icon = ElementIcons.STREAM)
public class SourceElement extends AbstractInputElement implements Element {
}
