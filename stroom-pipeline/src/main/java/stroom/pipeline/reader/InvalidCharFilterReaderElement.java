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

package stroom.pipeline.reader;

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.Reader;

@ConfigurableElement(type = "InvalidCharFilterReader",
        category = Category.READER,
        roles = {
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_READER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.STREAM)
public class InvalidCharFilterReaderElement extends AbstractReaderElement {
    private static final char REPLACEMENT_CHAR = 0xfffd; // The <?> symbol.
    private static final Xml10Chars XML_10_CHARS = new Xml10Chars();
    private static final Xml11Chars XML_11_CHARS = new Xml11Chars();

    private final ErrorReceiver errorReceiver;

    private InvalidXmlCharFilter invalidXmlCharFilter;
    private XmlChars validChars = XML_11_CHARS;

    @Inject
    public InvalidCharFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        invalidXmlCharFilter = new InvalidXmlCharFilter(reader, validChars);
        return invalidXmlCharFilter;
    }

    @Override
    public void endStream() {
        if (invalidXmlCharFilter.hasModifiedContent()) {
            errorReceiver.log(Severity.WARNING, null, getElementId(),
                    "Some illegal characters were removed from the input stream", null);
        }
        super.endStream();
    }

    @PipelineProperty(
            description = "XML version, e.g. 1.0 or 1.1",
            defaultValue = "1.1",
            displayPriority = 1)
    public void setXmlVersion(final String xmlMode) {
        if ("1.0".equals(xmlMode)) {
            validChars = XML_10_CHARS;
        }
    }
}
