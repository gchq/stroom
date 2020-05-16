package stroom.dashboard.impl.visualisation;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.util.string.EncodingUtil;
import stroom.visualisation.shared.VisualisationDoc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

public class VisualisationSerialiser implements DocumentSerialiser2<VisualisationDoc> {
    private static final String JSON = "json";

    private final Serialiser2<VisualisationDoc> delegate;

    @Inject
    public VisualisationSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(VisualisationDoc.class);
    }

    @Override
    public VisualisationDoc read(final Map<String, byte[]> data) throws IOException {
        final VisualisationDoc document = delegate.read(data);

        final String json = EncodingUtil.asString(data.get(JSON));
        if (json != null) {
            document.setSettings(json);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final VisualisationDoc document) throws IOException {
        final String settings = document.getSettings();
        document.setSettings(null);

        final Map<String, byte[]> data = delegate.write(document);

        if (settings != null) {
            data.put(JSON, EncodingUtil.asBytes(settings));
            document.setSettings(settings);
        }

        return data;
    }
}