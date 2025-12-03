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
        type = "InvalidXMLCharFilterReader",
        category = Category.READER,
        description = """
                Replaces any characters that are not in the standard XML character set with a '�'.
                The version of XML (e.g. 1.0 or 1.1) can be set using the 'xmlVersion' property.
                """,
        roles = {
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_READER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class InvalidXMLCharFilterReaderElement extends AbstractReaderElement {

    static final char REPLACEMENT_CHAR = 0xfffd; // The � symbol (3 bytes)
    private static final Xml10Chars XML_10_CHARS = new Xml10Chars();
    private static final Xml11Chars XML_11_CHARS = new Xml11Chars();

    private final ErrorReceiver errorReceiver;

    private InvalidXmlCharFilter invalidXmlCharFilter;
    private XmlChars validChars = XML_11_CHARS;
    private boolean warnOnReplacement = true;

    @Inject
    public InvalidXMLCharFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        invalidXmlCharFilter = InvalidXmlCharFilter.createReplaceCharsFilter(
                reader, validChars, true, REPLACEMENT_CHAR);
        return invalidXmlCharFilter;
    }

    @Override
    public void endStream() {
        if (warnOnReplacement && invalidXmlCharFilter.hasModifiedContent()) {
            errorReceiver.log(
                    Severity.WARNING,
                    null,
                    getElementId(),
                    "Some characters that are not valid in XML v" + validChars.getXmlVersion()
                            + " were replaced with '"
                            + REPLACEMENT_CHAR
                            + "' in the input stream",
                    null);
        }
        super.endStream();
    }

    @PipelineProperty(
            description = "XML version, e.g. '1.0' or '1.1'",
            defaultValue = "1.1",
            displayPriority = 1)
    public void setXmlVersion(final String xmlMode) {
        if ("1.0".equals(xmlMode)) {
            validChars = XML_10_CHARS;
        }
    }

    @PipelineProperty(
            description = "Log a warning if any characters have been replaced in the input stream.",
            defaultValue = "true",
            displayPriority = 2)
    public void setWarnOnReplacement(final boolean warnOnReplacement) {
        this.warnOnReplacement = warnOnReplacement;
    }
}
