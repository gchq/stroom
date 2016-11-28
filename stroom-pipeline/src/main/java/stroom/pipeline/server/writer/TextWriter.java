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

package stroom.pipeline.server.writer;

import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Joins text instances into a single text instance.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "TextWriter", category = Category.WRITER, roles = {PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.ROLE_WRITER,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.TEXT)
public class TextWriter extends AbstractWriter {
    private byte[] header;
    private byte[] footer;

    public TextWriter() {
    }

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
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        try {
            int lastStart = start;
            for (int i = start; i < start + length; i++) {
                final char c = ch[i];
                if (c == '\n') {
                    borrowDestinations(header, footer);
                    getWriter().write(ch, lastStart, i - start);
                    lastStart = i;
                    returnDestinations();
                }
            }

            if (lastStart < start + length) {
                borrowDestinations(header, footer);
                getWriter().write(ch, lastStart, length - lastStart);
            }

        } catch (final IOException e) {
            throw ProcessException.wrap(e.getMessage(), e);
        }
    }

    @PipelineProperty(description = "Header text that can be added to the output at the start.")
    public void setHeader(final String header) {
        try {
            if (header == null) {
                this.header = null;
            } else {
                this.header = StringEscapeUtils.unescapeJava(header).getBytes();
            }
        } catch (final Exception e) {
            throw ProcessException.wrap(e.getMessage(), e);
        }
    }

    @PipelineProperty(description = "Footer text that can be added to the output at the end.")
    public void setFooter(final String footer) {
        try {
            if (footer == null) {
                this.footer = null;
            } else {
                this.footer = StringEscapeUtils.unescapeJava(footer).getBytes();
            }
        } catch (final Exception e) {
            throw ProcessException.wrap(e.getMessage(), e);
        }
    }

    @Override
    @PipelineProperty(description = "The output character encoding to use.", defaultValue = "UTF-8")
    public void setEncoding(final String encoding) {
        super.setEncoding(encoding);
    }
}
