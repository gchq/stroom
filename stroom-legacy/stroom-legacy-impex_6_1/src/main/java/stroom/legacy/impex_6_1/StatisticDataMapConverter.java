package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.StatisticStoreEntity;
import stroom.legacy.model_6_1.StatisticsDataSourceMarshaller;
import stroom.statistics.impl.sql.entity.StatisticStoreSerialiser;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

class StatisticDataMapConverter implements DataMapConverter {
//    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticDataMapConverter.class);

    private final StatisticStoreSerialiser serialiser;

    @Inject
    StatisticDataMapConverter(final StatisticStoreSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            try {
                StatisticStoreEntity oldStatisticStore = new StatisticStoreEntity();
                LegacyXmlSerialiser.performImport(oldStatisticStore, dataMap);
                final StatisticsDataSourceMarshaller marshaller = new StatisticsDataSourceMarshaller();
                oldStatisticStore = marshaller.unmarshal(oldStatisticStore);

                final StatisticStoreDoc document = new StatisticStoreDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldStatisticStore.getCreateTime());
                document.setUpdateTimeMs(oldStatisticStore.getUpdateTime());
                document.setCreateUser(oldStatisticStore.getCreateUser());
                document.setUpdateUser(oldStatisticStore.getUpdateUser());

                document.setDescription(oldStatisticStore.getDescription());
                document.setStatisticType(StatisticType.valueOf(oldStatisticStore.getStatisticType().name()));
                document.setRollUpType(StatisticRollUpType.valueOf(oldStatisticStore.getRollUpType().name()));
                document.setPrecision(oldStatisticStore.getPrecision());
                document.setEnabled(oldStatisticStore.isEnabled());

                document.setConfig(MappingUtil.map(oldStatisticStore.getStatisticDataSourceDataObject()));

//                final StatisticsDataSourceData statisticsDataSourceData = getDataFromLegacyXML(oldStatisticStore.getData());
//                if (statisticsDataSourceData != null) {
//                    document.setConfig(statisticsDataSourceData);
//                }

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

//    public stroom.legacy.model_6_1.StatisticsDataSourceData getDataFromLegacyXML(final String xml) {
//        if (xml != null) {
//            try {
//                final JAXBContext jaxbContext = JAXBContext.newInstance(stroom.legacy.model_6_1.StatisticsDataSourceData.class);
//                return XMLMarshallerUtil.unmarshal(jaxbContext, stroom.legacy.model_6_1.StatisticsDataSourceData.class, xml);
//            } catch (final JAXBException | RuntimeException e) {
//                LOGGER.error("Unable to unmarshal dashboard config", e);
//            }
//        }
//
//        return null;
//    }
}
