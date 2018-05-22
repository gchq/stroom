package stroom.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.JsonSerialiser2;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexFields;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class IndexSerialiser extends JsonSerialiser2<IndexDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexSerialiser.class);

//    private static final String JS = "js";

    public IndexSerialiser() {
        super(IndexDoc.class);
    }

    @Override
    public IndexDoc read(final Map<String, byte[]> data) throws IOException {
        final IndexDoc document = super.read(data);

//        final String js = EncodingUtil.asString(data.get(JS));
//        if (js != null) {
//            document.setData(js);
//        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final IndexDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

//        if (document.getData() != null) {
//            data.put(JS, EncodingUtil.asBytes(document.getData()));
//        }

        return data;
    }

    public IndexFields getIndexFieldsFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(IndexFields.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, IndexFields.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal index config", e);
            }
        }

        return null;
    }
}