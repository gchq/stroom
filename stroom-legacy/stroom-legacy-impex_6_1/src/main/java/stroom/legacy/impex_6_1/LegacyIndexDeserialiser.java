package stroom.legacy.impex_6_1;

import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexDoc;
import stroom.legacy.model_6_1.DocRef;
import stroom.legacy.model_6_1.Index;

import java.util.Map;
import java.util.UUID;

@Deprecated
public class LegacyIndexDeserialiser {
    public IndexDoc getIndexDocFromLegacyImport(
            final DocRef docRef,
            final Map<String, byte[]> dataMap,
            final VolumeConfig volumeConfig) {

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

        // The legacy index has only a set of index volumes but we need an index volume group.
        // so use the default one.
        if (volumeConfig.getDefaultIndexVolumeGroupName() != null
                && !volumeConfig.getDefaultIndexVolumeGroupName().isEmpty()) {
            document.setVolumeGroupName(volumeConfig.getDefaultIndexVolumeGroupName());
        } else {
            throw new RuntimeException("Property " +
                    volumeConfig.getFullPathStr(VolumeConfig.PROP_NAME_DEFUALT_VOLUME_GROUP_NAME) +
                    " is not set. Unable to migrate index.");
        }

        return document;
    }
}
