package stroom.statistics.impl.sql.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class StatisticStoreSerialiser implements DocumentSerialiser2<StatisticStoreDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticStoreSerialiser.class);

    private final Serialiser2<StatisticStoreDoc> delegate;

    @Inject
    public StatisticStoreSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(StatisticStoreDoc.class);
    }

    @Override
    public StatisticStoreDoc read(final Map<String, byte[]> data) throws IOException {
        final StatisticStoreDoc document = delegate.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final StatisticStoreDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
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