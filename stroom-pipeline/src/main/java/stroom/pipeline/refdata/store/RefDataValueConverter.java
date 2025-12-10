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

package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.store.onheapstore.FastInfosetValueConsumer;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.trans.XPathException;

import java.io.StringWriter;

public class RefDataValueConverter {

    public String refDataValueToString(final RefDataValue refDataValue) {
        if (refDataValue instanceof StringValue) {
            return ((StringValue) refDataValue).getValue();
        } else if (refDataValue instanceof FastInfosetValue) {
            final FastInfosetValue fastInfosetValue = (FastInfosetValue) refDataValue;

            return convertToString(fastInfosetValue);
        } else {
            throw new RuntimeException("Unknown type " + refDataValue.getTypeId());
        }
    }

    public Receiver buildStringReceiver(final StringWriter stringWriter,
                                        final PipelineConfiguration pipelineConfiguration) {
        final XMLEmitter xmlEmitterReceiver = new FragmentXmlEmitter();
        try {
            xmlEmitterReceiver.setWriter(stringWriter);
            xmlEmitterReceiver.setPipelineConfiguration(pipelineConfiguration);
        } catch (final XPathException e) {
            throw new RuntimeException(e);
        }

        return xmlEmitterReceiver;
    }

    private String convertToString(final FastInfosetValue fastInfosetValue) {

        final Configuration configuration = Configuration.newConfiguration();
        final PipelineConfiguration pipelineConfiguration = configuration.makePipelineConfiguration();
        final StringWriter stringWriter = new StringWriter();
        final Receiver xmlEmitterReceiver = buildStringReceiver(stringWriter, pipelineConfiguration);

        final FastInfosetValueConsumer fastInfosetValueConsumer = new FastInfosetValueConsumer(
                xmlEmitterReceiver, pipelineConfiguration);

        fastInfosetValueConsumer.consume(fastInfosetValue);

        return stringWriter.toString();
    }

    private static class FragmentXmlEmitter extends XMLEmitter {

        @Override
        public void writeDeclaration() throws XPathException {
            // We are dealing in XML fragments so ignore the declaration
            //   <?xml version="1.0" encoding="UTF-8" ?>
        }
    }
}
