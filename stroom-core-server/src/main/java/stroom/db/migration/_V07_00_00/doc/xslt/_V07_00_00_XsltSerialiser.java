package stroom.db.migration._V07_00_00.doc.xslt;

import stroom.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.db.migration._V07_00_00.doc._V07_00_00_EncodingUtil;

import java.io.IOException;
import java.util.Map;

public class _V07_00_00_XsltSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_XsltDoc> {
    private static final String XSL = "xsl";

    public _V07_00_00_XsltSerialiser() {
        super(_V07_00_00_XsltDoc.class);
    }

    @Override
    public _V07_00_00_XsltDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_XsltDoc document = super.read(data);
        document.setData(_V07_00_00_EncodingUtil.asString(data.get(XSL)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_XsltDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(XSL, _V07_00_00_EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}