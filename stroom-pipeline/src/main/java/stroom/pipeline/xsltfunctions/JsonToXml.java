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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.xml.converter.json.JSONFactoryConfig;
import stroom.pipeline.xml.converter.json.JSONParser;
import stroom.util.shared.Severity;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

class JsonToXml extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "json-to-xml";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Sequence result = EmptyAtomicSequence.getInstance();

        try {
            // Get the json string.
            final String json = getSafeString(functionName, context, arguments, 0);

            if (json != null && !json.isEmpty()) {
                try {
                    result = jsonToXml(context, json);
                } catch (final IOException | SAXException e) {
                    createWarning(context, e);
                }
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return result;
    }

    static Sequence jsonToXml(final XPathContext context, final String json) throws IOException, SAXException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        final JSONParser parser = new JSONParser(new JSONFactoryConfig(), false);
        parser.setContentHandler(contentHandler);

        try {
            parser.parse(new InputSource(new StringReader(json)));
        } catch (final Exception e) {
            throw new RuntimeException("Error parsing JSON - " + e.getMessage(), e);
        }

        final Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed
        // document.
        builder.reset();

        return sequence;
    }

    private void createWarning(final XPathContext context, final Throwable t) {
        // Create the message.
        final StringBuilder sb = new StringBuilder(t.getMessage());
        outputWarning(context, sb, t);
    }
}
