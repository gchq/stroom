package stroom.core.db.migration._V07_00_00.doc.statistics.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.core.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.core.db.migration._V07_00_00.entity.util._V07_00_00_XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class _V07_00_00_StatisticStoreSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_StatisticStoreDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(_V07_00_00_StatisticStoreSerialiser.class);

    public _V07_00_00_StatisticStoreSerialiser() {
        super(_V07_00_00_StatisticStoreDoc.class);
    }

    @Override
    public _V07_00_00_StatisticStoreDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_StatisticStoreDoc document = super.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_StatisticStoreDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);
        return data;
    }

    public _V07_00_00_StatisticsDataSourceData getDataFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_StatisticsDataSourceData.class);
                return _V07_00_00_XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_StatisticsDataSourceData.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}