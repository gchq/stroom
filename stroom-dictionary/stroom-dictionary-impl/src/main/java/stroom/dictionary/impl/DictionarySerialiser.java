package stroom.dictionary.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.util.string.EncodingUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class DictionarySerialiser implements DocumentSerialiser2<DictionaryDoc> {
    private static final String TEXT = "txt";

    private final Serialiser2<DictionaryDoc> delegate;

    @Inject
    public DictionarySerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(DictionaryDoc.class);
    }

    @Override
    public DictionaryDoc read(final Map<String, byte[]> data) throws IOException {
        final DictionaryDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(TEXT)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final DictionaryDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        if (document.getData() != null) {
            data.put(TEXT, EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}