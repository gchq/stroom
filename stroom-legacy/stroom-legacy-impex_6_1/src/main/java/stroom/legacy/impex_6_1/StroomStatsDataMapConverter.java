package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.StroomStatsStoreEntity;
import stroom.legacy.model_6_1.StroomStatsStoreEntityMarshaller;
import stroom.statistics.impl.hbase.entity.StroomStatsStoreSerialiser;
import stroom.statistics.impl.hbase.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.hbase.shared.StatisticRollUpType;
import stroom.statistics.impl.hbase.shared.StatisticType;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
class StroomStatsDataMapConverter implements DataMapConverter {
//    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsDataMapConverter.class);

    private final StroomStatsStoreSerialiser serialiser;

    @Inject
    StroomStatsDataMapConverter(final StroomStatsStoreSerialiser serialiser) {
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
                StroomStatsStoreEntity oldStroomStatsStore = new StroomStatsStoreEntity();
                LegacyXmlSerialiser.performImport(oldStroomStatsStore, dataMap);
                final StroomStatsStoreEntityMarshaller marshaller = new StroomStatsStoreEntityMarshaller();
                oldStroomStatsStore = marshaller.unmarshal(oldStroomStatsStore);

                final StroomStatsStoreDoc document = new StroomStatsStoreDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldStroomStatsStore.getCreateTime());
                document.setUpdateTimeMs(oldStroomStatsStore.getUpdateTime());
                document.setCreateUser(oldStroomStatsStore.getCreateUser());
                document.setUpdateUser(oldStroomStatsStore.getUpdateUser());

                document.setDescription(oldStroomStatsStore.getDescription());
                document.setStatisticType(StatisticType.valueOf(oldStroomStatsStore.getStatisticType().name()));
                document.setRollUpType(StatisticRollUpType.valueOf(oldStroomStatsStore.getRollUpType().name()));
                document.setPrecision(EventStoreTimeIntervalEnum.valueOf(oldStroomStatsStore.getPrecisionAsInterval().name()));
                document.setEnabled(oldStroomStatsStore.isEnabled());

                document.setConfig(MappingUtil.map(oldStroomStatsStore.getDataObject()));

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
