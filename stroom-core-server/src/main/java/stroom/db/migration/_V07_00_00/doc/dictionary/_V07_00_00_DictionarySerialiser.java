package stroom.db.migration._V07_00_00.doc.dictionary;

import stroom.db.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;

import java.io.IOException;
import java.util.Map;

public class _V07_00_00_DictionarySerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_DictionaryDoc> {
    private static final String TEXT = "txt";

    public _V07_00_00_DictionarySerialiser() {
        super(_V07_00_00_DictionaryDoc.class);
    }

    @Override
    public _V07_00_00_DictionaryDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_DictionaryDoc document = super.read(data);
        document.setData(_V07_00_00_EncodingUtil.asString(data.get(TEXT)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_DictionaryDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        if (document.getData() != null) {
            data.put(TEXT, _V07_00_00_EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}