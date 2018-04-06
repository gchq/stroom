package stroom.pipeline;

import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.pipeline.shared.TextConverterDoc;

import java.io.IOException;
import java.util.Map;

class TextConverterSerialiser extends JsonSerialiser2<TextConverterDoc> {
    private static final String XML = "xml";

    TextConverterSerialiser() {
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