package stroom.db.migration.doc.statistics.hbase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.migration.doc.JsonSerialiser2;
import stroom.entity.util.XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class StroomStatsStoreSerialiser extends JsonSerialiser2<StroomStatsStoreDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsStoreSerialiser.class);

    public StroomStatsStoreSerialiser() {
        super(StroomStatsStoreDoc.class);
    }

    @Override
    public StroomStatsStoreDoc read(final Map<String, byte[]> data) throws IOException {
        final StroomStatsStoreDoc document = super.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final StroomStatsStoreDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        return data;
    }

    public StroomStatsStoreEntityData getDataFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(StroomStatsStoreEntityData.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, StroomStatsStoreEntityData.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}