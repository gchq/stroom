package stroom.statistics.sql.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.JsonSerialiser2;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.statistics.shared.StatisticStoreDoc;
import stroom.statistics.shared.StatisticsDataSourceData;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class StatisticStoreSerialiser extends JsonSerialiser2<StatisticStoreDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticStoreSerialiser.class);

    public StatisticStoreSerialiser() {
        super(StatisticStoreDoc.class);
    }

    @Override
    public StatisticStoreDoc read(final Map<String, byte[]> data) throws IOException {
        final StatisticStoreDoc document = super.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final StatisticStoreDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        return data;
    }

    public StatisticsDataSourceData getDataFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(StatisticsDataSourceData.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, StatisticsDataSourceData.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}