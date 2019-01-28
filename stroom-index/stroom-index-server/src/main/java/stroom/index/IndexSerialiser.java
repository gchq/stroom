package stroom.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.DocumentSerialiser2;
import stroom.docstore.Serialiser2;
import stroom.docstore.Serialiser2Factory;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexFields;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class IndexSerialiser implements DocumentSerialiser2<IndexDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexSerialiser.class);

//    private static final String JS = "js";

    private final Serialiser2<IndexDoc> delegate;

    @Inject
    public IndexSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(IndexDoc.class);
    }

    @Override
    public IndexDoc read(final Map<String, byte[]> data) throws IOException {
        final IndexDoc document = delegate.read(data);

//        final String js = EncodingUtil.asString(data.get(JS));
//        if (js != null) {
//            document.setData(js);
//        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final IndexDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);

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