package stroom.dictionary;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;

import java.io.IOException;
import java.util.Map;

public class DictionarySerialiser extends JsonSerialiser2<DictionaryDoc> {
    private static final String TEXT = "txt";

    public DictionarySerialiser() {
        super(DictionaryDoc.class);
    }

    @Override
    public DictionaryDoc read(final Map<String, byte[]> data) throws IOException {
        final DictionaryDoc document = super.read(data);
        document.setData(EncodingUtil.asString(data.get(TEXT)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final DictionaryDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(TEXT, EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}