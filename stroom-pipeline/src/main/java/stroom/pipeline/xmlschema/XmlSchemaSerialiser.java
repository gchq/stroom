package stroom.pipeline.xmlschema;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.util.string.EncodingUtil;
import stroom.xmlschema.shared.XmlSchemaDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class XmlSchemaSerialiser implements DocumentSerialiser2<XmlSchemaDoc> {
    private static final String XSD = "xsd";

    private final Serialiser2<XmlSchemaDoc> delegate;

    @Inject
    public XmlSchemaSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(XmlSchemaDoc.class);
    }

    @Override
    public XmlSchemaDoc read(final Map<String, byte[]> data) throws IOException {
        final XmlSchemaDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(XSD)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final XmlSchemaDoc document) throws IOException {
        final String xsd = document.getData();
        document.setData(null);

        final Map<String, byte[]> data = delegate.write(document);
        if (xsd != null) {
            data.put(XSD, EncodingUtil.asBytes(xsd));
            document.setData(xsd);
        }

        return data;
    }
}