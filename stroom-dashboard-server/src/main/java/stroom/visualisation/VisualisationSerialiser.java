package stroom.visualisation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.entity.shared.DocRefs;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.query.api.v2.DocRef;
import stroom.visualisation.shared.VisualisationDoc;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class VisualisationSerialiser extends JsonSerialiser2<VisualisationDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VisualisationSerialiser.class);

    private static final String JSON = "json";

    public VisualisationSerialiser() {
        super(VisualisationDoc.class);
    }

    @Override
    public VisualisationDoc read(final Map<String, byte[]> data) throws IOException {
        final VisualisationDoc document = super.read(data);

        final String json = EncodingUtil.asString(data.get(JSON));
        if (json != null) {
            document.setSettings(json);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final VisualisationDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

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