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

package stroom.pipeline.writer;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * Joins text instances into a single text instance.
 */
@ConfigurableElement(
        type = "TextWriter",
        description = """
                Writer to convert XML character data events into plain text output.
                """,
        category = Category.WRITER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_WRITER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_TEXT)
public class TextWriter extends AbstractWriter {

    private byte[] header;
    private byte[] footer;

    @Inject
    public TextWriter(final ErrorReceiverProxy errorReceiverProxy) {
        super(errorReceiverProxy);
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            // We return destinations here even though they would be returned
            // anyway in end processing because we want stepping mode to see
            // flushed output.
            returnDestinations();
        } finally {
            super.endDocument();
        }
    }

    /**
     * Writes characters.
     *
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        try {
            int pos = start;
            for (int i = start; i < start + length; i++) {
                final char c = ch[i];
                if (c == '\n') {
                    final int nextPos = i + 1;
                    final int len = nextPos - pos;

//                    final String str = new String(ch, pos, len);

                    borrowDestinations(header, footer);
                    getWriter().write(ch, pos, len);
                    pos = nextPos;
                    returnDestinations();
                }
            }

            final int len = length - pos;
            if (len > 0) {
                borrowDestinations(header, footer);

//                final String str = new String(ch, pos, len);

                getWriter().write(ch, pos, len);
            }

        } catch (final IOException e) {
            throw ProcessException.wrap(e);
        }
    }

    @PipelineProperty(
            description = "Header text that can be added to the output at the start.",
            displayPriority = 1)
    public void setHeader(final String header) {
        try {
            if (header == null) {
                this.header = null;
            } else {
                this.header = StringEscapeUtils.unescapeJava(header).getBytes();
            }
        } catch (final RuntimeException e) {
            throw ProcessException.wrap(e);
        }
    }

    @PipelineProperty(
            description = "Footer text that can be added to the output at the end.",
            displayPriority = 2)
    public void setFooter(final String footer) {
        try {
            if (footer == null) {
                this.footer = null;
            } else {
                this.footer = StringEscapeUtils.unescapeJava(footer).getBytes();
            }
        } catch (final RuntimeException e) {
            throw ProcessException.wrap(e);
        }
    }

    @Override
    @PipelineProperty(
            description = "The output character encoding to use.",
            defaultValue = "UTF-8",
            displayPriority = 3)
    public void setEncoding(final String encoding) {
        super.setEncoding(encoding);
    }
}
