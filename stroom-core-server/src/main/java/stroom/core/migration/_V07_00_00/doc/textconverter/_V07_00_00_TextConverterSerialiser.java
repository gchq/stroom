package stroom.core.migration._V07_00_00.doc.textconverter;


import stroom.core.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.core.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;

import java.io.IOException;
import java.util.Map;

public class _V07_00_00_TextConverterSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_TextConverterDoc> {
    private static final String XML = "xml";

    public _V07_00_00_TextConverterSerialiser() {
        super(_V07_00_00_TextConverterDoc.class);
    }

    @Override
    public _V07_00_00_TextConverterDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_TextConverterDoc document = super.read(data);
        document.setData(_V07_00_00_EncodingUtil.asString(data.get(XML)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_TextConverterDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(XML, _V07_00_00_EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}