package stroom.legacy.impex_6_1;

import stroom.dictionary.impl.DictionarySerialiser;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.OldDictionaryDoc;
import stroom.util.shared.Severity;
import stroom.util.string.EncodingUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
class DictionaryDataMapConverter implements DataMapConverter {
    private final DocumentSerialiser2<DictionaryDoc> serialiser;
    private final DocumentSerialiser2<OldDictionaryDoc> oldSerialiser;

    @Inject
    DictionaryDataMapConverter(final DictionarySerialiser serialiser,
                               final Serialiser2Factory serialiser2Factory) {
        this.serialiser = serialiser;
        this.oldSerialiser = serialiser2Factory.createSerialiser(OldDictionaryDoc.class);
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;

        try {
            if (!dataMap.containsKey("meta")) {
                // The latest version has a 'meta' file for the core details about the dictionary so convert this data.

                if (dataMap.containsKey("dat")) {
                    // Version 6.0 stored the whole dictionary in a single JSON file ending in 'dat' so convert this.
                    dataMap.put("meta", dataMap.remove("dat"));
                    final OldDictionaryDoc oldDocument = oldSerialiser.read(dataMap);

                    final DictionaryDoc document = new DictionaryDoc();
                    document.setType(docRef.getType());
                    document.setUuid(docRef.getUuid());
                    document.setName(docRef.getName());
                    document.setVersion(oldDocument.getVersion());
                    document.setCreateTimeMs(oldDocument.getCreateTime());
                    document.setUpdateTimeMs(oldDocument.getUpdateTime());
                    document.setCreateUser(oldDocument.getCreateUser());
                    document.setUpdateUser(oldDocument.getUpdateUser());

                    document.setDescription(oldDocument.getDescription());
                    document.setImports(MappingUtil.mapList(oldDocument.getImports(), MappingUtil::map));
                    document.setData(oldDocument.getData());

                    result = serialiser.write(document);
                } else {
                    // If we don't have a 'dat' file then this version is pre 6.0. We need to create the dictionary meta and put the data in the map.
                    final long now = System.currentTimeMillis();

                    final DictionaryDoc document = new DictionaryDoc();
                    document.setType(docRef.getType());
                    document.setUuid(docRef.getUuid());
                    document.setName(docRef.getName());
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTimeMs(now);
                    document.setUpdateTimeMs(now);
                    document.setCreateUser(userId);
                    document.setUpdateUser(userId);

                    if (dataMap.containsKey("data.xml")) {
                        document.setData(EncodingUtil.asString(dataMap.get("data.xml")));
                    }

                    result = serialiser.write(document);
                }
            }
        } catch (final IOException | RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            result = null;
        }

        return result;
    }
}
