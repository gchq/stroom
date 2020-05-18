package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.TextConverter;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.textconverter.TextConverterSerialiser;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
@Deprecated
class TextConverterDataMapConverter implements DataMapConverter {
    private final TextConverterSerialiser serialiser;

    @Inject
    TextConverterDataMapConverter(final TextConverterSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 1 && !dataMap.containsKey("meta") && dataMap.containsKey("xml")) {
            try {
                final TextConverter oldTextConverter = new TextConverter();
                LegacyXmlSerialiser.performImport(oldTextConverter, dataMap);

                final TextConverterDoc document = new TextConverterDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldTextConverter.getCreateTime());
                document.setUpdateTimeMs(oldTextConverter.getUpdateTime());
                document.setCreateUser(oldTextConverter.getCreateUser());
                document.setUpdateUser(oldTextConverter.getUpdateUser());

                document.setDescription(oldTextConverter.getDescription());
                document.setConverterType(MappingUtil.map(oldTextConverter.getConverterType()));

                result = serialiser.write(document);
                if (dataMap.containsKey("data.xml")) {
                    result.put("xml", dataMap.remove("data.xml"));
                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
