package stroom.core.migration._V07_00_00.doc.visualisation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.core.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.core.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.core.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.migration._V07_00_00.entity.shared._V07_00_00_DocRefs;
import stroom.core.migration._V07_00_00.entity.util._V07_00_00_XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class _V07_00_00_VisualisationSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_VisualisationDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(_V07_00_00_VisualisationSerialiser.class);

    private static final String JSON = "json";

    public _V07_00_00_VisualisationSerialiser() {
        super(_V07_00_00_VisualisationDoc.class);
    }

    @Override
    public _V07_00_00_VisualisationDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_VisualisationDoc document = super.read(data);

        final String json = _V07_00_00_EncodingUtil.asString(data.get(JSON));
        if (json != null) {
            document.setSettings(json);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_VisualisationDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

        if (document.getSettings() != null) {
            data.put(JSON, _V07_00_00_EncodingUtil.asBytes(document.getSettings()));
        }

        return data;
    }

    public _V07_00_00_DocRef getDocRefFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_DocRefs.class);
                return _V07_00_00_XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_DocRef.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}