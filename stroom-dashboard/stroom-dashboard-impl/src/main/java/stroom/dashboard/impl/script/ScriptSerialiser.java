package stroom.dashboard.impl.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.script.shared.ScriptDoc;
import stroom.util.shared.DocRefs;
import stroom.util.string.EncodingUtil;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class ScriptSerialiser implements DocumentSerialiser2<ScriptDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptSerialiser.class);

    private static final String JS = "js";

    private final Serialiser2<ScriptDoc> delegate;

    @Inject
    ScriptSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(ScriptDoc.class);
    }

    @Override
    public ScriptDoc read(final Map<String, byte[]> data) throws IOException {
        final ScriptDoc document = delegate.read(data);

        final String js = EncodingUtil.asString(data.get(JS));
        if (js != null) {
            document.setData(js);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final ScriptDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);

        if (document.getData() != null) {
            data.put(JS, EncodingUtil.asBytes(document.getData()));
        }

        return data;
    }

    public DocRefs getDocRefsFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(DocRefs.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, DocRefs.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}