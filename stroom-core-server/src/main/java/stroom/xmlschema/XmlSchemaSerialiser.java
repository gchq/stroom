package stroom.xmlschema;

import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.xmlschema.shared.XmlSchemaDoc;

import java.io.IOException;
import java.util.Map;

public class XmlSchemaSerialiser extends JsonSerialiser2<XmlSchemaDoc> {
    private static final String XSD = "xsd";

    public XmlSchemaSerialiser() {
        super(XmlSchemaDoc.class);
    }

    @Override
    public XmlSchemaDoc read(final Map<String, byte[]> data) throws IOException {
        final XmlSchemaDoc document = super.read(data);
        document.setData(EncodingUtil.asString(data.get(XSD)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final XmlSchemaDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(XSD, EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}