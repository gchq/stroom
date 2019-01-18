package stroom.pipeline;

import stroom.docstore.DocumentSerialiser2;
import stroom.docstore.Serialiser2;
import stroom.docstore.Serialiser2Factory;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.util.string.EncodingUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class TextConverterSerialiser implements DocumentSerialiser2<TextConverterDoc> {
    private static final String XML = "xml";

    private final Serialiser2<TextConverterDoc> delegate;

    @Inject
    public TextConverterSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(TextConverterDoc.class);
    }

    @Override
    public TextConverterDoc read(final Map<String, byte[]> data) throws IOException {
        final TextConverterDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(XML)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final TextConverterDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        if (document.getData() != null) {
            data.put(XML, EncodingUtil.asBytes(document.getData()));
        }
        return data;
    }
}