package stroom.visualisation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.DocumentSerialiser2;
import stroom.docstore.Serialiser2;
import stroom.docstore.Serialiser2Factory;
import stroom.entity.shared.DocRefs;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.util.string.EncodingUtil;
import stroom.visualisation.shared.VisualisationDoc;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class VisualisationSerialiser implements DocumentSerialiser2<VisualisationDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VisualisationSerialiser.class);

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
        final Map<String, byte[]> data = delegate.write(document);

        if (document.getSettings() != null) {
            data.put(JSON, EncodingUtil.asBytes(document.getSettings()));
        }

        return data;
    }

    public DocRef getDocRefFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(DocRefs.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, DocRef.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}