package stroom.db.migration.doc.textconverter;

import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;

import java.io.IOException;
import java.util.Map;

public class TextConverterSerialiser extends JsonSerialiser2<TextConverterDoc> {
    private static final String XML = "xml";

    public TextConverterSerialiser() {
        super(TextConverterDoc.class);
    }

    @Override
    public TextConverterDoc read(final Map<String, byte[]> data) throws IOException {
        final TextConverterDoc document = super.read(data);
        document.setData(EncodingUtil.asString(data.get(XML)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final TextConverterDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(XML, EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}