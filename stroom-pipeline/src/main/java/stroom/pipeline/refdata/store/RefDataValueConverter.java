package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.store.onheapstore.FastInfosetValueConsumer;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
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

    private String convertToString(final FastInfosetValue fastInfosetValue) {

        final Configuration configuration = Configuration.newConfiguration();
        final PipelineConfiguration pipelineConfiguration = configuration.makePipelineConfiguration();
        final XMLEmitter xmlEmitterReceiver = new FragmentXmlEmitter();
        final StringWriter stringWriter = new StringWriter();
        try {
            xmlEmitterReceiver.setWriter(stringWriter);
            xmlEmitterReceiver.setPipelineConfiguration(pipelineConfiguration);
        } catch (XPathException e) {
            throw new RuntimeException(e);
        }

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
