package stroom.core.db.migration._V07_00_00.doc.xmlschema;


import stroom.core.db.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.core.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;

import java.io.IOException;
import java.util.Map;

public class _V07_00_00_XmlSchemaSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_XmlSchemaDoc> {
    private static final String XSD = "xsd";

    public _V07_00_00_XmlSchemaSerialiser() {
        super(_V07_00_00_XmlSchemaDoc.class);
    }

    @Override
    public _V07_00_00_XmlSchemaDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_XmlSchemaDoc document = super.read(data);
        document.setData(_V07_00_00_EncodingUtil.asString(data.get(XSD)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_XmlSchemaDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(XSD, _V07_00_00_EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}