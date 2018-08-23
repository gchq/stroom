package stroom.db.migration.doc.xslt;

import stroom.db.migration.doc.JsonSerialiser2;
import stroom.docstore.EncodingUtil;

import java.io.IOException;
import java.util.Map;

public class XsltSerialiser extends JsonSerialiser2<XsltDoc> {
    private static final String XSL = "xsl";

    public XsltSerialiser() {
        super(XsltDoc.class);
    }

    @Override
    public XsltDoc read(final Map<String, byte[]> data) throws IOException {
        final XsltDoc document = super.read(data);
        document.setData(EncodingUtil.asString(data.get(XSL)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final XsltDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(XSL, EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}