package stroom.legacy.impex_6_1;

import stroom.index.shared.IndexDoc;
import stroom.legacy.model_6_1.DocRef;
import stroom.legacy.model_6_1.Index;

import java.util.Map;
import java.util.UUID;

@Deprecated
public class LegacyIndexDeserialiser {
    public IndexDoc getIndexDocFromLegacyImport(final DocRef docRef, final Map<String, byte[]> dataMap) {
        final Index oldIndex = new Index();
        LegacyXmlSerialiser.performImport(oldIndex, dataMap);

        final IndexDoc document = new IndexDoc();
        document.setType(docRef.getType());
        document.setUuid(docRef.getUuid());
        document.setName(docRef.getName());
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTimeMs(oldIndex.getCreateTime());
        document.setUpdateTimeMs(oldIndex.getUpdateTime());
        document.setCreateUser(oldIndex.getCreateUser());
        document.setUpdateUser(oldIndex.getUpdateUser());

        document.setDescription(oldIndex.getDescription());
        document.setMaxDocsPerShard(oldIndex.getMaxDocsPerShard());
        if (oldIndex.getPartitionBy() != null) {
            document.setPartitionBy(IndexDoc.PartitionBy.valueOf(oldIndex.getPartitionBy().name()));
        }
        document.setPartitionSize(oldIndex.getPartitionSize());
        document.setShardsPerPartition(oldIndex.getShardsPerPartition());
        document.setRetentionDayAge(oldIndex.getRetentionDayAge());

        final stroom.legacy.model_6_1.IndexFields indexFields =
                LegacyXmlSerialiser.getIndexFieldsFromLegacyXml(oldIndex.getIndexFields());
        document.setFields(MappingUtil.map(indexFields));

        return document;
    }
}