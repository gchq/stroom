package stroom.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.entity.shared.DocRefs;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.script.shared.ScriptDoc;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class ScriptSerialiser extends JsonSerialiser2<ScriptDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptSerialiser.class);

    private static final String JS = "js";

    public ScriptSerialiser() {
        super(ScriptDoc.class);
    }

    @Override
    public ScriptDoc read(final Map<String, byte[]> data) throws IOException {
        final ScriptDoc document = super.read(data);

        final String js = EncodingUtil.asString(data.get(JS));
        if (js != null) {
            document.setData(js);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final ScriptDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

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