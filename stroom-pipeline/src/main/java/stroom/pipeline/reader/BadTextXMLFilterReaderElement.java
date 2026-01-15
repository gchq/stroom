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

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;

import java.io.Reader;

@ConfigurableElement(
        type = "BadTextXMLFilterReader",
        category = Category.READER,
        description = """
                Escapes the content of a configured list of named XML elements that are know to potentially \
                contain un-escaped XML reserved characters.
                For example the element `<Expression>$time < now()</Expression>` would be transformed to \
                `<Expression>$time &lt; now()</Expression>` if property `leafList` is set to `Expression`.""",
        roles = {
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_READER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class BadTextXMLFilterReaderElement extends AbstractReaderElement {

    private final ErrorReceiver errorReceiver;

    private BadTextXMLFilterReader badTextXMLFilterReader;
    private String[] forceLeafEntities = new String[0];

    @Inject
    public BadTextXMLFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        badTextXMLFilterReader = new BadTextXMLFilterReader(reader, forceLeafEntities);
        return badTextXMLFilterReader;
    }

    @Override
    public void endStream() {
        if (badTextXMLFilterReader.hasModifiedContent()) {
            errorReceiver.log(Severity.WARNING,
                    null,
                    getElementId(),
                    "The content was modified",
                    null);
        }
        super.endStream();
    }

    @PipelineProperty(
            description = "A comma separated list of XML element names (case sensitive) between which non-escaped " +
                    "XML characters will be escaped, e.g. '>' => '&gt;'.",
            displayPriority = 1)
    public void setTags(final String leafList) {
        if (leafList != null) {
            forceLeafEntities = leafList.split(",");
        }
    }
}
